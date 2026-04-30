package com.example.ledgerlens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.domain.source.SourceDetector
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
            MaterialTheme {
                LedgerLensSourceSetupApp(
                    database = database,
                    onBackfillSmsHistory = {
                        requestSmsImport(SmsImportMode.BACKFILL_HISTORY)
                    },
                    onRefreshLatestSms = {
                        requestSmsImport(SmsImportMode.REFRESH_LATEST)
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
            val importedCount = withContext(Dispatchers.IO) {
                when (mode) {
                    SmsImportMode.BACKFILL_HISTORY -> importFinanceSmsMessages(daysBack = 365 * 5)
                    SmsImportMode.REFRESH_LATEST -> importFinanceSmsMessages(daysBack = 90)
                }
            }

            Log.d(
                "LedgerLensSmsImport",
                "mode=$mode importedCount=$importedCount"
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
}

data class CategorySpendSummary(
    val categoryName: String,
    val subcategoryName: String?,
    val amountCents: Long,
    val transactionCount: Int
)

enum class SmsImportMode {
    BACKFILL_HISTORY,
    REFRESH_LATEST
}

enum class AppScreen {
    HOME,
    SOURCES,
    TRANSACTIONS,
    SUMMARY,
    REVIEW_QUEUE,
    MERCHANTS,
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
    val categoryName: String?,
    val subcategoryName: String?,
    val uncategorizedCount: Int,
    val latestTransactionEpochMs: Long
)

@Composable
fun LedgerLensSourceSetupApp(
    database: AppDatabase,
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit
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

    suspend fun saveMergedRule(
        sourceKey: String,
        matchPhrase: String,
        merchantName: String? = null,
        categoryName: String? = null,
        subcategoryName: String? = null,
        transactionType: String? = null,
        reviewStatus: String? = null,
        excludedFromSpending: Boolean? = null
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
                    database.financialSourceDao().confirmAccountType(
                        sourceKey = selectedSource!!.sourceKey,
                        accountType = accountType,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        statusText = "Marked source as $accountType."
                        selectedSource = null
                    }
                }
            },
            onDismissAsNonSource = {
                scope.launch(Dispatchers.IO) {
                    database.financialSourceDao().ignoreSource(
                        sourceKey = selectedSource!!.sourceKey,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        statusText = "Dismissed source as non-source."
                        selectedSource = null
                    }
                }
            },
            onMoveToUncategorized = {
                scope.launch(Dispatchers.IO) {
                    database.financialSourceDao().resetSourceConfirmation(
                        sourceKey = selectedSource!!.sourceKey,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        statusText = "Moved source back to uncategorized."
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
                            excludedFromSpending = excludedFromSpending,
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
                            updatedAtEpochMs = System.currentTimeMillis()
                        )

                        onComplete(updatedCount)
                    }
                }
            }
        )
    } else if (activeScreen == AppScreen.SUMMARY) {
        SpendingSummaryScreen(
            transactions = transactions,
            onBack = {
                activeScreen = AppScreen.SOURCES
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
                activeScreen = AppScreen.SOURCES
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
                activeScreen = AppScreen.SOURCES
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
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
            onUpdateMerchantCategory = { category, subcategory ->
                scope.launch(Dispatchers.IO) {
                    val cleanedCategory = category.trim().ifBlank { null }
                    val cleanedSubcategory = subcategory.trim().ifBlank { null }

                    database.transactionDao().updateCategoryForMerchantName(
                        merchantName = selectedMerchant!!.merchantName,
                        categoryName = cleanedCategory,
                        subcategoryName = cleanedSubcategory,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedMerchant = selectedMerchant!!.copy(
                            categoryName = cleanedCategory,
                            subcategoryName = cleanedSubcategory,
                            uncategorizedCount = 0
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
        )
    } else if (activeScreen == AppScreen.TOOLS) {
        ToolsScreen(
            rawAlertCount = rawAlertCount,
            sourceCount = sourceCount,
            transactionCount = transactionCount,
            statusText = statusText,
            onBack = {
                activeScreen = AppScreen.HOME
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

                    withContext(Dispatchers.Main) {
                        statusText = "Detected ${detectedSources.size} possible SMS sources."
                    }
                }
            },
            onParseIdentifiedSources = {
                statusText = "Parsing transactions from identified sources..."

                scope.launch(Dispatchers.IO) {
                    val rawAlertsOnce = database.rawAlertDao().getAllOnce()
                    val identifiedSources = database
                        .financialSourceDao()
                        .getIdentifiedSourcesOnce()

                    var parsedCount = 0
                    var skippedCount = 0
                    var failedCount = 0
                    var matchedAlertCount = 0

                    identifiedSources.forEach { source ->
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
                                source = source
                            )

                            if (parsedTransaction == null) {
                                database.rawAlertDao().updateProcessingStatus(
                                    rawAlert.id,
                                    "FAILED_TRANSACTION_PARSE"
                                )
                                failedCount++
                            } else {
                                val rules = database
                                    .transactionRuleDao()
                                    .getActiveRulesForSource(source.sourceKey)

                                val ruleAdjustedTransaction = applyRulesToTransaction(
                                    transaction = parsedTransaction,
                                    rawAlert = rawAlert,
                                    rules = rules
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

                    withContext(Dispatchers.Main) {
                        statusText =
                            "Matched $matchedAlertCount SMS from identified sources. Parsed $parsedCount, skipped $skippedCount, failed $failedCount."
                    }
                }
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
fun SourceListScreen(
    sources: List<FinancialSourceEntity>,
    onBack: () -> Unit,
    onSourceSelected: (FinancialSourceEntity) -> Unit
) {
    val uncategorizedSources = sources.filter { !it.userConfirmed && !it.ignored }
    val identifiedSources = sources.filter { it.userConfirmed && !it.ignored }
    val nonSources = sources.filter { it.ignored }

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
fun SetupActionsCard(
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit,
    onDetectSources: () -> Unit,
    onParseIdentifiedSources: () -> Unit,
    onViewTransactions: () -> Unit,
    onViewSummary: () -> Unit,
    onViewReviewQueue: () -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Setup Actions",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBackfillSmsHistory,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Backfill")
                }

                Button(
                    onClick = onRefreshLatestSms,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDetectSources,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Detect Sources")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onParseIdentifiedSources,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Parse Identified Sources")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onViewTransactions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Transactions")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onViewSummary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Spending Summary")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onViewReviewQueue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Review Queue")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onClearAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear All Test Data")
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

    val sortedTransactions = remember(transactions) {
        transactions.sortedByDescending { it.occurredAtEpochMs }
    }

    val filteredTransactions = remember(sortedTransactions, selectedFilter) {
        when (selectedFilter) {
            TransactionFilter.ALL -> sortedTransactions

            TransactionFilter.NEEDS_REVIEW -> sortedTransactions.filter {
                it.reviewStatus == "NEEDS_REVIEW"
            }

            TransactionFilter.EXPENSES -> sortedTransactions.filter {
                it.transactionType == "EXPENSE"
            }

            TransactionFilter.TRANSFERS -> sortedTransactions.filter {
                it.transactionType == "TRANSFER"
            }

            TransactionFilter.CREDIT_CARD_PAYMENTS -> sortedTransactions.filter {
                it.transactionType == "CREDIT_CARD_PAYMENT"
            }

            TransactionFilter.EXCLUDED_FROM_SPENDING -> sortedTransactions.filter {
                it.excludedFromSpending
            }
        }
    }

    val totalCount = sortedTransactions.size
    val needsReviewCount = sortedTransactions.count { it.reviewStatus == "NEEDS_REVIEW" }
    val excludedCount = sortedTransactions.count { it.excludedFromSpending }
    val expenseCount = sortedTransactions.count { it.transactionType == "EXPENSE" }
    val transferCount = sortedTransactions.count { it.transactionType == "TRANSFER" }
    val creditCardPaymentCount = sortedTransactions.count { it.transactionType == "CREDIT_CARD_PAYMENT" }

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
            Text("Excluded from spending: $excludedCount")
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
                    ?: transaction.transactionType,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${"%.2f".format(amount)} • ${transaction.transactionType}",
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
                    text = "Excluded from spending totals",
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
                    label = "Excluded ($excludedCount)",
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

    val amount = transaction.amountCents / 100.0

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

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Parsed Fields",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        DetailRow("Amount", "$${"%.2f".format(amount)}")
                        DetailRow("Transaction type", transaction.transactionType)
                        DetailRow("Merchant raw", transaction.merchantRaw ?: "Not detected")
                        DetailRow("Display merchant", transaction.displayMerchantName ?: "Not detected")
                        DetailRow("Category", transaction.categoryName ?: "Not assigned")
                        DetailRow("Subcategory", transaction.subcategoryName ?: "Not assigned")
                        DetailRow("Source institution", transaction.sourceInstitution ?: "Not detected")
                        DetailRow("Account hint", transaction.accountHint ?: "Not detected")
                        DetailRow("Review status", transaction.reviewStatus)
                        DetailRow("Parse confidence", "${"%.0f".format(transaction.parseConfidence * 100)}%")
                        DetailRow(
                            "Excluded from spending",
                            if (transaction.excludedFromSpending) "Yes" else "No"
                        )
                        DetailRow("Occurred at", formatter.format(Date(transaction.occurredAtEpochMs)))
                        DetailRow("Received at", formatter.format(Date(transaction.receivedAtEpochMs)))
                        DetailRow("Source key", transaction.sourceKey)
                    }
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
                    onUpdateCategory = onUpdateCategory
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
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Parser Notes",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = transaction.parserNotes
                                ?: "No parser notes.",
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                            text = "Original SMS",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (rawAlert == null) {
                            Text(
                                text = "Original raw SMS was not found.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            DetailRow("SMS title", rawAlert.title ?: "None")
                            DetailRow("SMS date", formatter.format(Date(rawAlert.postTimeEpochMs)))
                            DetailRow("Raw status", rawAlert.processingStatus)

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = rawAlert.combinedText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
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
                text = "Quick Correction",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUpdateTransactionType("EXPENSE", false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Expense")
                }

                Button(
                    onClick = { onUpdateTransactionType("TRANSFER", true) },
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
                    onClick = { onUpdateTransactionType("CREDIT_CARD_PAYMENT", true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CC Pay")
                }

                Button(
                    onClick = { onUpdateTransactionType("INCOME", false) },
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
                    onClick = { onUpdateTransactionType("REFUND", false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refund")
                }

                Button(
                    onClick = { onUpdateTransactionType("UNKNOWN", false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Unknown")
                }
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
                text = "Spending Treatment",
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
                    Text("Include")
                }

                Button(
                    onClick = { onUpdateExcludedFromSpending(true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Exclude")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Current: ${transaction.transactionType} • ${transaction.reviewStatus} • ${
                    if (transaction.excludedFromSpending) "Excluded" else "Included"
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
                text = "Merchant / Payee Correction",
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
                text = "Apply to Similar Transactions",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This matches transactions from the same source where the original SMS contains this phrase.",
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
                text = "Current classification: ${transaction.transactionType} • ${transaction.reviewStatus} • ${
                    if (transaction.excludedFromSpending) "Excluded" else "Included"
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
                text = "Category / Subcategory Correction",
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
            .filter { it.transactionType == "EXPENSE" }
            .filter { !it.excludedFromSpending }
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
                            "No included expense transactions for this month. Mark transactions as Expense and assign categories."
                        )
                    }
                } else {
                    items(
                        items = categorySummaries,
                        key = { "${it.categoryName}|${it.subcategoryName ?: ""}" }
                    ) { summary ->
                        CategorySpendCard(
                            summary = summary,
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
                text = "Included Spending",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$${"%.2f".format(total)}",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Included expense transactions: $includedExpenseCount")
            Text("Unassigned category transactions: $unassignedCount")

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "This excludes transfers, credit card payments, and transactions marked excluded from spending.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun CategorySpendCard(
    summary: CategorySpendSummary,
    onClick: () -> Unit
) {
    val amount = summary.amountCents / 100.0

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

            Text(
                text = "Transactions: ${summary.transactionCount}",
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
    return transaction.displayMerchantName.isNullOrBlank() &&
            transaction.merchantRaw.isNullOrBlank()
}

fun hasMissingCategory(transaction: TransactionEntity): Boolean {
    return transaction.transactionType == "EXPENSE" &&
            !transaction.excludedFromSpending &&
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
                text = "$${"%.2f".format(amount)} • ${transaction.transactionType}",
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

fun applyRulesToTransaction(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity,
    rules: List<TransactionRuleEntity>
): TransactionEntity {
    var updated = transaction
    val appliedRulePhrases = mutableListOf<String>()

    rules.forEach { rule ->
        if (!rule.active) return@forEach

        val phrase = rule.matchPhrase.trim()
        if (phrase.isBlank()) return@forEach

        val matches = rawAlert.combinedText.contains(
            other = phrase,
            ignoreCase = true
        )

        if (!matches) return@forEach

        updated = updated.copy(
            merchantRaw = rule.merchantName ?: updated.merchantRaw,
            displayMerchantName = rule.merchantName ?: updated.displayMerchantName,
            categoryName = rule.categoryName ?: updated.categoryName,
            subcategoryName = rule.subcategoryName ?: updated.subcategoryName,
            transactionType = rule.transactionType ?: updated.transactionType,
            reviewStatus = rule.reviewStatus ?: updated.reviewStatus,
            excludedFromSpending = rule.excludedFromSpending ?: updated.excludedFromSpending,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        appliedRulePhrases.add(rule.matchPhrase)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rawAlertCount: Int,
    sourceCount: Int,
    transactionCount: Int,
    onOpenSources: () -> Unit,
    onOpenReviewQueue: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenMerchants: () -> Unit,
    onOpenTools: () -> Unit
){
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LedgerLens") }
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
                    transactionCount = transactionCount
                )
            }

            item {
                HomeNavCard(
                    title = "Spending Summary",
                    description = "View monthly spending totals and category drilldowns.",
                    buttonText = "Open Summary",
                    onClick = onOpenSummary
                )
            }

            item {
                HomeNavCard(
                    title = "Review Queue",
                    description = "Fix transactions missing merchant, category, or needing review.",
                    buttonText = "Open Review Queue",
                    onClick = onOpenReviewQueue
                )
            }

            item {
                HomeNavCard(
                    title = "Merchant Review",
                    description = "Assign default categories to merchants/payees like Walmart, Shell, Netflix, or donations.",
                    buttonText = "Open Merchants",
                    onClick = onOpenMerchants
                )
            }

            item {
                HomeNavCard(
                    title = "Transactions",
                    description = "Browse all parsed transactions and inspect parser output.",
                    buttonText = "Open Transactions",
                    onClick = onOpenTransactions
                )
            }

            item {
                HomeNavCard(
                    title = "Sources",
                    description = "Review SMS senders and classify them as sources or non-sources.",
                    buttonText = "Open Sources",
                    onClick = onOpenSources
                )
            }

            item {
                HomeNavCard(
                    title = "Tools",
                    description = "Import SMS, detect sources, parse identified sources, and clear test data.",
                    buttonText = "Open Tools",
                    onClick = onOpenTools
                )
            }
        }
    }
}

@Composable
fun HomeStatusCard(
    rawAlertCount: Int,
    sourceCount: Int,
    transactionCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Current Data",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Imported SMS alerts: $rawAlertCount")
            Text("Detected sources: $sourceCount")
            Text("Parsed transactions: $transactionCount")
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
fun ToolsScreen(
    rawAlertCount: Int,
    sourceCount: Int,
    transactionCount: Int,
    statusText: String,
    onBack: () -> Unit,
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit,
    onDetectSources: () -> Unit,
    onParseIdentifiedSources: () -> Unit,
    onClearAll: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
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
                    }
                }
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
                    it.transactionType == "EXPENSE" && !it.excludedFromSpending
                }

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
                    categoryName = mostCommonCategory?.first,
                    subcategoryName = mostCommonCategory?.second,
                    uncategorizedCount = group.count {
                        it.transactionType == "EXPENSE" &&
                                !it.excludedFromSpending &&
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

    val uncategorizedMerchants = merchantSummaries.filter { it.uncategorizedCount > 0 }
    val categorizedMerchants = merchantSummaries.filter { it.uncategorizedCount == 0 }

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
                text = "This groups existing parsed transactions by merchant/payee. Future merchant rules will be added later.",
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
    onUpdateMerchantCategory: (String, String) -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    val expenseTotal = transactions
        .filter { it.transactionType == "EXPENSE" }
        .filter { !it.excludedFromSpending }
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
    onUpdateMerchantCategory: (String, String) -> Unit
) {
    var categoryText by remember(merchant.merchantName, merchant.categoryName) {
        mutableStateOf(merchant.categoryName ?: "")
    }

    var subcategoryText by remember(merchant.merchantName, merchant.subcategoryName) {
        mutableStateOf(merchant.subcategoryName ?: "")
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
                                onUpdateMerchantCategory(categoryText, subcategoryText)
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
                    onUpdateMerchantCategory(categoryText, subcategoryText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply to Existing Merchant Transactions")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "For now, this updates existing transactions for this merchant. Future merchant default rules will be added later.",
                style = MaterialTheme.typography.labelSmall
            )
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
                text = "$${"%.2f".format(amount)} • ${transaction.transactionType}",
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