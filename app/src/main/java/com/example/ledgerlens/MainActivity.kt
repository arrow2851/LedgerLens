package com.example.ledgerlens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.Telephony
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.domain.source.SourceDetector
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.ui.theme.LedgerLensTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ledgerlens.domain.parser.SmsTransactionParser
import com.example.ledgerlens.data.entity.TransactionEntity
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableLongStateOf
import java.util.Calendar
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import java.io.File

private const val MERCHANT_DEFAULT_RULE_SOURCE_KEY = "__merchant_defaults__"

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    private var pendingSmsImportMode: SmsImportMode = SmsImportMode.REFRESH_LATEST

    private val requestSmsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                runSmsImport(pendingSmsImportMode)
            } else {
                Log.d("LedgerLensSmsImport", "READ_SMS permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getInstance(applicationContext)

        setContent {
            LedgerLensTheme(dynamicColor = false) {
                LedgerLensSourceSetupApp(
                    database = database,
                    onBackfillSmsHistory = {
                        requestSmsImport(SmsImportMode.BACKFILL_HISTORY)
                    },
                    onRefreshLatestSms = {
                        requestSmsImport(SmsImportMode.REFRESH_LATEST)
                    },
                    onExportTransactions = {
                        exportTransactionsCsv()
                    },
                    onExportParserCorpus = {
                        exportParserCorpusJsonl()
                    }
                )
            }
        }
    }

    private fun requestSmsImport(mode: SmsImportMode) {
        pendingSmsImportMode = mode

        val permissionStatus = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        )

        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            runSmsImport(mode)
        } else {
            requestSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun runSmsImport(mode: SmsImportMode) {
        CoroutineScope(Dispatchers.Main).launch {
            val (importedCount, detectedCount) = withContext(Dispatchers.IO) {
                val imported = when (mode) {
                    SmsImportMode.BACKFILL_HISTORY -> importFinanceSmsMessages(daysBack = 365 * 5)
                    SmsImportMode.REFRESH_LATEST -> importFinanceSmsMessages(daysBack = 90)
                }
                val detected = detectAndSaveSources(
                    database = database,
                    rawAlerts = database.rawAlertDao().getAllOnce()
                )
                imported to detected
            }

            Log.d(
                "LedgerLensSmsImport",
                "mode=$mode importedCount=$importedCount detectedSources=$detectedCount"
            )
        }
    }

    private suspend fun importFinanceSmsMessages(daysBack: Int): Int {
        val dao = database.rawAlertDao()
        val now = System.currentTimeMillis()
        val startDate = now - (daysBack * 24L * 60L * 60L * 1000L)

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(startDate.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        var importedCount = 0
        var scannedCount = 0
        var financeLookingCount = 0
        var duplicateCount = 0

        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->

            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (cursor.moveToNext()) {
                scannedCount++

                val smsId = cursor.getLong(idIndex)
                val address = cursor.getString(addressIndex).orEmpty()
                val body = cursor.getString(bodyIndex).orEmpty()
                val date = cursor.getLong(dateIndex)
                val type = cursor.getInt(typeIndex)

                if (!looksLikeFinancialSms(body)) {
                    continue
                }

                financeLookingCount++

                val notificationKey = "sms:$smsId"

                if (dao.countByNotificationKey(notificationKey) > 0) {
                    duplicateCount++
                    continue
                }

                val rawAlert = RawAlertEntity(
                    notificationKey = notificationKey,
                    sourcePackage = "sms",
                    title = "SMS from $address",
                    text = body,
                    bigText = null,
                    subText = "sms_type=$type",
                    combinedText = body,
                    postTimeEpochMs = date,
                    capturedAtEpochMs = now,
                    processingStatus = "IMPORTED_SMS"
                )

                dao.insert(rawAlert)
                importedCount++
            }
        }

        Log.d(
            "LedgerLensSmsImport",
            "daysBack=$daysBack scanned=$scannedCount financeLooking=$financeLookingCount duplicates=$duplicateCount imported=$importedCount"
        )

        return importedCount
    }

    private fun looksLikeFinancialSms(body: String): Boolean {
        val lower = body.lowercase(Locale.US)

        val hasMoneyAmount = Regex(
            pattern = """(\$|usd\s*)?\d{1,3}(,\d{3})*(\.\d{2})"""
        ).containsMatchIn(lower)

        val financeKeywords = listOf(
            "spent",
            "purchase",
            "transaction",
            "charged",
            "charge",
            "debit",
            "debited",
            "credit",
            "credited",
            "deposit",
            "withdrawal",
            "payment",
            "paid",
            "balance",
            "card",
            "account",
            "atm",
            "pos",
            "zelle",
            "venmo",
            "cash app",
            "bank",
            "alert",
            "autopay",
            "refund",
            "authorized",
            "authorization",
            "available balance",
            "ending in"
        )

        return hasMoneyAmount && financeKeywords.any { lower.contains(it) }
    }

    private fun exportTransactionsCsv() {
        CoroutineScope(Dispatchers.Main).launch {
            val file = withContext(Dispatchers.IO) {
                val transactions = database.transactionDao().getAllOnce()
                val exportDir = File(
                    getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "exports"
                )
                exportDir.mkdirs()

                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                    .format(Date())
                val exportFile = File(exportDir, "ledgerlens-transactions-$timestamp.csv")

                exportFile.writeText(buildTransactionsCsv(transactions))
                exportFile
            }

            val uri = FileProvider.getUriForFile(
                this@MainActivity,
                "$packageName.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LedgerLens transaction export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Export LedgerLens CSV"))
        }
    }

    private fun exportParserCorpusJsonl() {
        CoroutineScope(Dispatchers.Main).launch {
            val file = withContext(Dispatchers.IO) {
                val rawAlerts = database.rawAlertDao().getAllOnce()
                val transactionsByRawAlertId = database.transactionDao()
                    .getAllOnce()
                    .associateBy { it.rawAlertId }
                val exportDir = File(
                    getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "parser-corpus"
                )
                exportDir.mkdirs()

                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                    .format(Date())
                val exportFile = File(exportDir, "ledgerlens-parser-corpus-$timestamp.jsonl")

                exportFile.writeText(
                    buildParserCorpusJsonl(
                        rawAlerts = rawAlerts,
                        transactionsByRawAlertId = transactionsByRawAlertId
                    )
                )
                exportFile
            }

            val uri = FileProvider.getUriForFile(
                this@MainActivity,
                "$packageName.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LedgerLens parser corpus export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Export parser corpus"))
        }
    }
}

data class CategorySpendSummary(
    val categoryName: String,
    val subcategoryName: String?,
    val amountCents: Long,
    val transactionCount: Int
)

data class ParseRunResult(
    val matchedAlertCount: Int,
    val parsedCount: Int,
    val skippedCount: Int,
    val ignoredNonTransactionCount: Int,
    val failedCount: Int
)

enum class SmsImportMode {
    BACKFILL_HISTORY,
    REFRESH_LATEST
}

enum class AppScreen {
    HOME,
    SETUP,
    SOURCES,
    TRANSACTIONS,
    SUMMARY,
    REVIEW_QUEUE,
    MERCHANTS,
    RULES,
    TOOLS
}

enum class ReviewQueueFilter {
    ALL_ISSUES,
    NEEDS_REVIEW,
    MISSING_MERCHANT,
    MISSING_CATEGORY,
    LOW_CONFIDENCE
}

enum class TransactionFilter {
    ALL,
    NEEDS_REVIEW,
    EXPENSES,
    TRANSFERS,
    CREDIT_CARD_PAYMENTS,
    EXCLUDED_FROM_SPENDING
}

data class MerchantSummary(
    val merchantName: String,
    val transactionCount: Int,
    val totalAmountCents: Long,
    val primaryTreatment: String,
    val categoryName: String?,
    val subcategoryName: String?,
    val uncategorizedCount: Int,
    val latestTransactionEpochMs: Long
)

@Composable
fun LedgerLensSourceSetupApp(
    database: AppDatabase,
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit,
    onExportTransactions: () -> Unit,
    onExportParserCorpus: () -> Unit
) {
    val rawAlerts by database
        .rawAlertDao()
        .observeAll()
        .collectAsState(initial = emptyList())

    val rawAlertCount by database
        .rawAlertDao()
        .observeCount()
        .collectAsState(initial = 0)

    val sources by database
        .financialSourceDao()
        .observeAll()
        .collectAsState(initial = emptyList())

    val sourceCount by database
        .financialSourceDao()
        .observeCount()
        .collectAsState(initial = 0)

    val transactionCount by database
        .transactionDao()
        .observeCount()
        .collectAsState(initial = 0)

    val activeRuleCount by database
        .transactionRuleDao()
        .observeActiveRuleCount()
        .collectAsState(initial = 0)

    val activeRules by database
        .transactionRuleDao()
        .observeActiveRules()
        .collectAsState(initial = emptyList())

    val transactions by database
        .transactionDao()
        .observeAll()
        .collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    var statusText by remember {
        mutableStateOf("Import SMS, detect sources, then review uncategorized possible sources.")
    }

    var selectedSource by remember {
        mutableStateOf<FinancialSourceEntity?>(null)
    }

    var activeScreen by remember {
        mutableStateOf(AppScreen.HOME)
    }

    var selectedTransaction by remember {
        mutableStateOf<TransactionEntity?>(null)
    }

    var selectedMerchant by remember {
        mutableStateOf<MerchantSummary?>(null)
    }

    val selectedSourceAlerts = remember(selectedSource, rawAlerts) {
        selectedSource?.let { source ->
            rawAlerts
                .filter { alert -> SourceDetector.matchesSource(alert, source) }
                .sortedByDescending { it.postTimeEpochMs }
        } ?: emptyList()
    }

    val uncategorizedSourceCount = remember(sources) {
        sources.count { !it.userConfirmed && !it.ignored }
    }

    val identifiedSourceCount = remember(sources) {
        sources.count { it.userConfirmed && !it.ignored }
    }

    val reviewIssueCount = remember(transactions) {
        transactions.count { hasAnyReviewIssue(it) }
    }

    val currentMonthExpenses = remember(transactions) {
        val monthStart = getCurrentMonthStartEpochMs()
        val monthEnd = getNextMonthStartEpochMs(monthStart)

        transactions
            .filter {
                TransactionTreatments.countsAsSpending(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending
                )
            }
            .filter {
                it.occurredAtEpochMs >= monthStart &&
                        it.occurredAtEpochMs < monthEnd
            }
    }

    val currentMonthSpendingCents = remember(currentMonthExpenses) {
        currentMonthExpenses
            .sumOf { it.amountCents }
    }

    val currentMonthActivity = remember(transactions) {
        val monthStart = getCurrentMonthStartEpochMs()
        val monthEnd = getNextMonthStartEpochMs(monthStart)

        transactions.filter {
            it.occurredAtEpochMs >= monthStart &&
                    it.occurredAtEpochMs < monthEnd
        }
    }

    val currentMonthIncomeCents = remember(currentMonthActivity) {
        currentMonthActivity
            .filter { it.accountingTreatment == TransactionTreatments.INCOME }
            .sumOf { it.amountCents }
    }

    val currentMonthRefundCents = remember(currentMonthActivity) {
        currentMonthActivity
            .filter { it.accountingTreatment == TransactionTreatments.REFUND }
            .sumOf { it.amountCents }
    }

    val currentMonthMovementCents = remember(currentMonthActivity) {
        currentMonthActivity
            .filter {
                it.accountingTreatment in setOf(
                    TransactionTreatments.CREDIT_CARD_PAYMENT,
                    TransactionTreatments.TRANSFER,
                    TransactionTreatments.PERSON_TO_PERSON
                )
            }
            .sumOf { it.amountCents }
    }

    val previousMonthSpendingCents = remember(transactions) {
        val currentMonthStart = getCurrentMonthStartEpochMs()
        val previousMonthStart = getPreviousMonthStartEpochMs(currentMonthStart)

        transactions
            .filter {
                TransactionTreatments.countsAsSpending(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending
                )
            }
            .filter {
                it.occurredAtEpochMs >= previousMonthStart &&
                        it.occurredAtEpochMs < currentMonthStart
            }
            .sumOf { it.amountCents }
    }

    val topCategoryLabel = remember(currentMonthExpenses) {
        currentMonthExpenses
            .groupBy { it.categoryName?.takeIf { category -> category.isNotBlank() } ?: "Unassigned" }
            .maxByOrNull { entry -> entry.value.sumOf { it.amountCents } }
            ?.let { entry ->
                "${entry.key} - $${"%.2f".format(entry.value.sumOf { it.amountCents } / 100.0)}"
            }
            ?: "No spending yet"
    }

    val topMerchantLabel = remember(currentMonthExpenses) {
        currentMonthExpenses
            .groupBy { it.displayMerchantName ?: it.merchantRaw ?: "Unknown merchant" }
            .maxByOrNull { entry -> entry.value.sumOf { it.amountCents } }
            ?.let { entry ->
                "${entry.key} - $${"%.2f".format(entry.value.sumOf { it.amountCents } / 100.0)}"
            }
            ?: "No merchant yet"
    }

    suspend fun detectAndSaveSources(): Int {
        return detectAndSaveSources(
            database = database,
            rawAlerts = rawAlerts
        )
    }

    suspend fun confirmSourceAndParse(
        source: FinancialSourceEntity,
        accountType: String
    ): ParseRunResult {
        database.financialSourceDao().confirmAccountType(
            sourceKey = source.sourceKey,
            accountType = accountType,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        return parseIdentifiedSourceTransactions(database)
    }

    suspend fun saveMergedRule(
        sourceKey: String,
        matchPhrase: String,
        merchantName: String? = null,
        categoryName: String? = null,
        subcategoryName: String? = null,
        transactionType: String? = null,
        reviewStatus: String? = null,
        excludedFromSpending: Boolean? = null,
        appliesToTreatment: String? = null,
        applyCategoryAutomatically: Boolean = true,
        requiresReview: Boolean = false
    ) {
        val cleanedPhrase = matchPhrase.trim()
        if (cleanedPhrase.isBlank()) return

        val normalized = normalizeRulePhrase(cleanedPhrase)
        val now = System.currentTimeMillis()

        val existing = database.transactionRuleDao().getBySourceAndPhrase(
            sourceKey = sourceKey,
            normalizedMatchPhrase = normalized
        )

        val merged = if (existing == null) {
            TransactionRuleEntity(
                sourceKey = sourceKey,
                matchPhrase = cleanedPhrase,
                normalizedMatchPhrase = normalized,
                merchantName = merchantName,
                categoryName = categoryName,
                subcategoryName = subcategoryName,
                transactionType = transactionType,
                reviewStatus = reviewStatus,
                excludedFromSpending = excludedFromSpending,
                appliesToTreatment = appliesToTreatment,
                applyCategoryAutomatically = applyCategoryAutomatically,
                requiresReview = requiresReview,
                active = true,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        } else {
            existing.copy(
                matchPhrase = cleanedPhrase,
                merchantName = merchantName ?: existing.merchantName,
                categoryName = categoryName ?: existing.categoryName,
                subcategoryName = subcategoryName ?: existing.subcategoryName,
                transactionType = transactionType ?: existing.transactionType,
                reviewStatus = reviewStatus ?: existing.reviewStatus,
                excludedFromSpending = excludedFromSpending ?: existing.excludedFromSpending,
                appliesToTreatment = appliesToTreatment ?: existing.appliesToTreatment,
                applyCategoryAutomatically = applyCategoryAutomatically,
                requiresReview = requiresReview || existing.requiresReview,
                active = true,
                updatedAtEpochMs = now
            )
        }

        database.transactionRuleDao().upsert(merged)
    }

    if (selectedSource != null) {
        SourceDetailScreen(
            source = selectedSource!!,
            matchingAlerts = selectedSourceAlerts,
            onBack = {
                selectedSource = null
            },
            onMarkSourceType = { accountType ->
                scope.launch(Dispatchers.IO) {
                    val result = confirmSourceAndParse(selectedSource!!, accountType)

                    withContext(Dispatchers.Main) {
                        statusText = "Marked source as $accountType and parsed ${result.parsedCount} new transactions."
                        selectedSource = null
                    }
                }
            },
            onDismissAsNonSource = {
                scope.launch(Dispatchers.IO) {
                    val source = selectedSource!!
                    val sourceKey = source.sourceKey

                    database.financialSourceDao().ignoreSource(
                        sourceKey = sourceKey,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    val deletedTransactions = database.transactionDao()
                        .deleteBySourceKey(sourceKey)
                    updateRawAlertStatusesForSource(
                        database = database,
                        source = source,
                        status = "IGNORED_SOURCE"
                    )

                    withContext(Dispatchers.Main) {
                        statusText = "Dismissed source as non-source and removed $deletedTransactions parsed transactions."
                        selectedSource = null
                    }
                }
            },
            onMoveToUncategorized = {
                scope.launch(Dispatchers.IO) {
                    val source = selectedSource!!
                    val sourceKey = source.sourceKey

                    database.financialSourceDao().resetSourceConfirmation(
                        sourceKey = sourceKey,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    val deletedTransactions = database.transactionDao()
                        .deleteBySourceKey(sourceKey)
                    updateRawAlertStatusesForSource(
                        database = database,
                        source = source,
                        status = "IMPORTED_SMS"
                    )

                    withContext(Dispatchers.Main) {
                        statusText = "Moved source back to uncategorized and removed $deletedTransactions parsed transactions."
                        selectedSource = null
                    }
                }
            }
        )
    } else if (selectedTransaction != null) {
        val matchingRawAlert = rawAlerts.firstOrNull {
            it.id == selectedTransaction!!.rawAlertId
        }

        TransactionDetailScreen(
            transaction = selectedTransaction!!,
            rawAlert = matchingRawAlert,
            onBack = {
                selectedTransaction = null
            },
            onUpdateTransactionType = { transactionType, excludedFromSpending ->
                scope.launch(Dispatchers.IO) {
                    database.transactionDao().updateTransactionType(
                        transactionId = selectedTransaction!!.id,
                        transactionType = transactionType,
                        excludedFromSpending = excludedFromSpending,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            transactionType = transactionType,
                            accountingTreatment = transactionType,
                            excludedFromSpending = excludedFromSpending,
                            treatmentUserEdited = true,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            },
            onUpdateReviewStatus = { reviewStatus ->
                scope.launch(Dispatchers.IO) {
                    database.transactionDao().updateReviewStatus(
                        transactionId = selectedTransaction!!.id,
                        reviewStatus = reviewStatus,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            reviewStatus = reviewStatus,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            },
            onUpdateExcludedFromSpending = { excluded ->
                scope.launch(Dispatchers.IO) {
                    database.transactionDao().updateExcludedFromSpending(
                        transactionId = selectedTransaction!!.id,
                        excludedFromSpending = excluded,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            excludedFromSpending = excluded,
                            treatmentUserEdited = true,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            },
            onUpdateMerchant = { merchantName ->
                scope.launch(Dispatchers.IO) {
                    val cleanedMerchant = merchantName.trim().ifBlank { null }

                    database.transactionDao().updateMerchant(
                        transactionId = selectedTransaction!!.id,
                        merchantRaw = cleanedMerchant,
                        displayMerchantName = cleanedMerchant,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            merchantRaw = cleanedMerchant,
                            displayMerchantName = cleanedMerchant,
                            merchantUserEdited = true,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            },
            onUpdateCategory = { category, subcategory ->
                scope.launch(Dispatchers.IO) {
                    val cleanedCategory = category.trim().ifBlank { null }
                    val cleanedSubcategory = subcategory.trim().ifBlank { null }

                    database.transactionDao().updateCategory(
                        transactionId = selectedTransaction!!.id,
                        categoryName = cleanedCategory,
                        subcategoryName = cleanedSubcategory,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            categoryName = cleanedCategory,
                            subcategoryName = cleanedSubcategory,
                            categoryUserEdited = true,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            },
            onApplyMerchantToSimilar = { matchPhrase, merchantName, onComplete ->
                scope.launch(Dispatchers.IO) {
                    val cleanedPhrase = matchPhrase.trim()
                    val cleanedMerchant = merchantName.trim().ifBlank { null }

                    if (cleanedPhrase.isBlank()) {
                        withContext(Dispatchers.Main) {
                            onComplete(0)
                        }
                        return@launch
                    }

                    saveMergedRule(
                        sourceKey = selectedTransaction!!.sourceKey,
                        matchPhrase = cleanedPhrase,
                        merchantName = cleanedMerchant
                    )

                    val updatedCount = database.transactionDao().updateMerchantForSimilarRawText(
                        sourceKey = selectedTransaction!!.sourceKey,
                        likePattern = "%$cleanedPhrase%",
                        merchantName = cleanedMerchant,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            merchantRaw = cleanedMerchant,
                            displayMerchantName = cleanedMerchant,
                            merchantUserEdited = true,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )

                        onComplete(updatedCount)
                    }
                }
            },
            onApplyCurrentClassificationToSimilar = { matchPhrase, onComplete ->
                scope.launch(Dispatchers.IO) {
                    val cleanedPhrase = matchPhrase.trim()

                    if (cleanedPhrase.isBlank()) {
                        withContext(Dispatchers.Main) {
                            onComplete(0)
                        }
                        return@launch
                    }

                    saveMergedRule(
                        sourceKey = selectedTransaction!!.sourceKey,
                        matchPhrase = cleanedPhrase,
                        transactionType = selectedTransaction!!.transactionType,
                        reviewStatus = selectedTransaction!!.reviewStatus,
                        excludedFromSpending = selectedTransaction!!.excludedFromSpending
                    )

                    val updatedCount = database.transactionDao().updateClassificationForSimilarRawText(
                        sourceKey = selectedTransaction!!.sourceKey,
                        likePattern = "%$cleanedPhrase%",
                        transactionType = selectedTransaction!!.transactionType,
                        reviewStatus = selectedTransaction!!.reviewStatus,
                        excludedFromSpending = selectedTransaction!!.excludedFromSpending,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        onComplete(updatedCount)
                    }
                }
            },
            onApplyCategoryToSimilar = { matchPhrase, category, subcategory, onComplete ->
                scope.launch(Dispatchers.IO) {
                    val cleanedPhrase = matchPhrase.trim()
                    val cleanedCategory = category.trim().ifBlank { null }
                    val cleanedSubcategory = subcategory.trim().ifBlank { null }

                    if (cleanedPhrase.isBlank()) {
                        withContext(Dispatchers.Main) {
                            onComplete(0)
                        }
                        return@launch
                    }

                    saveMergedRule(
                        sourceKey = selectedTransaction!!.sourceKey,
                        matchPhrase = cleanedPhrase,
                        categoryName = cleanedCategory,
                        subcategoryName = cleanedSubcategory
                    )

                    val updatedCount = database.transactionDao().updateCategoryForSimilarRawText(
                        sourceKey = selectedTransaction!!.sourceKey,
                        likePattern = "%$cleanedPhrase%",
                        categoryName = cleanedCategory,
                        subcategoryName = cleanedSubcategory,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            categoryName = cleanedCategory,
                            subcategoryName = cleanedSubcategory,
                            categoryUserEdited = true,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )

                        onComplete(updatedCount)
                    }
                }
            }
        )
    } else if (activeScreen == AppScreen.SETUP) {
        SetupScreen(
            rawAlertCount = rawAlertCount,
            sources = sources,
            statusText = statusText,
            onBack = {
                activeScreen = AppScreen.HOME
            },
            onBackfillSmsHistory = {
                statusText = "Importing SMS history..."
                onBackfillSmsHistory()
            },
            onRefreshLatestSms = {
                statusText = "Refreshing SMS..."
                onRefreshLatestSms()
            },
            onDetectSources = {
                statusText = "Looking for financial senders..."

                scope.launch(Dispatchers.IO) {
                    val detectedCount = detectAndSaveSources()

                    withContext(Dispatchers.Main) {
                        statusText = "Found $detectedCount possible financial senders."
                    }
                }
            },
            onParseIdentifiedSources = {
                statusText = "Building spending dashboard..."

                scope.launch(Dispatchers.IO) {
                    val result = parseIdentifiedSourceTransactions(database)

                    withContext(Dispatchers.Main) {
                        statusText = "Parsed ${result.parsedCount} new transactions."
                    }
                }
            },
            onConfirmSource = { source, accountType ->
                statusText = "Confirming ${source.displayName ?: source.sourceAddress}..."

                scope.launch(Dispatchers.IO) {
                    val result = confirmSourceAndParse(source, accountType)

                    withContext(Dispatchers.Main) {
                        statusText = "Confirmed source and parsed ${result.parsedCount} new transactions."
                    }
                }
            },
            onDismissSource = { source ->
                statusText = "Dismissing ${source.displayName ?: source.sourceAddress}..."

                scope.launch(Dispatchers.IO) {
                    database.financialSourceDao().ignoreSource(
                        sourceKey = source.sourceKey,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    database.transactionDao().deleteBySourceKey(source.sourceKey)
                    updateRawAlertStatusesForSource(
                        database = database,
                        source = source,
                        status = "IGNORED_SOURCE"
                    )

                    withContext(Dispatchers.Main) {
                        statusText = "Dismissed source."
                    }
                }
            }
        )
    } else if (activeScreen == AppScreen.SUMMARY) {
        SpendingSummaryScreen(
            transactions = transactions,
            onBack = {
                activeScreen = AppScreen.HOME
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
            }
        )
    }

    else if (activeScreen == AppScreen.REVIEW_QUEUE) {
        ReviewQueueScreen(
            transactions = transactions,
            onBack = {
                activeScreen = AppScreen.HOME
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
            }
        )

    }

    else if (activeScreen == AppScreen.TRANSACTIONS) {
        TransactionReviewScreen(
            transactions = transactions,
            onBack = {
                activeScreen = AppScreen.HOME
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
            }
        )
    } else if (activeScreen == AppScreen.RULES) {
        RulesScreen(
            rules = activeRules,
            onBack = {
                activeScreen = AppScreen.HOME
            },
            onDisableRule = { rule ->
                scope.launch(Dispatchers.IO) {
                    database.transactionRuleDao().setRuleActive(
                        ruleId = rule.id,
                        active = false,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    withContext(Dispatchers.Main) {
                        statusText = "Disabled rule: ${rule.matchPhrase}"
                    }
                }
            },
            onDeleteRule = { rule ->
                scope.launch(Dispatchers.IO) {
                    database.transactionRuleDao().deleteById(rule.id)
                    withContext(Dispatchers.Main) {
                        statusText = "Deleted rule: ${rule.matchPhrase}"
                    }
                }
            }
        )
    } else if (selectedMerchant != null) {
        val merchantTransactions = transactions
            .filter {
                val merchant = it.displayMerchantName
                    ?: it.merchantRaw
                    ?: ""
                merchant.equals(selectedMerchant!!.merchantName, ignoreCase = true)
            }
            .sortedByDescending { it.occurredAtEpochMs }

        MerchantDetailScreen(
            merchant = selectedMerchant!!,
            transactions = merchantTransactions,
            onBack = {
                selectedMerchant = null
            },
            onUpdateMerchantCategory = { category, subcategory, treatment, applyCategoryAutomatically, requiresReview ->
                scope.launch(Dispatchers.IO) {
                    val cleanedCategory = category.trim().ifBlank { null }
                    val cleanedSubcategory = subcategory.trim().ifBlank { null }
                    val cleanedTreatment = treatment.trim().ifBlank {
                        selectedMerchant!!.primaryTreatment
                    }
                    val now = System.currentTimeMillis()

                    if (applyCategoryAutomatically) {
                        database.transactionDao().updateCategoryForMerchantName(
                            merchantName = selectedMerchant!!.merchantName,
                            categoryName = cleanedCategory,
                            subcategoryName = cleanedSubcategory,
                            updatedAtEpochMs = now
                        )
                    }

                    database.transactionDao().updateTreatmentForMerchantName(
                        merchantName = selectedMerchant!!.merchantName,
                        accountingTreatment = cleanedTreatment,
                        excludedFromSpending = TransactionTreatments.defaultExcludedFromSpending(cleanedTreatment),
                        updatedAtEpochMs = now
                    )

                    saveMergedRule(
                        sourceKey = MERCHANT_DEFAULT_RULE_SOURCE_KEY,
                        matchPhrase = selectedMerchant!!.merchantName,
                        merchantName = selectedMerchant!!.merchantName,
                        categoryName = cleanedCategory,
                        subcategoryName = cleanedSubcategory,
                        transactionType = cleanedTreatment,
                        excludedFromSpending = TransactionTreatments.defaultExcludedFromSpending(cleanedTreatment),
                        appliesToTreatment = cleanedTreatment,
                        applyCategoryAutomatically = applyCategoryAutomatically,
                        requiresReview = requiresReview
                    )

                    withContext(Dispatchers.Main) {
                        selectedMerchant = selectedMerchant!!.copy(
                            primaryTreatment = cleanedTreatment,
                            categoryName = cleanedCategory,
                            subcategoryName = cleanedSubcategory,
                            uncategorizedCount = if (applyCategoryAutomatically) 0 else selectedMerchant!!.uncategorizedCount
                        )
                    }
                }
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
            }
        )
    } else if (activeScreen == AppScreen.MERCHANTS) {
        MerchantReviewScreen(
            transactions = transactions,
            onBack = {
                activeScreen = AppScreen.HOME
            },
            onMerchantSelected = { merchant ->
                selectedMerchant = merchant
            }
        )
    } else if (activeScreen == AppScreen.HOME) {
        HomeScreen(
            rawAlertCount = rawAlertCount,
            sourceCount = sourceCount,
            transactionCount = transactionCount,
            activeRuleCount = activeRuleCount,
            uncategorizedSourceCount = uncategorizedSourceCount,
            identifiedSourceCount = identifiedSourceCount,
            reviewIssueCount = reviewIssueCount,
            currentMonthSpendingCents = currentMonthSpendingCents,
            currentMonthIncomeCents = currentMonthIncomeCents,
            currentMonthRefundCents = currentMonthRefundCents,
            currentMonthMovementCents = currentMonthMovementCents,
            previousMonthSpendingCents = previousMonthSpendingCents,
            currentMonthExpenseCount = currentMonthExpenses.size,
            topCategoryLabel = topCategoryLabel,
            topMerchantLabel = topMerchantLabel,
            onOpenSetup = {
                activeScreen = AppScreen.SETUP
            },
            onOpenSources = {
                activeScreen = AppScreen.SOURCES
            },
            onOpenReviewQueue = {
                activeScreen = AppScreen.REVIEW_QUEUE
            },
            onOpenTransactions = {
                activeScreen = AppScreen.TRANSACTIONS
            },
            onOpenSummary = {
                activeScreen = AppScreen.SUMMARY
            },
            onOpenTools = {
                activeScreen = AppScreen.TOOLS
            },
            onOpenMerchants = {
                activeScreen = AppScreen.MERCHANTS
            },
            onOpenRules = {
                activeScreen = AppScreen.RULES
            }
        )
    } else if (activeScreen == AppScreen.TOOLS) {
        ToolsScreen(
            rawAlertCount = rawAlertCount,
            sourceCount = sourceCount,
            transactionCount = transactionCount,
            activeRuleCount = activeRuleCount,
            statusText = statusText,
            onBack = {
                activeScreen = AppScreen.HOME
            },
            onOpenSources = {
                activeScreen = AppScreen.SOURCES
            },
            onOpenRules = {
                activeScreen = AppScreen.RULES
            },
            onBackfillSmsHistory = {
                statusText = "Running SMS backfill..."
                onBackfillSmsHistory()
            },
            onRefreshLatestSms = {
                statusText = "Refreshing latest SMS..."
                onRefreshLatestSms()
            },
            onDetectSources = {
                statusText = "Detecting SMS sources..."

                scope.launch(Dispatchers.IO) {
                    val detectedCount = detectAndSaveSources()

                    withContext(Dispatchers.Main) {
                        statusText = "Detected $detectedCount possible SMS sources."
                    }
                }
            },
            onParseIdentifiedSources = {
                statusText = "Parsing transactions from identified sources..."

                scope.launch(Dispatchers.IO) {
                    val result = parseIdentifiedSourceTransactions(database)

                    withContext(Dispatchers.Main) {
                        statusText =
                            "Matched ${result.matchedAlertCount} SMS from identified sources. Parsed ${result.parsedCount}, skipped existing ${result.skippedCount}, ignored ${result.ignoredNonTransactionCount}, failed ${result.failedCount}."
                    }
                }
            },
            onReparseTransactions = {
                statusText = "Reparsing transactions from identified sources..."

                scope.launch(Dispatchers.IO) {
                    database.transactionDao().deleteAll()
                    val result = parseIdentifiedSourceTransactions(database)

                    withContext(Dispatchers.Main) {
                        statusText =
                            "Reparsed from ${result.matchedAlertCount} SMS. Parsed ${result.parsedCount}, ignored ${result.ignoredNonTransactionCount}, failed ${result.failedCount}."
                    }
                }
            },
            onReapplySavedRules = {
                statusText = "Reapplying saved rules to existing transactions..."

                scope.launch(Dispatchers.IO) {
                    val updatedCount = reapplySavedRulesToExistingTransactions(database)

                    withContext(Dispatchers.Main) {
                        statusText = "Reapplied saved rules to $updatedCount existing transactions."
                    }
                }
            },
            onExportTransactions = {
                statusText = "Opening transaction export..."
                onExportTransactions()
            },
            onExportParserCorpus = {
                statusText = "Opening parser corpus export..."
                onExportParserCorpus()
            },
            onClearAll = {
                scope.launch(Dispatchers.IO) {
                    database.transactionDao().deleteAll()
                    database.transactionRuleDao().deleteAll()
                    database.financialSourceDao().deleteAll()
                    database.rawAlertDao().deleteAll()

                    withContext(Dispatchers.Main) {
                        statusText = "Cleared imported SMS, sources, rules, and transactions."
                    }
                }
            }
        )
    } else {
        SourceListScreen(
            sources = sources,
            onBack = {
                activeScreen = AppScreen.HOME
            },
            onSourceSelected = { source ->
                selectedSource = source
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    rawAlertCount: Int,
    sources: List<FinancialSourceEntity>,
    statusText: String,
    onBack: () -> Unit,
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit,
    onDetectSources: () -> Unit,
    onParseIdentifiedSources: () -> Unit,
    onConfirmSource: (FinancialSourceEntity, String) -> Unit,
    onDismissSource: (FinancialSourceEntity) -> Unit
) {
    val possibleSources = sources
        .filter { !it.userConfirmed && !it.ignored }
        .sortedWith(
            compareByDescending<FinancialSourceEntity> { it.detectionConfidence }
                .thenByDescending { it.messageCount }
                .thenByDescending { it.lastSeenEpochMs }
        )
    val identifiedCount = sources.count { it.userConfirmed && !it.ignored }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Connect SMS Sources",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Choose which SMS senders are real financial alerts. Once a source is confirmed, LedgerLens parses its transactions automatically.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Imported SMS: $rawAlertCount")
                        Text("Confirmed sources: $identifiedCount")
                        Text("Possible sources: ${possibleSources.size}")

                        if (statusText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Import",
                            style = MaterialTheme.typography.titleSmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRefreshLatestSms,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Refresh")
                            }

                            OutlinedButton(
                                onClick = onBackfillSmsHistory,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Backfill")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onDetectSources,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Find Sources")
                        }

                        if (identifiedCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = onParseIdentifiedSources,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Build Dashboard")
                            }
                        }
                    }
                }
            }

            item {
                SourceSectionHeader(
                    title = "Possible Financial Sources",
                    count = possibleSources.size
                )
            }

            if (possibleSources.isEmpty()) {
                item {
                    EmptySectionText(
                        if (rawAlertCount == 0) {
                            "Import SMS first. Possible financial senders will show here."
                        } else {
                            "No possible sources need review."
                        }
                    )
                }
            } else {
                items(
                    items = possibleSources,
                    key = { it.sourceKey }
                ) { source ->
                    SetupSourceCard(
                        source = source,
                        onConfirmSource = onConfirmSource,
                        onDismissSource = onDismissSource
                    )
                }
            }
        }
    }
}

@Composable
fun SetupSourceCard(
    source: FinancialSourceEntity,
    onConfirmSource: (FinancialSourceEntity, String) -> Unit,
    onDismissSource: (FinancialSourceEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = source.displayName
                    ?: source.institutionName
                    ?: "Sender ${source.sourceAddress}",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Sender ${source.sourceAddress} - ${source.messageCount} messages - ${"%.0f".format(source.detectionConfidence * 100)}% confidence",
                style = MaterialTheme.typography.bodyMedium
            )

            if (!source.sampleMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = source.sampleMessage.take(180),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onConfirmSource(source, "CREDIT_CARD") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Credit")
                }

                Button(
                    onClick = { onConfirmSource(source, "CHECKING") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Checking")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onConfirmSource(source, "DEBIT_CARD") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Debit")
                }

                OutlinedButton(
                    onClick = { onConfirmSource(source, "UNKNOWN") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Other")
                }

                OutlinedButton(
                    onClick = { onDismissSource(source) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ignore")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceListScreen(
    sources: List<FinancialSourceEntity>,
    onBack: () -> Unit,
    onSourceSelected: (FinancialSourceEntity) -> Unit
) {
    var searchText by remember {
        mutableStateOf("")
    }

    val visibleSources = remember(sources, searchText) {
        val query = searchText.trim().lowercase(Locale.US)
        if (query.isBlank()) {
            sources
        } else {
            sources.filter { source ->
                listOfNotNull(
                    source.displayName,
                    source.institutionName,
                    source.sourceAddress,
                    source.accountHint,
                    source.suggestedAccountType,
                    source.confirmedAccountType,
                    source.sampleMessage
                ).any { it.lowercase(Locale.US).contains(query) }
            }
        }
    }

    val uncategorizedSources = visibleSources.filter { !it.userConfirmed && !it.ignored }
    val identifiedSources = visibleSources.filter { it.userConfirmed && !it.ignored }
    val nonSources = visibleSources.filter { it.ignored }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Review SMS senders and classify them as identified sources, non-sources, or uncategorized possible sources.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search sources") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                SourceSectionHeader(
                    title = "Uncategorized Possible Sources",
                    count = uncategorizedSources.size
                )
            }

            if (uncategorizedSources.isEmpty()) {
                item {
                    EmptySectionText("No uncategorized possible sources.")
                }
            } else {
                items(
                    items = uncategorizedSources,
                    key = { it.sourceKey }
                ) { source ->
                    SourceCompactCard(
                        source = source,
                        categoryLabel = "Uncategorized",
                        onClick = { onSourceSelected(source) }
                    )
                }
            }

            item {
                SourceSectionHeader(
                    title = "Identified Sources",
                    count = identifiedSources.size
                )
            }

            if (identifiedSources.isEmpty()) {
                item {
                    EmptySectionText("No identified sources yet.")
                }
            } else {
                items(
                    items = identifiedSources,
                    key = { it.sourceKey }
                ) { source ->
                    SourceCompactCard(
                        source = source,
                        categoryLabel = "Identified",
                        onClick = { onSourceSelected(source) }
                    )
                }
            }

            item {
                SourceSectionHeader(
                    title = "Non-Sources",
                    count = nonSources.size
                )
            }

            if (nonSources.isEmpty()) {
                item {
                    EmptySectionText("No non-sources yet.")
                }
            } else {
                items(
                    items = nonSources,
                    key = { it.sourceKey }
                ) { source ->
                    SourceCompactCard(
                        source = source,
                        categoryLabel = "Non-Source",
                        onClick = { onSourceSelected(source) }
                    )
                }
            }
        }
    }
}

@Composable
fun SourceSectionHeader(
    title: String,
    count: Int
) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun EmptySectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SourceCompactCard(
    source: FinancialSourceEntity,
    categoryLabel: String,
    onClick: () -> Unit
) {
    val effectiveType = source.confirmedAccountType
        ?: source.suggestedAccountType

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = source.displayName
                    ?: source.institutionName
                    ?: "Unknown financial source",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$categoryLabel • Type: $effectiveType • Messages: ${source.messageCount}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Sender: ${source.sourceAddress}",
                style = MaterialTheme.typography.labelSmall
            )

            if (!source.accountHint.isNullOrBlank()) {
                Text(
                    text = "Detected account/card hints: ${source.accountHint}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = "Confidence: ${"%.0f".format(source.detectionConfidence * 100)}%",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Tap to review SMS and change category",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailScreen(
    source: FinancialSourceEntity,
    matchingAlerts: List<RawAlertEntity>,
    onBack: () -> Unit,
    onMarkSourceType: (String) -> Unit,
    onDismissAsNonSource: () -> Unit,
    onMoveToUncategorized: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    val effectiveType = source.confirmedAccountType
        ?: source.suggestedAccountType

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source Detail") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                SourceDetailSummaryCard(
                    source = source,
                    effectiveType = effectiveType,
                    matchingCount = matchingAlerts.size
                )
            }

            item {
                SourceActionCard(
                    onMarkSourceType = onMarkSourceType,
                    onDismissAsNonSource = onDismissAsNonSource,
                    onMoveToUncategorized = onMoveToUncategorized
                )
            }

            item {
                Text(
                    text = "Matching SMS (${matchingAlerts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (matchingAlerts.isEmpty()) {
                item {
                    EmptySectionText("No matching SMS found for this source.")
                }
            } else {
                items(
                    items = matchingAlerts,
                    key = { it.notificationKey }
                ) { alert ->
                    SmsMessageCard(
                        alert = alert,
                        formatter = formatter
                    )
                }
            }
        }
    }
}

@Composable
fun SourceDetailSummaryCard(
    source: FinancialSourceEntity,
    effectiveType: String,
    matchingCount: Int
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = source.displayName
                    ?: source.institutionName
                    ?: "Unknown financial source",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Sender: ${source.sourceAddress}")
            Text("Suggested type: ${source.suggestedAccountType}")
            Text("Confirmed type: ${source.confirmedAccountType ?: "Not confirmed"}")
            Text("Effective type: $effectiveType")
            Text("Matching SMS: $matchingCount")

            if (!source.accountHint.isNullOrBlank()) {
                Text("Detected account/card hints: ${source.accountHint}")
            }

            Text("Confidence: ${"%.0f".format(source.detectionConfidence * 100)}%")
            Text("First seen: ${formatter.format(Date(source.firstSeenEpochMs))}")
            Text("Last seen: ${formatter.format(Date(source.lastSeenEpochMs))}")

            val category = when {
                source.ignored -> "Non-Source"
                source.userConfirmed -> "Identified Source"
                else -> "Uncategorized Possible Source"
            }

            Text("Current category: $category")
        }
    }
}

@Composable
fun SourceActionCard(
    onMarkSourceType: (String) -> Unit,
    onDismissAsNonSource: () -> Unit,
    onMoveToUncategorized: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Choose Source Category",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onMarkSourceType("CREDIT_CARD") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Credit Card")
                }

                Button(
                    onClick = { onMarkSourceType("CHECKING") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Checking")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onMarkSourceType("SAVINGS") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Savings")
                }

                Button(
                    onClick = { onMarkSourceType("DEBIT_CARD") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Debit Card")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onMarkSourceType("UNKNOWN") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Valid Source, Type Unknown")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDismissAsNonSource,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss as Non-Source")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onMoveToUncategorized,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Move Back to Uncategorized")
            }
        }
    }
}

@Composable
fun SmsMessageCard(
    alert: RawAlertEntity,
    formatter: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = formatter.format(Date(alert.postTimeEpochMs)),
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Status: ${alert.processingStatus}",
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = alert.combinedText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    transactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    var selectedFilter by remember {
        mutableStateOf(TransactionFilter.ALL)
    }

    var searchText by remember {
        mutableStateOf("")
    }

    val sortedTransactions = remember(transactions) {
        transactions.sortedByDescending { it.occurredAtEpochMs }
    }

    val filteredTransactions = remember(sortedTransactions, selectedFilter, searchText) {
        val base = when (selectedFilter) {
            TransactionFilter.ALL -> sortedTransactions

            TransactionFilter.NEEDS_REVIEW -> sortedTransactions.filter {
                it.reviewStatus == "NEEDS_REVIEW"
            }

            TransactionFilter.EXPENSES -> sortedTransactions.filter {
                it.accountingTreatment == TransactionTreatments.EXPENSE
            }

            TransactionFilter.TRANSFERS -> sortedTransactions.filter {
                it.accountingTreatment in setOf(
                    TransactionTreatments.TRANSFER,
                    TransactionTreatments.PERSON_TO_PERSON
                )
            }

            TransactionFilter.CREDIT_CARD_PAYMENTS -> sortedTransactions.filter {
                it.accountingTreatment == TransactionTreatments.CREDIT_CARD_PAYMENT
            }

            TransactionFilter.EXCLUDED_FROM_SPENDING -> sortedTransactions.filter {
                it.accountingTreatment != TransactionTreatments.EXPENSE ||
                        it.excludedFromSpending
            }
        }

        val query = searchText.trim().lowercase(Locale.US)
        if (query.isBlank()) {
            base
        } else {
            base.filter { transaction ->
                listOfNotNull(
                    transaction.displayMerchantName,
                    transaction.merchantRaw,
                    transaction.categoryName,
                    transaction.subcategoryName,
                    transaction.sourceInstitution,
                    transaction.accountingTreatment,
                    transaction.transactionType,
                    transaction.reviewStatus
                ).any { it.lowercase(Locale.US).contains(query) }
            }
        }
    }

    val totalCount = sortedTransactions.size
    val needsReviewCount = sortedTransactions.count { it.reviewStatus == "NEEDS_REVIEW" }
    val excludedCount = sortedTransactions.count {
        it.accountingTreatment != TransactionTreatments.EXPENSE || it.excludedFromSpending
    }
    val expenseCount = sortedTransactions.count { it.accountingTreatment == TransactionTreatments.EXPENSE }
    val transferCount = sortedTransactions.count {
        it.accountingTreatment in setOf(
            TransactionTreatments.TRANSFER,
            TransactionTreatments.PERSON_TO_PERSON
        )
    }
    val creditCardPaymentCount = sortedTransactions.count {
        it.accountingTreatment == TransactionTreatments.CREDIT_CARD_PAYMENT
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                TransactionSummaryCard(
                    totalCount = totalCount,
                    expenseCount = expenseCount,
                    needsReviewCount = needsReviewCount,
                    excludedCount = excludedCount
                )
            }

            item {
                TransactionFilterCard(
                    selectedFilter = selectedFilter,
                    totalCount = totalCount,
                    needsReviewCount = needsReviewCount,
                    expenseCount = expenseCount,
                    transferCount = transferCount,
                    creditCardPaymentCount = creditCardPaymentCount,
                    excludedCount = excludedCount,
                    filteredCount = filteredTransactions.size,
                    onFilterSelected = { selectedFilter = it }
                )
            }

            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search transactions") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    EmptySectionText("No transactions found for this filter.")
                }
            } else {
                items(
                    items = filteredTransactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        onClick = { onTransactionSelected(transaction) }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionSummaryCard(
    totalCount: Int,
    expenseCount: Int,
    needsReviewCount: Int,
    excludedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Parsed Transaction Summary",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Total parsed: $totalCount")
            Text("Expenses: $expenseCount")
            Text("Needs review: $needsReviewCount")
            Text("Tracked outside spending: $excludedCount")
        }
    }
}

@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    val amount = transaction.amountCents / 100.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = transaction.displayMerchantName
                    ?: transaction.sourceInstitution
                    ?: treatmentLabel(transaction.accountingTreatment),
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${"%.2f".format(amount)} • ${treatmentLabel(transaction.accountingTreatment)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = formatter.format(Date(transaction.occurredAtEpochMs)),
                style = MaterialTheme.typography.labelSmall
            )

            val sourceText = listOfNotNull(
                transaction.sourceInstitution,
                transaction.accountHint?.let { "Hint $it" }
            ).joinToString(" • ")

            if (sourceText.isNotBlank()) {
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = "Review: ${transaction.reviewStatus}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Confidence: ${"%.0f".format(transaction.parseConfidence * 100)}%",
                style = MaterialTheme.typography.labelSmall
            )

            if (transaction.excludedFromSpending) {
                Text(
                    text = "Tracked outside Spending Summary",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (!transaction.parserNotes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transaction.parserNotes,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tap for details",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun TransactionFilterCard(
    selectedFilter: TransactionFilter,
    totalCount: Int,
    needsReviewCount: Int,
    expenseCount: Int,
    transferCount: Int,
    creditCardPaymentCount: Int,
    excludedCount: Int,
    filteredCount: Int,
    onFilterSelected: (TransactionFilter) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Showing $filteredCount transactions",
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilterButton(
                    label = "All ($totalCount)",
                    selected = selectedFilter == TransactionFilter.ALL,
                    onClick = { onFilterSelected(TransactionFilter.ALL) },
                    modifier = Modifier.weight(1f)
                )

                TransactionFilterButton(
                    label = "Review ($needsReviewCount)",
                    selected = selectedFilter == TransactionFilter.NEEDS_REVIEW,
                    onClick = { onFilterSelected(TransactionFilter.NEEDS_REVIEW) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilterButton(
                    label = "Expenses ($expenseCount)",
                    selected = selectedFilter == TransactionFilter.EXPENSES,
                    onClick = { onFilterSelected(TransactionFilter.EXPENSES) },
                    modifier = Modifier.weight(1f)
                )

                TransactionFilterButton(
                    label = "Transfers ($transferCount)",
                    selected = selectedFilter == TransactionFilter.TRANSFERS,
                    onClick = { onFilterSelected(TransactionFilter.TRANSFERS) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilterButton(
                    label = "CC Pay ($creditCardPaymentCount)",
                    selected = selectedFilter == TransactionFilter.CREDIT_CARD_PAYMENTS,
                    onClick = { onFilterSelected(TransactionFilter.CREDIT_CARD_PAYMENTS) },
                    modifier = Modifier.weight(1f)
                )

                TransactionFilterButton(
                    label = "Other ($excludedCount)",
                    selected = selectedFilter == TransactionFilter.EXCLUDED_FROM_SPENDING,
                    onClick = { onFilterSelected(TransactionFilter.EXCLUDED_FROM_SPENDING) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TransactionFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity?,
    onBack: () -> Unit,
    onUpdateTransactionType: (String, Boolean) -> Unit,
    onUpdateReviewStatus: (String) -> Unit,
    onUpdateExcludedFromSpending: (Boolean) -> Unit,
    onUpdateMerchant: (String) -> Unit,
    onUpdateCategory: (String, String) -> Unit,
    onApplyMerchantToSimilar: (String, String, (Int) -> Unit) -> Unit,
    onApplyCurrentClassificationToSimilar: (String, (Int) -> Unit) -> Unit,
    onApplyCategoryToSimilar: (String, String, String, (Int) -> Unit) -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    var showTechnicalDetails by remember(transaction.id) {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Detail") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                TransactionOverviewCard(
                    transaction = transaction,
                    formatter = formatter
                )
            }

            if (
                transaction.accountingTreatment in TransactionTreatments.movementTreatments ||
                transaction.parserNotes.orEmpty().contains("Zelle", ignoreCase = true)
            ) {
                item {
                    TransferResolutionCard(
                        onMarkPersonalTransfer = {
                            onUpdateTransactionType(TransactionTreatments.TRANSFER, true)
                            onUpdateCategory("Transfer", "Personal")
                            onUpdateReviewStatus("REVIEWED")
                        },
                        onMarkReimbursement = {
                            onUpdateTransactionType(TransactionTreatments.INCOME, true)
                            onUpdateCategory("Reimbursement", "Personal")
                            onUpdateReviewStatus("REVIEWED")
                        },
                        onMarkExpense = {
                            onUpdateTransactionType(TransactionTreatments.EXPENSE, false)
                            onUpdateCategory("Other", "Uncategorized")
                            onUpdateReviewStatus("REVIEWED")
                        }
                    )
                }
            }

            item {
                TransactionCorrectionCard(
                    transaction = transaction,
                    onUpdateTransactionType = onUpdateTransactionType,
                    onUpdateReviewStatus = onUpdateReviewStatus,
                    onUpdateExcludedFromSpending = onUpdateExcludedFromSpending
                )
            }

            item {
                MerchantCorrectionCard(
                    transaction = transaction,
                    onUpdateMerchant = onUpdateMerchant
                )
            }

            item {
                CategoryCorrectionCard(
                    transaction = transaction,
                    onUpdateCategory = { category, subcategory ->
                        onUpdateCategory(category, subcategory)
                        onUpdateReviewStatus("REVIEWED")
                    }
                )
            }

            item {
                SimilarTransactionsCorrectionCard(
                    transaction = transaction,
                    rawAlert = rawAlert,
                    onApplyMerchantToSimilar = onApplyMerchantToSimilar,
                    onApplyCurrentClassificationToSimilar = onApplyCurrentClassificationToSimilar,
                    onApplyCategoryToSimilar = onApplyCategoryToSimilar
                )
            }

            item {
                OutlinedButton(
                    onClick = { showTechnicalDetails = !showTechnicalDetails },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showTechnicalDetails) "Hide Details" else "Inspect Details")
                }
            }

            if (showTechnicalDetails) {
                item {
                    TransactionTechnicalDetailsCard(
                        transaction = transaction,
                        rawAlert = rawAlert,
                        formatter = formatter
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionOverviewCard(
    transaction: TransactionEntity,
    formatter: SimpleDateFormat
) {
    val amount = transaction.amountCents / 100.0
    val merchant = transaction.displayMerchantName
        ?: transaction.merchantRaw
        ?: "Unknown merchant"
    val category = if (transaction.categoryName.isNullOrBlank()) {
        "Uncategorized"
    } else {
        transaction.categoryName +
                if (!transaction.subcategoryName.isNullOrBlank()) {
                    " / ${transaction.subcategoryName}"
                } else {
                    ""
                }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = merchant,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${"%.2f".format(amount)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Text("${treatmentLabel(transaction.accountingTreatment)} - $category")
            Text(formatter.format(Date(transaction.occurredAtEpochMs)))

            if (transaction.excludedFromSpending) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tracked outside Spending Summary",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (transaction.reviewStatus == "NEEDS_REVIEW") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Needs review",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
fun TransactionTechnicalDetailsCard(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity?,
    formatter: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Inspection",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow("Merchant raw", transaction.merchantRaw ?: "Not detected")
            DetailRow("Source institution", transaction.sourceInstitution ?: "Not detected")
            DetailRow("Account hint", transaction.accountHint ?: "Not detected")
            DetailRow("Review status", transaction.reviewStatus)
            DetailRow("Parse confidence", "${"%.0f".format(transaction.parseConfidence * 100)}%")
            DetailRow("Received at", formatter.format(Date(transaction.receivedAtEpochMs)))
            DetailRow("Source key", transaction.sourceKey)
            DetailRow("Parser notes", transaction.parserNotes ?: "No parser notes.")

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Original SMS",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (rawAlert == null) {
                Text(
                    text = "Original SMS was not found.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                DetailRow("SMS title", rawAlert.title ?: "None")
                DetailRow("SMS date", formatter.format(Date(rawAlert.postTimeEpochMs)))
                DetailRow("Raw status", rawAlert.processingStatus)

                Text(
                    text = rawAlert.combinedText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun TransferResolutionCard(
    onMarkPersonalTransfer: () -> Unit,
    onMarkReimbursement: () -> Unit,
    onMarkExpense: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "What was this transfer?",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Transfers can be personal movement, reimbursements, or real spending. Pick the closest treatment.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onMarkPersonalTransfer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Personal Transfer - Exclude")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMarkReimbursement,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reimbursement")
                }

                OutlinedButton(
                    onClick = onMarkExpense,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Count as Spending")
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun TransactionCorrectionCard(
    transaction: TransactionEntity,
    onUpdateTransactionType: (String, Boolean) -> Unit,
    onUpdateReviewStatus: (String) -> Unit,
    onUpdateExcludedFromSpending: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Classification",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Accounting Treatment",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUpdateTransactionType(TransactionTreatments.EXPENSE, false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Expense")
                }

                Button(
                    onClick = { onUpdateTransactionType(TransactionTreatments.TRANSFER, true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Transfer")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUpdateTransactionType(TransactionTreatments.CREDIT_CARD_PAYMENT, true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CC Pay")
                }

                Button(
                    onClick = { onUpdateTransactionType(TransactionTreatments.INCOME, true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Income")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUpdateTransactionType(TransactionTreatments.REFUND, true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refund")
                }

                Button(
                    onClick = { onUpdateTransactionType(TransactionTreatments.PERSON_TO_PERSON, true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Person")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onUpdateTransactionType(TransactionTreatments.UNKNOWN, true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mark as Unknown")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Review Status",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUpdateReviewStatus("REVIEWED") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reviewed")
                }

                Button(
                    onClick = { onUpdateReviewStatus("NEEDS_REVIEW") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Needs Review")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Spending Summary Override",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUpdateExcludedFromSpending(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Count in Spending")
                }

                Button(
                    onClick = { onUpdateExcludedFromSpending(true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Track Outside")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Current: ${treatmentLabel(transaction.accountingTreatment)} • ${transaction.reviewStatus} • ${
                    if (transaction.excludedFromSpending) "Outside spending" else "Spending"
                }",
                style = MaterialTheme.typography.labelSmall
            )

            if (!transaction.categoryName.isNullOrBlank()) {
                Text(
                    text = "Category: ${transaction.categoryName}" +
                            if (!transaction.subcategoryName.isNullOrBlank()) {
                                " / ${transaction.subcategoryName}"
                            } else {
                                ""
                            },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun MerchantCorrectionCard(
    transaction: TransactionEntity,
    onUpdateMerchant: (String) -> Unit
) {
    var merchantText by remember(transaction.id, transaction.displayMerchantName, transaction.merchantRaw) {
        mutableStateOf(
            transaction.displayMerchantName
                ?: transaction.merchantRaw
                ?: ""
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Merchant / Payee",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                label = { Text("Merchant or payee name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onUpdateMerchant(merchantText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Merchant")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Current parsed merchant: ${
                    transaction.displayMerchantName
                        ?: transaction.merchantRaw
                        ?: "Not detected"
                }",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun SimilarTransactionsCorrectionCard(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity?,
    onApplyMerchantToSimilar: (String, String, (Int) -> Unit) -> Unit,
    onApplyCurrentClassificationToSimilar: (String, (Int) -> Unit) -> Unit,
    onApplyCategoryToSimilar: (String, String, String, (Int) -> Unit) -> Unit
) {
    var matchPhrase by remember(transaction.id, rawAlert?.combinedText) {
        mutableStateOf(
            transaction.displayMerchantName
                ?: transaction.merchantRaw
                ?: ""
        )
    }

    var merchantText by remember(transaction.id, transaction.displayMerchantName, transaction.merchantRaw) {
        mutableStateOf(
            transaction.displayMerchantName
                ?: transaction.merchantRaw
                ?: ""
        )
    }

    var categoryText by remember(transaction.id, transaction.categoryName) {
        mutableStateOf(transaction.categoryName ?: "")
    }

    var subcategoryText by remember(transaction.id, transaction.subcategoryName) {
        mutableStateOf(transaction.subcategoryName ?: "")
    }

    var resultText by remember(transaction.id) {
        mutableStateOf("")
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Apply as Rule",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create or update a saved rule for transactions from the same source when the original SMS contains this phrase.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = matchPhrase,
                onValueChange = { matchPhrase = it },
                label = { Text("Match phrase from SMS") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                label = { Text("Merchant/payee to apply") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    resultText = "Applying merchant to similar transactions..."

                    onApplyMerchantToSimilar(matchPhrase, merchantText) { updatedCount ->
                        resultText = "Updated merchant on $updatedCount similar transactions."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Merchant to Similar")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    resultText = "Applying current type/review/spending treatment..."

                    onApplyCurrentClassificationToSimilar(matchPhrase) { updatedCount ->
                        resultText = "Updated classification on $updatedCount similar transactions."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Current Type/Review to Similar")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Apply category to similar",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = categoryText,
                onValueChange = { categoryText = it },
                label = { Text("Category to apply") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = subcategoryText,
                onValueChange = { subcategoryText = it },
                label = { Text("Subcategory to apply") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    resultText = "Applying category to similar transactions..."

                    onApplyCategoryToSimilar(
                        matchPhrase,
                        categoryText,
                        subcategoryText
                    ) { updatedCount ->
                        resultText = "Updated category on $updatedCount similar transactions."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Category to Similar")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Current classification: ${treatmentLabel(transaction.accountingTreatment)} • ${transaction.reviewStatus} • ${
                    if (transaction.excludedFromSpending) "Outside spending" else "Spending"
                }",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Current category: ${
                    transaction.categoryName ?: "Not assigned"
                } / ${
                    transaction.subcategoryName ?: "Not assigned"
                }",
                style = MaterialTheme.typography.labelSmall
            )

            if (resultText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = resultText,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (rawAlert != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Original SMS preview:",
                    style = MaterialTheme.typography.labelSmall
                )

                Text(
                    text = rawAlert.combinedText.take(250),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun CategoryCorrectionCard(
    transaction: TransactionEntity,
    onUpdateCategory: (String, String) -> Unit
) {
    var categoryText by remember(transaction.id, transaction.categoryName) {
        mutableStateOf(transaction.categoryName ?: "")
    }

    var subcategoryText by remember(transaction.id, transaction.subcategoryName) {
        mutableStateOf(transaction.subcategoryName ?: "")
    }

    val presets = listOf(
        "Groceries" to "General",
        "Restaurants" to "Dining Out",
        "Gas" to "Fuel",
        "Shopping" to "General",
        "Bills & Utilities" to "General",
        "Subscriptions" to "General",
        "Healthcare" to "General",
        "Travel" to "General",
        "Charity" to "Donation",
        "Transfer" to "Internal",
        "Other" to "Uncategorized"
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Quick presets",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            presets.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                categoryText = preset.first
                                subcategoryText = preset.second
                                onUpdateCategory(categoryText, subcategoryText)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(preset.first)
                        }
                    }

                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = categoryText,
                onValueChange = { categoryText = it },
                label = { Text("Category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = subcategoryText,
                onValueChange = { subcategoryText = it },
                label = { Text("Subcategory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onUpdateCategory(categoryText, subcategoryText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Category")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Current: ${
                    transaction.categoryName ?: "Not assigned"
                } / ${
                    transaction.subcategoryName ?: "Not assigned"
                }",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingSummaryScreen(
    transactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    var selectedMonthStart by remember {
        mutableLongStateOf(getCurrentMonthStartEpochMs())
    }

    var selectedCategorySummary by remember {
        mutableStateOf<CategorySpendSummary?>(null)
    }

    val selectedMonthEnd = remember(selectedMonthStart) {
        getNextMonthStartEpochMs(selectedMonthStart)
    }

    val includedExpensesForMonth = remember(transactions, selectedMonthStart, selectedMonthEnd) {
        transactions
            .filter {
                TransactionTreatments.countsAsSpending(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending
                )
            }
            .filter {
                it.occurredAtEpochMs >= selectedMonthStart &&
                        it.occurredAtEpochMs < selectedMonthEnd
            }
    }

    val totalExpenseCents = remember(includedExpensesForMonth) {
        includedExpensesForMonth.sumOf { it.amountCents }
    }

    val unassignedCount = remember(includedExpensesForMonth) {
        includedExpensesForMonth.count { it.categoryName.isNullOrBlank() }
    }

    val categorySummaries = remember(includedExpensesForMonth) {
        includedExpensesForMonth
            .groupBy {
                Pair(
                    it.categoryName ?: "Unassigned",
                    it.subcategoryName
                )
            }
            .map { (key, group) ->
                CategorySpendSummary(
                    categoryName = key.first,
                    subcategoryName = key.second,
                    amountCents = group.sumOf { it.amountCents },
                    transactionCount = group.size
                )
            }
            .sortedByDescending { it.amountCents }
    }

    val selectedCategoryTransactions = remember(
        selectedCategorySummary,
        includedExpensesForMonth
    ) {
        val selected = selectedCategorySummary

        if (selected == null) {
            emptyList()
        } else {
            includedExpensesForMonth
                .filter {
                    val category = it.categoryName ?: "Unassigned"
                    val subcategory = it.subcategoryName

                    category == selected.categoryName &&
                            subcategory == selected.subcategoryName
                }
                .sortedByDescending { it.occurredAtEpochMs }
        }
    }

    if (selectedCategorySummary != null) {
        CategoryDrilldownScreen(
            monthLabel = formatMonthYear(selectedMonthStart),
            summary = selectedCategorySummary!!,
            transactions = selectedCategoryTransactions,
            onBack = {
                selectedCategorySummary = null
            },
            onTransactionSelected = onTransactionSelected
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Spending Summary") },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("Back")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    MonthSelectorCard(
                        monthStartEpochMs = selectedMonthStart,
                        onPreviousMonth = {
                            selectedCategorySummary = null
                            selectedMonthStart = getPreviousMonthStartEpochMs(selectedMonthStart)
                        },
                        onNextMonth = {
                            selectedCategorySummary = null
                            selectedMonthStart = getNextMonthStartEpochMs(selectedMonthStart)
                        },
                        onCurrentMonth = {
                            selectedCategorySummary = null
                            selectedMonthStart = getCurrentMonthStartEpochMs()
                        }
                    )
                }

                item {
                    SpendingSummaryTopCard(
                        totalExpenseCents = totalExpenseCents,
                        includedExpenseCount = includedExpensesForMonth.size,
                        unassignedCount = unassignedCount
                    )
                }

                item {
                    Text(
                        text = "Spend by Category",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (categorySummaries.isEmpty()) {
                    item {
                        EmptySectionText(
                            "No expense transactions counted in this month yet. Mark transactions as Expense and assign categories."
                        )
                    }
                } else {
                    items(
                        items = categorySummaries,
                        key = { "${it.categoryName}|${it.subcategoryName ?: ""}" }
                    ) { summary ->
                        CategorySpendCard(
                            summary = summary,
                            totalAmountCents = totalExpenseCents,
                            onClick = {
                                selectedCategorySummary = summary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpendingSummaryTopCard(
    totalExpenseCents: Long,
    includedExpenseCount: Int,
    unassignedCount: Int
) {
    val total = totalExpenseCents / 100.0

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Spending",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$${"%.2f".format(total)}",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Transactions counted as spending: $includedExpenseCount")
            Text("Unassigned category transactions: $unassignedCount")

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "This view counts expenses only. Payments, transfers, income, and refunds are tracked separately.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun CategorySpendCard(
    summary: CategorySpendSummary,
    totalAmountCents: Long,
    onClick: () -> Unit
) {
    val amount = summary.amountCents / 100.0
    val share = if (totalAmountCents > 0) {
        summary.amountCents.toFloat() / totalAmountCents.toFloat()
    } else {
        0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = if (summary.subcategoryName.isNullOrBlank()) {
                    summary.categoryName
                } else {
                    "${summary.categoryName} / ${summary.subcategoryName}"
                },
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${"%.2f".format(amount)}",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Transactions: ${summary.transactionCount} - ${"%.0f".format(share * 100)}%",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Tap to view transactions",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun MonthSelectorCard(
    monthStartEpochMs: Long,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Selected Month",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formatMonthYear(monthStartEpochMs),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Previous")
                }

                OutlinedButton(
                    onClick = onCurrentMonth,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Current")
                }

                OutlinedButton(
                    onClick = onNextMonth,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Next")
                }
            }
        }
    }
}

fun getCurrentMonthStartEpochMs(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun getNextMonthStartEpochMs(monthStartEpochMs: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = monthStartEpochMs
    calendar.add(Calendar.MONTH, 1)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun getPreviousMonthStartEpochMs(monthStartEpochMs: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = monthStartEpochMs
    calendar.add(Calendar.MONTH, -1)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun formatMonthYear(monthStartEpochMs: Long): String {
    val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return formatter.format(Date(monthStartEpochMs))
}

fun treatmentLabel(treatment: String): String {
    return when (treatment) {
        TransactionTreatments.EXPENSE -> "Expense"
        TransactionTreatments.INCOME -> "Income"
        TransactionTreatments.REFUND -> "Refund"
        TransactionTreatments.CREDIT_CARD_PAYMENT -> "Credit card payment"
        TransactionTreatments.TRANSFER -> "Transfer"
        TransactionTreatments.PERSON_TO_PERSON -> "Person to person"
        else -> "Unknown"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDrilldownScreen(
    monthLabel: String,
    summary: CategorySpendSummary,
    transactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    val amount = summary.amountCents / 100.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Category Detail") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                CategoryDetailSummaryCard(
                    monthLabel = monthLabel,
                    summary = summary,
                    amountText = "$${"%.2f".format(amount)}"
                )
            }

            item {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (transactions.isEmpty()) {
                item {
                    EmptySectionText("No transactions found for this category.")
                }
            } else {
                items(
                    items = transactions,
                    key = { it.id }
                ) { transaction ->
                    CategoryTransactionCard(
                        transaction = transaction,
                        onClick = {
                            onTransactionSelected(transaction)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDetailSummaryCard(
    monthLabel: String,
    summary: CategorySpendSummary,
    amountText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = if (summary.subcategoryName.isNullOrBlank()) {
                    summary.categoryName
                } else {
                    "${summary.categoryName} / ${summary.subcategoryName}"
                },
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = monthLabel,
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = amountText,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Transactions: ${summary.transactionCount}")
        }
    }
}

@Composable
fun CategoryTransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    val amount = transaction.amountCents / 100.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = transaction.displayMerchantName
                    ?: transaction.merchantRaw
                    ?: transaction.sourceInstitution
                    ?: "Unknown merchant",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${"%.2f".format(amount)}",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = formatter.format(Date(transaction.occurredAtEpochMs)),
                style = MaterialTheme.typography.labelSmall
            )

            val categoryText = listOfNotNull(
                transaction.categoryName,
                transaction.subcategoryName
            ).joinToString(" / ")

            if (categoryText.isNotBlank()) {
                Text(
                    text = categoryText,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (transaction.reviewStatus == "NEEDS_REVIEW") {
                Text(
                    text = "Needs review",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tap for details",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

fun hasMissingMerchant(transaction: TransactionEntity): Boolean {
    return transaction.accountingTreatment in setOf(
        TransactionTreatments.EXPENSE,
        TransactionTreatments.REFUND,
        TransactionTreatments.PERSON_TO_PERSON,
        TransactionTreatments.CREDIT_CARD_PAYMENT,
        TransactionTreatments.INCOME
    ) &&
            transaction.displayMerchantName.isNullOrBlank() &&
            transaction.merchantRaw.isNullOrBlank()
}

fun hasMissingCategory(transaction: TransactionEntity): Boolean {
    return TransactionTreatments.countsAsSpending(
        treatment = transaction.accountingTreatment,
        excludedFromSpending = transaction.excludedFromSpending
    ) &&
            transaction.categoryName.isNullOrBlank()
}

fun hasLowConfidence(transaction: TransactionEntity): Boolean {
    return transaction.parseConfidence < 0.70
}

fun hasAnyReviewIssue(transaction: TransactionEntity): Boolean {
    return transaction.reviewStatus == "NEEDS_REVIEW" ||
            hasMissingMerchant(transaction) ||
            hasMissingCategory(transaction) ||
            hasLowConfidence(transaction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
    transactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    var selectedFilter by remember {
        mutableStateOf(ReviewQueueFilter.ALL_ISSUES)
    }

    val allIssueTransactions = remember(transactions) {
        transactions
            .filter { hasAnyReviewIssue(it) }
            .sortedByDescending { it.occurredAtEpochMs }
    }

    val filteredTransactions = remember(allIssueTransactions, selectedFilter) {
        when (selectedFilter) {
            ReviewQueueFilter.ALL_ISSUES -> allIssueTransactions

            ReviewQueueFilter.NEEDS_REVIEW -> allIssueTransactions.filter {
                it.reviewStatus == "NEEDS_REVIEW"
            }

            ReviewQueueFilter.MISSING_MERCHANT -> allIssueTransactions.filter {
                hasMissingMerchant(it)
            }

            ReviewQueueFilter.MISSING_CATEGORY -> allIssueTransactions.filter {
                hasMissingCategory(it)
            }

            ReviewQueueFilter.LOW_CONFIDENCE -> allIssueTransactions.filter {
                hasLowConfidence(it)
            }
        }
    }

    val needsReviewCount = transactions.count { it.reviewStatus == "NEEDS_REVIEW" }
    val missingMerchantCount = transactions.count { hasMissingMerchant(it) }
    val missingCategoryCount = transactions.count { hasMissingCategory(it) }
    val lowConfidenceCount = transactions.count { hasLowConfidence(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Queue") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                ReviewQueueSummaryCard(
                    totalIssueCount = allIssueTransactions.size,
                    visibleCount = filteredTransactions.size,
                    needsReviewCount = needsReviewCount,
                    missingMerchantCount = missingMerchantCount,
                    missingCategoryCount = missingCategoryCount,
                    lowConfidenceCount = lowConfidenceCount
                )
            }

            item {
                ReviewQueueFilterCard(
                    selectedFilter = selectedFilter,
                    allIssuesCount = allIssueTransactions.size,
                    needsReviewCount = needsReviewCount,
                    missingMerchantCount = missingMerchantCount,
                    missingCategoryCount = missingCategoryCount,
                    lowConfidenceCount = lowConfidenceCount,
                    onFilterSelected = { selectedFilter = it }
                )
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    EmptySectionText("No transactions found for this review filter.")
                }
            } else {
                items(
                    items = filteredTransactions,
                    key = { it.id }
                ) { transaction ->
                    ReviewTransactionCard(
                        transaction = transaction,
                        onClick = { onTransactionSelected(transaction) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewQueueSummaryCard(
    totalIssueCount: Int,
    visibleCount: Int,
    needsReviewCount: Int,
    missingMerchantCount: Int,
    missingCategoryCount: Int,
    lowConfidenceCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Review Summary",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Total issue transactions: $totalIssueCount")
            Text("Currently showing: $visibleCount")
            Text("Needs review: $needsReviewCount")
            Text("Missing merchant: $missingMerchantCount")
            Text("Missing category: $missingCategoryCount")
            Text("Low confidence: $lowConfidenceCount")
        }
    }
}

@Composable
fun ReviewQueueFilterCard(
    selectedFilter: ReviewQueueFilter,
    allIssuesCount: Int,
    needsReviewCount: Int,
    missingMerchantCount: Int,
    missingCategoryCount: Int,
    lowConfidenceCount: Int,
    onFilterSelected: (ReviewQueueFilter) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Review Filters",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewFilterButton(
                    label = "All ($allIssuesCount)",
                    selected = selectedFilter == ReviewQueueFilter.ALL_ISSUES,
                    onClick = { onFilterSelected(ReviewQueueFilter.ALL_ISSUES) },
                    modifier = Modifier.weight(1f)
                )

                ReviewFilterButton(
                    label = "Review ($needsReviewCount)",
                    selected = selectedFilter == ReviewQueueFilter.NEEDS_REVIEW,
                    onClick = { onFilterSelected(ReviewQueueFilter.NEEDS_REVIEW) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewFilterButton(
                    label = "Merchant ($missingMerchantCount)",
                    selected = selectedFilter == ReviewQueueFilter.MISSING_MERCHANT,
                    onClick = { onFilterSelected(ReviewQueueFilter.MISSING_MERCHANT) },
                    modifier = Modifier.weight(1f)
                )

                ReviewFilterButton(
                    label = "Category ($missingCategoryCount)",
                    selected = selectedFilter == ReviewQueueFilter.MISSING_CATEGORY,
                    onClick = { onFilterSelected(ReviewQueueFilter.MISSING_CATEGORY) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ReviewFilterButton(
                label = "Low Confidence ($lowConfidenceCount)",
                selected = selectedFilter == ReviewQueueFilter.LOW_CONFIDENCE,
                onClick = { onFilterSelected(ReviewQueueFilter.LOW_CONFIDENCE) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ReviewFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label)
        }
    }
}

@Composable
fun ReviewTransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    val amount = transaction.amountCents / 100.0

    val issues = buildList {
        if (transaction.reviewStatus == "NEEDS_REVIEW") add("Needs review")
        if (hasMissingMerchant(transaction)) add("Missing merchant")
        if (hasMissingCategory(transaction)) add("Missing category")
        if (hasLowConfidence(transaction)) add("Low confidence")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = transaction.displayMerchantName
                    ?: transaction.merchantRaw
                    ?: transaction.sourceInstitution
                    ?: "Unknown merchant",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${"%.2f".format(amount)} • ${treatmentLabel(transaction.accountingTreatment)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = formatter.format(Date(transaction.occurredAtEpochMs)),
                style = MaterialTheme.typography.labelSmall
            )

            if (issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Issues: ${issues.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            val categoryText = listOfNotNull(
                transaction.categoryName,
                transaction.subcategoryName
            ).joinToString(" / ")

            if (categoryText.isNotBlank()) {
                Text(
                    text = "Category: $categoryText",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tap to fix",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

fun normalizeRulePhrase(phrase: String): String {
    return phrase.trim().lowercase(Locale.US)
}

suspend fun parseIdentifiedSourceTransactions(database: AppDatabase): ParseRunResult {
    val rawAlertsOnce = database.rawAlertDao().getAllOnce()
    val identifiedSources = database
        .financialSourceDao()
        .getIdentifiedSourcesOnce()
    val merchantDefaultRules = database
        .transactionRuleDao()
        .getActiveRulesForSource(MERCHANT_DEFAULT_RULE_SOURCE_KEY)

    var parsedCount = 0
    var skippedCount = 0
    var ignoredNonTransactionCount = 0
    var failedCount = 0
    var matchedAlertCount = 0

    identifiedSources.forEach { source ->
        val sourceRules = database
            .transactionRuleDao()
            .getActiveRulesForSource(source.sourceKey)

        val matchingAlerts = rawAlertsOnce.filter { rawAlert ->
            SourceDetector.matchesSource(rawAlert, source)
        }

        matchedAlertCount += matchingAlerts.size

        matchingAlerts.forEach { rawAlert ->
            if (database.transactionDao().countByRawAlertId(rawAlert.id) > 0) {
                skippedCount++
                return@forEach
            }

            val parsedTransaction = SmsTransactionParser.parse(
                rawAlert = rawAlert,
                source = source,
                sourceMessages = matchingAlerts.map { it.combinedText }
            )

            if (parsedTransaction == null) {
                val status = if (SmsTransactionParser.isNonTransactionAlert(rawAlert)) {
                    "IGNORED_NON_TRANSACTION"
                } else {
                    "FAILED_TRANSACTION_PARSE"
                }

                database.rawAlertDao().updateProcessingStatus(
                    rawAlert.id,
                    status
                )

                if (status == "FAILED_TRANSACTION_PARSE") {
                    failedCount++
                } else {
                    ignoredNonTransactionCount++
                }
            } else {
                val ruleAdjustedTransaction = applyRulesToTransaction(
                    transaction = parsedTransaction,
                    rawAlert = rawAlert,
                    sourceRules = sourceRules,
                    merchantDefaultRules = merchantDefaultRules
                )

                database.transactionDao().insert(ruleAdjustedTransaction)

                database.rawAlertDao().updateProcessingStatus(
                    rawAlert.id,
                    "PARSED_TRANSACTION"
                )

                parsedCount++
            }
        }
    }

    return ParseRunResult(
        matchedAlertCount = matchedAlertCount,
        parsedCount = parsedCount,
        skippedCount = skippedCount,
        ignoredNonTransactionCount = ignoredNonTransactionCount,
        failedCount = failedCount
    )
}

suspend fun detectAndSaveSources(
    database: AppDatabase,
    rawAlerts: List<RawAlertEntity>
): Int {
    database.financialSourceDao().deleteLegacyNonSenderSources()

    val detectedSources = SourceDetector.detect(rawAlerts)

    detectedSources.forEach { detected ->
        val existing = database
            .financialSourceDao()
            .getBySourceKey(detected.sourceKey)

        val sourceToSave = if (existing == null) {
            detected
        } else {
            detected.copy(
                confirmedAccountType = existing.confirmedAccountType,
                displayName = existing.displayName ?: detected.displayName,
                userConfirmed = existing.userConfirmed,
                ignored = existing.ignored,
                createdAtEpochMs = existing.createdAtEpochMs,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        }

        database.financialSourceDao().upsert(sourceToSave)
    }

    return detectedSources.size
}

suspend fun reapplySavedRulesToExistingTransactions(database: AppDatabase): Int {
    val rawAlertsById = database.rawAlertDao()
        .getAllOnce()
        .associateBy { it.id }

    val merchantDefaultRules = database
        .transactionRuleDao()
        .getActiveRulesForSource(MERCHANT_DEFAULT_RULE_SOURCE_KEY)

    val sourceRuleCache = mutableMapOf<String, List<TransactionRuleEntity>>()
    var updatedCount = 0

    database.transactionDao().getAllOnce().forEach { transaction ->
        val rawAlert = rawAlertsById[transaction.rawAlertId] ?: return@forEach
        val sourceRules = sourceRuleCache.getOrPut(transaction.sourceKey) {
            database.transactionRuleDao().getActiveRulesForSource(transaction.sourceKey)
        }

        val updatedTransaction = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = sourceRules,
            merchantDefaultRules = merchantDefaultRules
        )

        if (updatedTransaction != transaction) {
            database.transactionDao().update(updatedTransaction)
            updatedCount++
        }
    }

    return updatedCount
}

suspend fun updateRawAlertStatusesForSource(
    database: AppDatabase,
    source: FinancialSourceEntity,
    status: String
) {
    database.rawAlertDao()
        .getAllOnce()
        .filter { rawAlert -> SourceDetector.matchesSource(rawAlert, source) }
        .forEach { rawAlert ->
            database.rawAlertDao().updateProcessingStatus(
                rawAlertId = rawAlert.id,
                status = status
            )
        }
}

fun buildTransactionsCsv(transactions: List<TransactionEntity>): String {
    val header = listOf(
        "id",
        "occurredAt",
        "amount",
        "currency",
        "type",
        "accountingTreatment",
        "merchant",
        "category",
        "subcategory",
        "source",
        "accountHint",
        "reviewStatus",
        "excludedFromSpending",
        "parseConfidence",
        "notes"
    )

    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val rows = transactions.map { transaction ->
        listOf(
            transaction.id.toString(),
            formatter.format(Date(transaction.occurredAtEpochMs)),
            "%.2f".format(Locale.US, transaction.amountCents / 100.0),
            transaction.currency,
            transaction.transactionType,
            transaction.accountingTreatment,
            transaction.displayMerchantName ?: transaction.merchantRaw ?: "",
            transaction.categoryName ?: "",
            transaction.subcategoryName ?: "",
            transaction.sourceInstitution ?: transaction.sourceKey,
            transaction.accountHint ?: "",
            transaction.reviewStatus,
            transaction.excludedFromSpending.toString(),
            "%.2f".format(Locale.US, transaction.parseConfidence),
            transaction.parserNotes ?: ""
        )
    }

    return (listOf(header) + rows)
        .joinToString(separator = "\n") { row ->
            row.joinToString(separator = ",") { csvEscape(it) }
        } + "\n"
}

fun buildParserCorpusJsonl(
    rawAlerts: List<RawAlertEntity>,
    transactionsByRawAlertId: Map<Long, TransactionEntity>
): String {
    return rawAlerts
        .sortedBy { it.postTimeEpochMs }
        .joinToString(separator = "\n", postfix = "\n") { alert ->
            val transaction = transactionsByRawAlertId[alert.id]
            buildString {
                append("{")
                appendJsonField("rawAlertId", alert.id)
                append(",")
                appendJsonField("notificationKey", alert.notificationKey)
                append(",")
                appendJsonField("sourcePackage", alert.sourcePackage)
                append(",")
                appendJsonField("sender", alert.title.orEmpty().removePrefix("SMS from ").trim())
                append(",")
                appendJsonField("timestampEpochMs", alert.postTimeEpochMs)
                append(",")
                appendJsonField("processingStatus", alert.processingStatus)
                append(",")
                appendJsonField("rawSmsText", alert.combinedText)
                append(",")
                append("\"parsed\":")
                if (transaction == null) {
                    append("null")
                } else {
                    append("{")
                    appendJsonField("transactionId", transaction.id)
                    append(",")
                    appendJsonField("sourceKey", transaction.sourceKey)
                    append(",")
                    appendJsonField("amountCents", transaction.amountCents)
                    append(",")
                    appendJsonField("currency", transaction.currency)
                    append(",")
                    appendJsonField("transactionType", transaction.transactionType)
                    append(",")
                    appendJsonField("accountingTreatment", transaction.accountingTreatment)
                    append(",")
                    appendJsonField("merchantRaw", transaction.merchantRaw)
                    append(",")
                    appendJsonField("displayMerchantName", transaction.displayMerchantName)
                    append(",")
                    appendJsonField("categoryName", transaction.categoryName)
                    append(",")
                    appendJsonField("subcategoryName", transaction.subcategoryName)
                    append(",")
                    appendJsonField("reviewStatus", transaction.reviewStatus)
                    append(",")
                    appendJsonField("parseConfidence", transaction.parseConfidence)
                    append(",")
                    appendJsonField("parserNotes", transaction.parserNotes)
                    append(",")
                    appendJsonField("merchantUserEdited", transaction.merchantUserEdited)
                    append(",")
                    appendJsonField("categoryUserEdited", transaction.categoryUserEdited)
                    append(",")
                    appendJsonField("treatmentUserEdited", transaction.treatmentUserEdited)
                    append("}")
                }
                append("}")
            }
        }
}

private fun StringBuilder.appendJsonField(name: String, value: String?) {
    append("\"")
    append(jsonEscape(name))
    append("\":")
    if (value == null) {
        append("null")
    } else {
        append("\"")
        append(jsonEscape(value))
        append("\"")
    }
}

private fun StringBuilder.appendJsonField(name: String, value: Long) {
    append("\"")
    append(jsonEscape(name))
    append("\":")
    append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Double) {
    append("\"")
    append(jsonEscape(name))
    append("\":")
    append("%.4f".format(Locale.US, value))
}

private fun StringBuilder.appendJsonField(name: String, value: Boolean) {
    append("\"")
    append(jsonEscape(name))
    append("\":")
    append(value)
}

private fun jsonEscape(value: String): String {
    return buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}

private fun csvEscape(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"$escaped\""
    } else {
        escaped
    }
}

fun applyRulesToTransaction(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity,
    sourceRules: List<TransactionRuleEntity>,
    merchantDefaultRules: List<TransactionRuleEntity> = emptyList()
): TransactionEntity {
    var updated = transaction
    val appliedRulePhrases = mutableListOf<String>()

    sourceRules.forEach { rule ->
        if (!rule.active) return@forEach
        if (!rule.matchesTreatment(updated.accountingTreatment)) return@forEach

        val phrase = rule.matchPhrase.trim()
        if (phrase.isBlank()) return@forEach

        val matches = rawAlert.combinedText.contains(
            other = phrase,
            ignoreCase = true
        )

        if (!matches) return@forEach

        val ruleTreatment = rule.transactionType ?: updated.accountingTreatment
        val shouldApplyCategory = rule.shouldApplyCategoryTo(updated.accountingTreatment)

        updated = updated.copy(
            merchantRaw = if (!updated.merchantUserEdited) rule.merchantName ?: updated.merchantRaw else updated.merchantRaw,
            displayMerchantName = if (!updated.merchantUserEdited) rule.merchantName ?: updated.displayMerchantName else updated.displayMerchantName,
            categoryName = if (!updated.categoryUserEdited && shouldApplyCategory) rule.categoryName ?: updated.categoryName else updated.categoryName,
            subcategoryName = if (!updated.categoryUserEdited && shouldApplyCategory) rule.subcategoryName ?: updated.subcategoryName else updated.subcategoryName,
            transactionType = if (!updated.treatmentUserEdited) ruleTreatment else updated.transactionType,
            accountingTreatment = if (!updated.treatmentUserEdited) ruleTreatment else updated.accountingTreatment,
            reviewStatus = when {
                rule.requiresReview -> "NEEDS_REVIEW"
                rule.reviewStatus != null -> rule.reviewStatus
                else -> updated.reviewStatus
            },
            excludedFromSpending = if (!updated.treatmentUserEdited) {
                rule.excludedFromSpending ?: TransactionTreatments.defaultExcludedFromSpending(ruleTreatment)
            } else {
                updated.excludedFromSpending
            },
            updatedAtEpochMs = System.currentTimeMillis()
        )

        appliedRulePhrases.add(rule.matchPhrase)
    }

    val merchantName = (updated.displayMerchantName ?: updated.merchantRaw)
        ?.trim()

    if (
        !merchantName.isNullOrBlank()
    ) {
        val normalizedMerchant = normalizeRulePhrase(merchantName)
        val merchantRule = merchantDefaultRules.firstOrNull { rule ->
            rule.active &&
                    rule.matchesTreatment(updated.accountingTreatment) &&
                    (
                            rule.normalizedMatchPhrase == normalizedMerchant ||
                                    normalizeRulePhrase(rule.merchantName ?: rule.matchPhrase) == normalizedMerchant
                            )
        }

        if (merchantRule != null) {
            val merchantTreatment = merchantRule.transactionType ?: updated.accountingTreatment
            val shouldApplyCategory = merchantRule.shouldApplyCategoryTo(updated.accountingTreatment)
            val categoryName = if (!updated.categoryUserEdited && shouldApplyCategory) {
                merchantRule.categoryName ?: updated.categoryName
            } else {
                updated.categoryName
            }
            val subcategoryName = if (!updated.categoryUserEdited && shouldApplyCategory) {
                merchantRule.subcategoryName ?: updated.subcategoryName
            } else {
                updated.subcategoryName
            }

            updated = updated.copy(
                merchantRaw = if (!updated.merchantUserEdited) merchantRule.merchantName ?: updated.merchantRaw else updated.merchantRaw,
                displayMerchantName = if (!updated.merchantUserEdited) merchantRule.merchantName ?: updated.displayMerchantName else updated.displayMerchantName,
                categoryName = categoryName,
                subcategoryName = subcategoryName,
                transactionType = if (!updated.treatmentUserEdited) merchantTreatment else updated.transactionType,
                accountingTreatment = if (!updated.treatmentUserEdited) merchantTreatment else updated.accountingTreatment,
                excludedFromSpending = if (!updated.treatmentUserEdited) {
                    merchantRule.excludedFromSpending
                        ?: TransactionTreatments.defaultExcludedFromSpending(merchantTreatment)
                } else {
                    updated.excludedFromSpending
                },
                reviewStatus = when {
                    merchantRule.requiresReview -> "NEEDS_REVIEW"
                    !categoryName.isNullOrBlank() && updated.reviewStatus == "NEEDS_REVIEW" -> "AUTO_PARSED"
                    else -> updated.reviewStatus
                },
                updatedAtEpochMs = System.currentTimeMillis()
            )

            appliedRulePhrases.add("merchant default: ${merchantRule.matchPhrase}")
        }
    }

    if (appliedRulePhrases.isEmpty()) {
        return updated
    }

    val existingNotes = updated.parserNotes.orEmpty()
    val ruleNote = "Applied saved rule(s): ${appliedRulePhrases.joinToString(", ")}."

    return updated.copy(
        parserNotes = listOf(existingNotes, ruleNote)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    )
}

private fun TransactionRuleEntity.matchesTreatment(treatment: String): Boolean {
    return appliesToTreatment.isNullOrBlank() ||
            appliesToTreatment == treatment ||
            transactionType != null
}

private fun TransactionRuleEntity.shouldApplyCategoryTo(treatment: String): Boolean {
    if (!applyCategoryAutomatically || requiresReview) return false
    if (categoryName.isNullOrBlank()) return false
    return !appliesToTreatment.isNullOrBlank() ||
            treatment == TransactionTreatments.EXPENSE ||
            transactionType == treatment
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rawAlertCount: Int,
    sourceCount: Int,
    transactionCount: Int,
    activeRuleCount: Int,
    uncategorizedSourceCount: Int,
    identifiedSourceCount: Int,
    reviewIssueCount: Int,
    currentMonthSpendingCents: Long,
    currentMonthIncomeCents: Long,
    currentMonthRefundCents: Long,
    currentMonthMovementCents: Long,
    previousMonthSpendingCents: Long,
    currentMonthExpenseCount: Int,
    topCategoryLabel: String,
    topMerchantLabel: String,
    onOpenSetup: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenReviewQueue: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenMerchants: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenTools: () -> Unit
){
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LedgerLens") }
            )
        },
        bottomBar = {
            HomeBottomNav(
                onOpenHome = {},
                onOpenSummary = onOpenSummary,
                onOpenReviewQueue = onOpenReviewQueue,
                onOpenMerchants = onOpenMerchants,
                onOpenTransactions = onOpenTransactions
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                HomeStatusCard(
                    rawAlertCount = rawAlertCount,
                    sourceCount = sourceCount,
                    transactionCount = transactionCount,
                    activeRuleCount = activeRuleCount,
                    uncategorizedSourceCount = uncategorizedSourceCount,
                    identifiedSourceCount = identifiedSourceCount,
                    reviewIssueCount = reviewIssueCount,
                    currentMonthSpendingCents = currentMonthSpendingCents,
                    currentMonthIncomeCents = currentMonthIncomeCents,
                    currentMonthRefundCents = currentMonthRefundCents,
                    currentMonthMovementCents = currentMonthMovementCents,
                    previousMonthSpendingCents = previousMonthSpendingCents,
                    currentMonthExpenseCount = currentMonthExpenseCount,
                    topCategoryLabel = topCategoryLabel,
                    topMerchantLabel = topMerchantLabel
                )
            }

            item {
                NextActionCard(
                    rawAlertCount = rawAlertCount,
                    identifiedSourceCount = identifiedSourceCount,
                    uncategorizedSourceCount = uncategorizedSourceCount,
                    transactionCount = transactionCount,
                    reviewIssueCount = reviewIssueCount,
                    onOpenSetup = onOpenSetup,
                    onOpenReviewQueue = onOpenReviewQueue,
                    onOpenSummary = onOpenSummary
                )
            }

            item {
                HomeNavCard(
                    title = "Spending",
                    description = "Monthly totals, categories, and transaction drilldowns.",
                    buttonText = "View Spending",
                    onClick = onOpenSummary
                )
            }

            item {
                HomeNavCard(
                    title = "Needs Review",
                    description = "Only the transactions that need a decision.",
                    buttonText = "Review Items",
                    onClick = onOpenReviewQueue
                )
            }

            item {
                HomeNavCard(
                    title = "Merchants",
                    description = "Set default categories once and let future transactions follow them.",
                    buttonText = "Review Merchants",
                    onClick = onOpenMerchants
                )
            }

            item {
                HomeNavCard(
                    title = "All Transactions",
                    description = "Search and inspect the full transaction history.",
                    buttonText = "Browse",
                    onClick = onOpenTransactions
                )
            }

            item {
                HomeNavCard(
                    title = "Settings",
                    description = "Manage sources, rules, imports, exports, and maintenance.",
                    buttonText = "Open Settings",
                    onClick = onOpenTools
                )
            }
        }
    }
}

@Composable
fun HomeBottomNav(
    onOpenHome: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenReviewQueue: () -> Unit,
    onOpenMerchants: () -> Unit,
    onOpenTransactions: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = onOpenHome,
            icon = { Text("H") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenSummary,
            icon = { Text("S") },
            label = { Text("Spend") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenReviewQueue,
            icon = { Text("!") },
            label = { Text("Review") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenMerchants,
            icon = { Text("M") },
            label = { Text("Merchants") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenTransactions,
            icon = { Text("T") },
            label = { Text("Activity") }
        )
    }
}

@Composable
fun HomeStatusCard(
    rawAlertCount: Int,
    sourceCount: Int,
    transactionCount: Int,
    activeRuleCount: Int,
    uncategorizedSourceCount: Int,
    identifiedSourceCount: Int,
    reviewIssueCount: Int,
    currentMonthSpendingCents: Long,
    currentMonthIncomeCents: Long,
    currentMonthRefundCents: Long,
    currentMonthMovementCents: Long,
    previousMonthSpendingCents: Long,
    currentMonthExpenseCount: Int,
    topCategoryLabel: String,
    topMerchantLabel: String
) {
    val currentMonthSpending = currentMonthSpendingCents / 100.0
    val currentMonthIncome = currentMonthIncomeCents / 100.0
    val currentMonthRefunds = currentMonthRefundCents / 100.0
    val currentMonthMovements = currentMonthMovementCents / 100.0
    val previousMonthSpending = previousMonthSpendingCents / 100.0
    val delta = currentMonthSpending - previousMonthSpending

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "$${"%.2f".format(currentMonthSpending)}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "${formatMonthYear(getCurrentMonthStartEpochMs())} spending across $currentMonthExpenseCount transactions",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Previous month: $${"%.2f".format(previousMonthSpending)}")
            Text("Month change: ${if (delta >= 0) "+" else ""}$${"%.2f".format(delta)}")
            Text("Income: $${"%.2f".format(currentMonthIncome)}")
            Text("Refunds: $${"%.2f".format(currentMonthRefunds)}")
            Text("Payments/transfers: $${"%.2f".format(currentMonthMovements)}")
            Text("Top category: $topCategoryLabel")
            Text("Top merchant: $topMerchantLabel")

            Spacer(modifier = Modifier.height(8.dp))

            Text("Imported SMS: $rawAlertCount")
            Text("Sources: $identifiedSourceCount active ($uncategorizedSourceCount to review)")
            Text("Transactions: $transactionCount ($reviewIssueCount need attention)")
            Text("Saved rules: $activeRuleCount")
        }
    }
}

@Composable
fun NextActionCard(
    rawAlertCount: Int,
    identifiedSourceCount: Int,
    uncategorizedSourceCount: Int,
    transactionCount: Int,
    reviewIssueCount: Int,
    onOpenSetup: () -> Unit,
    onOpenReviewQueue: () -> Unit,
    onOpenSummary: () -> Unit
) {
    val title: String
    val description: String
    val buttonText: String
    val action: () -> Unit

    when {
        rawAlertCount == 0 -> {
            title = "Start with SMS import"
            description = "Import historical SMS alerts, then detect sender-level financial sources."
            buttonText = "Start Setup"
            action = onOpenSetup
        }

        identifiedSourceCount == 0 -> {
            title = "Choose financial sources"
            description = "Confirm at least one bank or card SMS sender so LedgerLens can parse transactions."
            buttonText = "Continue Setup"
            action = onOpenSetup
        }

        uncategorizedSourceCount > 0 -> {
            title = "Confirm financial sources"
            description = "$uncategorizedSourceCount sender sources need a decision before they can be parsed."
            buttonText = "Continue Setup"
            action = onOpenSetup
        }

        transactionCount == 0 -> {
            title = "Parse transactions"
            description = "Your sources are ready. Parse their SMS alerts to build the spending dashboard."
            buttonText = "Continue Setup"
            action = onOpenSetup
        }

        reviewIssueCount > 0 -> {
            title = "Clean up transactions"
            description = "$reviewIssueCount transactions need a merchant, category, or review decision."
            buttonText = "Open Review Queue"
            action = onOpenReviewQueue
        }

        else -> {
            title = "Dashboard is ready"
            description = "Your sources, rules, and spending summary are in good shape."
            buttonText = "View Summary"
            action = onOpenSummary
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = action,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun HomeNavCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    rules: List<TransactionRuleEntity>,
    onBack: () -> Unit,
    onDisableRule: (TransactionRuleEntity) -> Unit,
    onDeleteRule: (TransactionRuleEntity) -> Unit
) {
    val merchantRules = rules.filter { it.sourceKey == MERCHANT_DEFAULT_RULE_SOURCE_KEY }
    val phraseRules = rules.filter { it.sourceKey != MERCHANT_DEFAULT_RULE_SOURCE_KEY }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rules") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Automation Rules",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Merchant defaults: ${merchantRules.size}")
                        Text("Phrase/source rules: ${phraseRules.size}")
                    }
                }
            }

            item {
                SourceSectionHeader(
                    title = "Merchant Defaults",
                    count = merchantRules.size
                )
            }

            if (merchantRules.isEmpty()) {
                item {
                    EmptySectionText("No merchant defaults yet. Assign categories in Merchant Review.")
                }
            } else {
                items(
                    items = merchantRules,
                    key = { it.id }
                ) { rule ->
                    RuleCard(
                        rule = rule,
                        onDisableRule = onDisableRule,
                        onDeleteRule = onDeleteRule
                    )
                }
            }

            item {
                SourceSectionHeader(
                    title = "Phrase Rules",
                    count = phraseRules.size
                )
            }

            if (phraseRules.isEmpty()) {
                item {
                    EmptySectionText("No phrase rules yet. Use Transaction Detail to apply fixes to similar SMS.")
                }
            } else {
                items(
                    items = phraseRules,
                    key = { it.id }
                ) { rule ->
                    RuleCard(
                        rule = rule,
                        onDisableRule = onDisableRule,
                        onDeleteRule = onDeleteRule
                    )
                }
            }
        }
    }
}

@Composable
fun RuleCard(
    rule: TransactionRuleEntity,
    onDisableRule: (TransactionRuleEntity) -> Unit,
    onDeleteRule: (TransactionRuleEntity) -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = rule.merchantName ?: rule.matchPhrase,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (rule.sourceKey == MERCHANT_DEFAULT_RULE_SOURCE_KEY) {
                    "Merchant default"
                } else {
                    "Source phrase: ${rule.sourceKey}"
                },
                style = MaterialTheme.typography.labelMedium
            )

            val details = listOfNotNull(
                rule.categoryName?.let {
                    if (rule.subcategoryName.isNullOrBlank()) it else "$it / ${rule.subcategoryName}"
                },
                rule.transactionType?.let { treatmentLabel(it) },
                rule.appliesToTreatment?.let { "Scope: ${treatmentLabel(it)}" },
                rule.reviewStatus,
                rule.excludedFromSpending?.let { if (it) "Outside spending" else "Spending" },
                if (!rule.applyCategoryAutomatically) "No auto category" else null,
                if (rule.requiresReview) "Requires review" else null
            )

            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" - "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Match phrase: ${rule.matchPhrase}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Updated: ${formatter.format(Date(rule.updatedAtEpochMs))}",
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onDisableRule(rule) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disable")
                }

                OutlinedButton(
                    onClick = { onDeleteRule(rule) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    rawAlertCount: Int,
    sourceCount: Int,
    transactionCount: Int,
    activeRuleCount: Int,
    statusText: String,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenRules: () -> Unit,
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit,
    onDetectSources: () -> Unit,
    onParseIdentifiedSources: () -> Unit,
    onReparseTransactions: () -> Unit,
    onReapplySavedRules: () -> Unit,
    onExportTransactions: () -> Unit,
    onExportParserCorpus: () -> Unit,
    onClearAll: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Tool Status",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(statusText)
                        Text("Imported SMS: $rawAlertCount")
                        Text("Sources: $sourceCount")
                        Text("Transactions: $transactionCount")
                        Text("Saved rules: $activeRuleCount")
                    }
                }
            }

            item {
                ToolActionCard(
                    title = "Source Setup",
                    description = "Manage SMS senders, source types, and ignored sources.",
                    buttonText = "Manage Sources",
                    onClick = onOpenSources
                )
            }

            item {
                ToolActionCard(
                    title = "Rules",
                    description = "Review saved merchant defaults and phrase rules.",
                    buttonText = "Manage Rules",
                    onClick = onOpenRules
                )
            }

            item {
                ToolActionCard(
                    title = "Backfill SMS History",
                    description = "Import financial-looking SMS messages from the last several years.",
                    buttonText = "Backfill SMS",
                    onClick = onBackfillSmsHistory
                )
            }

            item {
                ToolActionCard(
                    title = "Refresh Latest SMS",
                    description = "Import newer financial-looking SMS messages. Existing SMS ids are skipped.",
                    buttonText = "Refresh SMS",
                    onClick = onRefreshLatestSms
                )
            }

            item {
                ToolActionCard(
                    title = "Detect Sources",
                    description = "Group imported SMS messages by sender and detect possible financial sources.",
                    buttonText = "Detect Sources",
                    onClick = onDetectSources
                )
            }

            item {
                ToolActionCard(
                    title = "Parse Identified Sources",
                    description = "Parse transactions only from sources you marked as valid.",
                    buttonText = "Parse Sources",
                    onClick = onParseIdentifiedSources
                )
            }

            item {
                ToolActionCard(
                    title = "Reparse Transactions",
                    description = "Clear parsed transactions and rebuild them from identified SMS using current parser and saved rules.",
                    buttonText = "Reparse Transactions",
                    onClick = onReparseTransactions
                )
            }

            item {
                ToolActionCard(
                    title = "Reapply Saved Rules",
                    description = "Apply saved merchant and phrase rules to transactions that already exist.",
                    buttonText = "Reapply Rules",
                    onClick = onReapplySavedRules
                )
            }

            item {
                ToolActionCard(
                    title = "Export Transactions",
                    description = "Create a CSV file and open Android sharing so you can save or send your transaction data.",
                    buttonText = "Export CSV",
                    onClick = onExportTransactions
                )
            }

            item {
                ToolActionCard(
                    title = "Export Parser Corpus",
                    description = "Create a JSONL debugging corpus with raw SMS, parser output, and user corrections for parser tuning.",
                    buttonText = "Export JSONL",
                    onClick = onExportParserCorpus
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Danger Zone",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Clears imported SMS, sources, rules, and transactions from the local test database.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onClearAll,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear All Test Data")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolActionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantReviewScreen(
    transactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onMerchantSelected: (MerchantSummary) -> Unit
) {
    var searchText by remember {
        mutableStateOf("")
    }

    val merchantSummaries = remember(transactions) {
        transactions
            .filter {
                !it.displayMerchantName.isNullOrBlank() ||
                        !it.merchantRaw.isNullOrBlank()
            }
            .groupBy {
                (it.displayMerchantName ?: it.merchantRaw ?: "Unknown merchant").trim()
            }
            .map { (merchantName, group) ->
                val expenseGroup = group.filter {
                    TransactionTreatments.countsAsSpending(
                        treatment = it.accountingTreatment,
                        excludedFromSpending = it.excludedFromSpending
                    )
                }

                val primaryTreatment = group
                    .groupingBy { it.accountingTreatment }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: TransactionTreatments.UNKNOWN

                val categoryPairs = group
                    .mapNotNull { transaction ->
                        val category = transaction.categoryName
                        if (category.isNullOrBlank()) {
                            null
                        } else {
                            category to transaction.subcategoryName
                        }
                    }

                val mostCommonCategory = categoryPairs
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key

                MerchantSummary(
                    merchantName = merchantName,
                    transactionCount = group.size,
                    totalAmountCents = expenseGroup.sumOf { it.amountCents },
                    primaryTreatment = primaryTreatment,
                    categoryName = mostCommonCategory?.first,
                    subcategoryName = mostCommonCategory?.second,
                    uncategorizedCount = group.count {
                        TransactionTreatments.countsAsSpending(
                            treatment = it.accountingTreatment,
                            excludedFromSpending = it.excludedFromSpending
                        ) &&
                                it.categoryName.isNullOrBlank()
                    },
                    latestTransactionEpochMs = group.maxOf { it.occurredAtEpochMs }
                )
            }
            .sortedWith(
                compareByDescending<MerchantSummary> { it.uncategorizedCount }
                    .thenByDescending { it.totalAmountCents }
                    .thenByDescending { it.transactionCount }
                    .thenBy { it.merchantName.lowercase(Locale.US) }
            )
    }

    val visibleMerchants = remember(merchantSummaries, searchText) {
        val query = searchText.trim().lowercase(Locale.US)
        if (query.isBlank()) {
            merchantSummaries
        } else {
            merchantSummaries.filter { merchant ->
                listOfNotNull(
                    merchant.merchantName,
                    merchant.primaryTreatment,
                    merchant.categoryName,
                    merchant.subcategoryName
                ).any { it.lowercase(Locale.US).contains(query) }
            }
        }
    }

    val uncategorizedMerchants = visibleMerchants.filter { it.uncategorizedCount > 0 }
    val categorizedMerchants = visibleMerchants.filter { it.uncategorizedCount == 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merchant Review") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                MerchantReviewSummaryCard(
                    totalMerchants = merchantSummaries.size,
                    uncategorizedMerchants = uncategorizedMerchants.size,
                    categorizedMerchants = categorizedMerchants.size
                )
            }

            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search merchants") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                SourceSectionHeader(
                    title = "Needs Merchant Category",
                    count = uncategorizedMerchants.size
                )
            }

            if (uncategorizedMerchants.isEmpty()) {
                item {
                    EmptySectionText("No merchants need category assignment.")
                }
            } else {
                items(
                    items = uncategorizedMerchants,
                    key = { it.merchantName }
                ) { merchant ->
                    MerchantSummaryCard(
                        merchant = merchant,
                        onClick = { onMerchantSelected(merchant) }
                    )
                }
            }

            item {
                SourceSectionHeader(
                    title = "Categorized Merchants",
                    count = categorizedMerchants.size
                )
            }

            if (categorizedMerchants.isEmpty()) {
                item {
                    EmptySectionText("No categorized merchants yet.")
                }
            } else {
                items(
                    items = categorizedMerchants,
                    key = { it.merchantName }
                ) { merchant ->
                    MerchantSummaryCard(
                        merchant = merchant,
                        onClick = { onMerchantSelected(merchant) }
                    )
                }
            }
        }
    }
}

@Composable
fun MerchantReviewSummaryCard(
    totalMerchants: Int,
    uncategorizedMerchants: Int,
    categorizedMerchants: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Merchant Summary",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Total merchants/payees: $totalMerchants")
            Text("Need category: $uncategorizedMerchants")
            Text("Categorized: $categorizedMerchants")

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "This groups parsed transactions by merchant/payee. Category assignments are saved as merchant defaults for future parses.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun MerchantSummaryCard(
    merchant: MerchantSummary,
    onClick: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

    val amount = merchant.totalAmountCents / 100.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = merchant.merchantName,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Transactions: ${merchant.transactionCount} • Uncategorized expenses: ${merchant.uncategorizedCount}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Included expense total: $${"%.2f".format(amount)}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Usual treatment: ${treatmentLabel(merchant.primaryTreatment)}",
                style = MaterialTheme.typography.labelSmall
            )

            val categoryText = if (merchant.categoryName.isNullOrBlank()) {
                "No default category"
            } else {
                merchant.categoryName +
                        if (!merchant.subcategoryName.isNullOrBlank()) {
                            " / ${merchant.subcategoryName}"
                        } else {
                            ""
                        }
            }

            Text(
                text = categoryText,
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Latest: ${formatter.format(Date(merchant.latestTransactionEpochMs))}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Tap to review merchant",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantDetailScreen(
    merchant: MerchantSummary,
    transactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onUpdateMerchantCategory: (String, String, String, Boolean, Boolean) -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    val expenseTotal = transactions
        .filter {
            TransactionTreatments.countsAsSpending(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending
            )
        }
        .sumOf { it.amountCents }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merchant Detail") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                MerchantDetailSummaryCard(
                    merchant = merchant,
                    transactionCount = transactions.size,
                    expenseTotalCents = expenseTotal
                )
            }

            item {
                MerchantCategoryAssignmentCard(
                    merchant = merchant,
                    onUpdateMerchantCategory = onUpdateMerchantCategory
                )
            }

            item {
                Text(
                    text = "Merchant Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (transactions.isEmpty()) {
                item {
                    EmptySectionText("No transactions found for this merchant.")
                }
            } else {
                items(
                    items = transactions,
                    key = { it.id }
                ) { transaction ->
                    MerchantTransactionCard(
                        transaction = transaction,
                        onClick = { onTransactionSelected(transaction) }
                    )
                }
            }
        }
    }
}

@Composable
fun MerchantDetailSummaryCard(
    merchant: MerchantSummary,
    transactionCount: Int,
    expenseTotalCents: Long
) {
    val amount = expenseTotalCents / 100.0

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = merchant.merchantName,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Transactions: $transactionCount")
            Text("Included expense total: $${"%.2f".format(amount)}")
            Text("Usual treatment: ${treatmentLabel(merchant.primaryTreatment)}")
            Text("Uncategorized expenses: ${merchant.uncategorizedCount}")

            val categoryText = if (merchant.categoryName.isNullOrBlank()) {
                "No category assigned"
            } else {
                merchant.categoryName +
                        if (!merchant.subcategoryName.isNullOrBlank()) {
                            " / ${merchant.subcategoryName}"
                        } else {
                            ""
                        }
            }

            Text("Current merchant category: $categoryText")
        }
    }
}

@Composable
fun MerchantCategoryAssignmentCard(
    merchant: MerchantSummary,
    onUpdateMerchantCategory: (String, String, String, Boolean, Boolean) -> Unit
) {
    var categoryText by remember(merchant.merchantName, merchant.categoryName) {
        mutableStateOf(merchant.categoryName ?: "")
    }

    var subcategoryText by remember(merchant.merchantName, merchant.subcategoryName) {
        mutableStateOf(merchant.subcategoryName ?: "")
    }

    var treatmentText by remember(merchant.merchantName, merchant.primaryTreatment) {
        mutableStateOf(merchant.primaryTreatment)
    }

    var applyCategoryAutomatically by remember(merchant.merchantName) {
        mutableStateOf(true)
    }

    var requiresReview by remember(merchant.merchantName) {
        mutableStateOf(merchant.primaryTreatment == TransactionTreatments.PERSON_TO_PERSON)
    }

    val presets = listOf(
        "Groceries" to "General",
        "Restaurants" to "Dining Out",
        "Gas" to "Fuel",
        "Shopping" to "General",
        "Bills & Utilities" to "General",
        "Subscriptions" to "General",
        "Healthcare" to "General",
        "Travel" to "General",
        "Charity" to "Donation",
        "Transfer" to "Internal",
        "Other" to "Uncategorized"
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Assign Merchant Category",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            presets.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                categoryText = preset.first
                                subcategoryText = preset.second
                                treatmentText = TransactionTreatments.EXPENSE
                                onUpdateMerchantCategory(
                                    categoryText,
                                    subcategoryText,
                                    treatmentText,
                                    applyCategoryAutomatically,
                                    requiresReview
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(preset.first)
                        }
                    }

                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Accounting treatment",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            listOf(
                listOf(TransactionTreatments.EXPENSE, TransactionTreatments.INCOME),
                listOf(TransactionTreatments.REFUND, TransactionTreatments.CREDIT_CARD_PAYMENT),
                listOf(TransactionTreatments.TRANSFER, TransactionTreatments.PERSON_TO_PERSON)
            ).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { treatment ->
                        TreatmentChoiceButton(
                            treatment = treatment,
                            selected = treatmentText == treatment,
                            onClick = { treatmentText = treatment },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = applyCategoryAutomatically,
                    onCheckedChange = { applyCategoryAutomatically = it }
                )
                Text(
                    text = "Apply this category automatically",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = requiresReview,
                    onCheckedChange = { requiresReview = it }
                )
                Text(
                    text = "Keep future matches in review",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = categoryText,
                onValueChange = { categoryText = it },
                label = { Text("Category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = subcategoryText,
                onValueChange = { subcategoryText = it },
                label = { Text("Subcategory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onUpdateMerchantCategory(
                        categoryText,
                        subcategoryText,
                        treatmentText,
                        applyCategoryAutomatically,
                        requiresReview
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Merchant Default")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "This updates existing transactions and saves a merchant default for future parsed SMS.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun TreatmentChoiceButton(
    treatment: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = treatmentLabel(treatment)
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label)
        }
    }
}

@Composable
fun MerchantTransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    val amount = transaction.amountCents / 100.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "$${"%.2f".format(amount)} • ${treatmentLabel(transaction.accountingTreatment)}",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = formatter.format(Date(transaction.occurredAtEpochMs)),
                style = MaterialTheme.typography.labelSmall
            )

            val categoryText = listOfNotNull(
                transaction.categoryName,
                transaction.subcategoryName
            ).joinToString(" / ")

            if (categoryText.isNotBlank()) {
                Text(
                    text = "Category: $categoryText",
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(
                    text = "Category: Not assigned",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (transaction.reviewStatus == "NEEDS_REVIEW") {
                Text(
                    text = "Needs review",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = "Tap for transaction detail",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
