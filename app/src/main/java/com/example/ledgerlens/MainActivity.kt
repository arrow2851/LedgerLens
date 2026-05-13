package com.example.ledgerlens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.Telephony
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.domain.source.SourceDetector
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.export.buildParserCorpusJsonl
import com.example.ledgerlens.domain.export.buildTransactionsCsv
import com.example.ledgerlens.domain.merchants.applyMerchantCategoryBulk
import com.example.ledgerlens.domain.parser.ParseRunResult
import com.example.ledgerlens.domain.parser.detectAndSaveSources
import com.example.ledgerlens.domain.parser.parseIdentifiedSourceTransactions
import com.example.ledgerlens.domain.parser.reapplySavedRulesToExistingTransactions
import com.example.ledgerlens.domain.parser.updateRawAlertStatusesForSource
import com.example.ledgerlens.ui.theme.LedgerLensTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ledgerlens.data.entity.TransactionEntity
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableLongStateOf
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.rules.MERCHANT_DEFAULT_RULE_SOURCE_KEY
import com.example.ledgerlens.domain.rules.MerchantAliasApplyResult
import com.example.ledgerlens.domain.rules.MerchantAliasRuleDraft
import com.example.ledgerlens.domain.rules.applyMerchantAliasRuleToTransaction
import com.example.ledgerlens.domain.rules.buildMerchantAliasRules
import com.example.ledgerlens.domain.rules.normalizeRulePhrase
import com.example.ledgerlens.domain.rules.normalizeAliasText
import com.example.ledgerlens.domain.rules.previewMerchantAliasRule
import com.example.ledgerlens.domain.summary.CategorySpendSummary
import com.example.ledgerlens.domain.summary.MerchantSummary
import com.example.ledgerlens.domain.summary.categorySpendSummaries
import com.example.ledgerlens.domain.summary.displayCategoryName
import com.example.ledgerlens.domain.summary.expenseTransactionsForRange
import com.example.ledgerlens.domain.summary.formatMonthYear
import com.example.ledgerlens.domain.summary.getCurrentMonthStartEpochMs
import com.example.ledgerlens.domain.summary.getNextMonthStartEpochMs
import com.example.ledgerlens.domain.summary.getPreviousMonthStartEpochMs
import com.example.ledgerlens.domain.summary.hasAnyReviewIssue
import com.example.ledgerlens.domain.summary.hasLowConfidence
import com.example.ledgerlens.domain.summary.hasMissingCategory
import com.example.ledgerlens.domain.summary.hasMissingMerchant
import com.example.ledgerlens.domain.summary.isVirtualUncategorizedCategory
import com.example.ledgerlens.domain.summary.merchantSummaries
import com.example.ledgerlens.domain.summary.merchantSummaryName
import com.example.ledgerlens.domain.summary.treatmentLabel
import com.example.ledgerlens.ui.components.CategoryBarRow
import com.example.ledgerlens.ui.components.FinanceHeroCard
import com.example.ledgerlens.ui.components.InlineInfoPanel
import com.example.ledgerlens.ui.components.LedgerAppScaffold
import com.example.ledgerlens.ui.components.LedgerBottomNav
import com.example.ledgerlens.ui.components.LedgerListRow
import com.example.ledgerlens.ui.components.ListSectionHeader
import com.example.ledgerlens.ui.components.MetricTile
import com.example.ledgerlens.ui.components.MetricPanel
import com.example.ledgerlens.ui.components.MiniTrendStrip
import com.example.ledgerlens.ui.components.QuickActionItem
import com.example.ledgerlens.ui.components.QuickActionSheet
import com.example.ledgerlens.ui.components.StatStrip
import com.example.ledgerlens.ui.components.StatStripItem
import com.example.ledgerlens.ui.components.TreatmentSelector
import com.example.ledgerlens.ui.components.TreatmentChip
import com.example.ledgerlens.ui.AppScreen
import com.example.ledgerlens.ui.LedgerBackAction
import com.example.ledgerlens.ui.LedgerLensApp
import com.example.ledgerlens.ui.ReviewQueueFilter
import com.example.ledgerlens.ui.TransactionFilter
import com.example.ledgerlens.ui.resolveLedgerBackAction
import com.example.ledgerlens.ui.merchants.MerchantReviewScreen as MerchantReviewInboxScreen
import com.example.ledgerlens.ui.rules.ParserRuleEditorSheet
import java.io.File

fun formatSignedMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    return "$sign${'$'}${"%.2f".format(Locale.US, kotlin.math.abs(cents) / 100.0)}"
}

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
                LedgerLensApp(
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

enum class SmsImportMode {
    BACKFILL_HISTORY,
    REFRESH_LATEST
}

data class ParserRuleEditorRequest(
    val title: String,
    val draft: MerchantAliasRuleDraft,
    val replaceRuleId: Long? = null
)

fun buildMerchantAliasDraftForTransaction(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity?,
    allTransactions: List<TransactionEntity>,
    includeCategory: Boolean = false,
    includeTreatment: Boolean = false
): MerchantAliasRuleDraft {
    val currentMerchant = transaction.displayMerchantName
        ?: transaction.merchantRaw
        ?: transaction.sourceInstitution
        ?: ""
    val normalizedSeed = normalizeAliasText(currentMerchant)
    val alphaSeed = normalizedSeed.takeWhile { it.isLetter() }

    val siblingAliases = if (alphaSeed.length >= 4) {
        allTransactions
            .asSequence()
            .filter { it.sourceKey == transaction.sourceKey }
            .mapNotNull { it.displayMerchantName ?: it.merchantRaw }
            .filter { merchant ->
                val normalized = normalizeAliasText(merchant)
                normalized.startsWith(alphaSeed.take(6)) ||
                        alphaSeed.startsWith(normalized.takeWhile { it.isLetter() }.take(6))
            }
            .toList()
    } else {
        emptyList()
    }

    val aliases = buildList {
        transaction.merchantRaw?.takeIf { it.isNotBlank() }?.let { add(it) }
        transaction.displayMerchantName?.takeIf { it.isNotBlank() }?.let { add(it) }
        rawAlert?.combinedText
            ?.split(" ", "\n", "\t")
            ?.windowed(size = 2, step = 1, partialWindows = true)
            ?.map { it.joinToString(" ").trim() }
            ?.filter { normalizeAliasText(it).contains(normalizedSeed.take(6)) && it.length <= 40 }
            ?.take(2)
            ?.let { addAll(it) }
        addAll(siblingAliases)
    }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalizeAliasText(it) }

    return MerchantAliasRuleDraft(
        sourceKey = transaction.sourceKey,
        canonicalMerchantName = currentMerchant,
        aliases = aliases.ifEmpty { listOf(currentMerchant) },
        applyCategory = includeCategory && !transaction.categoryName.isNullOrBlank(),
        categoryName = transaction.categoryName,
        applyTreatment = includeTreatment,
        transactionType = transaction.accountingTreatment,
        requiresReview = transaction.reviewStatus == "NEEDS_REVIEW"
    )
}

fun buildMerchantAliasDraftForRule(rule: TransactionRuleEntity): MerchantAliasRuleDraft {
    return MerchantAliasRuleDraft(
        sourceKey = rule.sourceKey,
        canonicalMerchantName = rule.merchantName ?: "",
        aliases = listOf(rule.matchPhrase),
        applyCategory = rule.applyCategoryAutomatically && !rule.categoryName.isNullOrBlank(),
        categoryName = rule.categoryName,
        applyTreatment = !rule.transactionType.isNullOrBlank(),
        transactionType = rule.transactionType,
        requiresReview = rule.requiresReview,
        active = rule.active
    )
}

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
        mutableStateOf(AppScreen.SUMMARY)
    }

    var selectedTransaction by remember {
        mutableStateOf<TransactionEntity?>(null)
    }

    var selectedMerchant by remember {
        mutableStateOf<MerchantSummary?>(null)
    }

    var showQuickActions by remember {
        mutableStateOf(false)
    }

    var parserRuleEditorRequest by remember {
        mutableStateOf<ParserRuleEditorRequest?>(null)
    }

    val backAction = resolveLedgerBackAction(
        showSheet = showQuickActions || parserRuleEditorRequest != null,
        hasSelectedTransaction = selectedTransaction != null,
        hasSelectedSource = selectedSource != null,
        hasSelectedMerchant = selectedMerchant != null,
        activeScreen = activeScreen
    )

    BackHandler(enabled = backAction != LedgerBackAction.EXIT_APP) {
        when (backAction) {
            LedgerBackAction.DISMISS_SHEET -> {
                showQuickActions = false
                parserRuleEditorRequest = null
            }
            LedgerBackAction.CLOSE_TRANSACTION_DETAIL -> selectedTransaction = null
            LedgerBackAction.CLOSE_SOURCE_DETAIL -> selectedSource = null
            LedgerBackAction.CLOSE_MERCHANT_DETAIL -> selectedMerchant = null
            LedgerBackAction.GO_REVIEW -> activeScreen = AppScreen.REVIEW_QUEUE
            LedgerBackAction.GO_SPENDING -> activeScreen = AppScreen.SUMMARY
            LedgerBackAction.EXIT_APP -> Unit
        }
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
                TransactionTreatments.isInSpendingView(
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
        currentMonthExpenses.sumOf {
            TransactionTreatments.spendingImpactCents(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending,
                amountCents = it.amountCents
            )
        }
    }

    val currentMonthGrossExpenseCents = remember(currentMonthExpenses) {
        currentMonthExpenses
            .filter { it.accountingTreatment == TransactionTreatments.EXPENSE }
            .sumOf { it.amountCents }
    }

    val currentMonthCategorySummaries = remember(currentMonthExpenses) {
        categorySpendSummaries(currentMonthExpenses).take(4)
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

    val currentMonthReimbursementCents = remember(currentMonthActivity) {
        currentMonthActivity
            .filter { it.accountingTreatment == TransactionTreatments.REIMBURSEMENT }
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
                TransactionTreatments.isInSpendingView(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending
                )
            }
            .filter {
                it.occurredAtEpochMs >= previousMonthStart &&
                        it.occurredAtEpochMs < currentMonthStart
            }
            .sumOf {
                TransactionTreatments.spendingImpactCents(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending,
                    amountCents = it.amountCents
                )
            }
    }

    val topCategoryLabel = remember(currentMonthExpenses) {
        currentMonthExpenses
            .groupBy { displayCategoryName(it.categoryName) }
            .maxByOrNull { entry ->
                kotlin.math.abs(
                    entry.value.sumOf {
                        TransactionTreatments.spendingImpactCents(
                            treatment = it.accountingTreatment,
                            excludedFromSpending = it.excludedFromSpending,
                            amountCents = it.amountCents
                        )
                    }
                )
            }
            ?.let { entry ->
                val impact = entry.value.sumOf {
                    TransactionTreatments.spendingImpactCents(
                        treatment = it.accountingTreatment,
                        excludedFromSpending = it.excludedFromSpending,
                        amountCents = it.amountCents
                    )
                }
                "${entry.key} - ${formatSignedMoney(impact)}"
            }
            ?: "No spending yet"
    }

    val topMerchantLabel = remember(currentMonthExpenses) {
        currentMonthExpenses
            .groupBy {
                it.spendingMerchantName?.takeIf { merchant -> merchant.isNotBlank() }
                    ?: it.displayMerchantName
                    ?: it.merchantRaw
                    ?: "Unknown merchant"
            }
            .maxByOrNull { entry ->
                kotlin.math.abs(
                    entry.value.sumOf {
                        TransactionTreatments.spendingImpactCents(
                            treatment = it.accountingTreatment,
                            excludedFromSpending = it.excludedFromSpending,
                            amountCents = it.amountCents
                        )
                    }
                )
            }
            ?.let { entry ->
                val impact = entry.value.sumOf {
                    TransactionTreatments.spendingImpactCents(
                        treatment = it.accountingTreatment,
                        excludedFromSpending = it.excludedFromSpending,
                        amountCents = it.amountCents
                    )
                }
                "${entry.key} - ${formatSignedMoney(impact)}"
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

    suspend fun applyParserAliasRuleDraft(
        draft: MerchantAliasRuleDraft,
        replaceRuleId: Long?
    ): MerchantAliasApplyResult {
        val now = System.currentTimeMillis()
        val currentTransactions = database.transactionDao().getAllOnce()
        val rawAlertsById = database.rawAlertDao().getAllOnce().associateBy { it.id }
        val previewItems = previewMerchantAliasRule(
            draft = draft,
            transactions = currentTransactions,
            rawAlertsById = rawAlertsById
        )
        val rules = buildMerchantAliasRules(draft, now)

        if (replaceRuleId != null) {
            database.transactionRuleDao().deleteById(replaceRuleId)
        }

        rules.forEach { rule ->
            database.transactionRuleDao().upsert(rule)
        }

        var merchantUpdated = 0
        var categoryUpdated = 0
        var treatmentUpdated = 0

        previewItems.forEach { preview ->
            val transaction = preview.transaction
            val updated = applyMerchantAliasRuleToTransaction(
                transaction = transaction,
                draft = draft,
                now = now
            )

            if (updated != transaction) {
                if (updated.displayMerchantName != transaction.displayMerchantName ||
                    updated.merchantRaw != transaction.merchantRaw
                ) {
                    merchantUpdated++
                }
                if (updated.categoryName != transaction.categoryName) {
                    categoryUpdated++
                }
                if (updated.accountingTreatment != transaction.accountingTreatment) {
                    treatmentUpdated++
                }
                database.transactionDao().update(updated)
            }
        }

        return MerchantAliasApplyResult(
            rulesSaved = rules.size,
            matchedTransactions = previewItems.size,
            merchantUpdated = merchantUpdated,
            categoryUpdated = categoryUpdated,
            treatmentUpdated = treatmentUpdated,
            skippedMerchantUserEdited = previewItems.count { it.skippedMerchantUserEdited }
        )
    }

    if (showQuickActions) {
        QuickActionSheet(
            actions = listOf(
                QuickActionItem(
                    title = "Refresh latest SMS",
                    supportingText = "Import new SMS alerts without duplicating existing ones.",
                    onClick = {
                        statusText = "Refreshing latest SMS..."
                        onRefreshLatestSms()
                    }
                ),
                QuickActionItem(
                    title = "Backfill SMS history",
                    supportingText = "Import older financial-looking SMS alerts.",
                    onClick = {
                        statusText = "Running SMS backfill..."
                        onBackfillSmsHistory()
                    }
                ),
                QuickActionItem(
                    title = "Detect sources",
                    supportingText = "Find sender-level financial sources from imported SMS.",
                    onClick = {
                        statusText = "Detecting SMS sources..."
                        scope.launch(Dispatchers.IO) {
                            val detectedCount = detectAndSaveSources()
                            withContext(Dispatchers.Main) {
                                statusText = "Detected $detectedCount possible SMS sources."
                            }
                        }
                    }
                ),
                QuickActionItem(
                    title = "Parse identified sources",
                    supportingText = "Build transactions from sources you already approved.",
                    onClick = {
                        statusText = "Parsing transactions from identified sources..."
                        scope.launch(Dispatchers.IO) {
                            val result = parseIdentifiedSourceTransactions(database)
                            withContext(Dispatchers.Main) {
                                statusText =
                                    "Matched ${result.matchedAlertCount} SMS from identified sources. Parsed ${result.parsedCount}, skipped existing ${result.skippedCount}, ignored ${result.ignoredNonTransactionCount}, failed ${result.failedCount}."
                            }
                        }
                    }
                ),
                QuickActionItem(
                    title = "Reapply saved rules",
                    supportingText = "Apply merchant defaults and phrase rules to existing transactions.",
                    onClick = {
                        statusText = "Reapplying saved rules to existing transactions..."
                        scope.launch(Dispatchers.IO) {
                            val updatedCount = reapplySavedRulesToExistingTransactions(database)
                            withContext(Dispatchers.Main) {
                                statusText = "Reapplied saved rules to $updatedCount existing transactions."
                            }
                        }
                    }
                ),
                QuickActionItem(
                    title = "Export transactions",
                    supportingText = "Share a CSV of parsed transaction data.",
                    onClick = {
                        statusText = "Opening transaction export..."
                        onExportTransactions()
                    }
                ),
                QuickActionItem(
                    title = "Export parser corpus",
                    supportingText = "Share local JSONL examples for parser tuning.",
                    onClick = {
                        statusText = "Opening parser corpus export..."
                        onExportParserCorpus()
                    }
                ),
                QuickActionItem(
                    title = "Tools and settings",
                    supportingText = "Open maintenance actions, sources, and rules.",
                    onClick = {
                        activeScreen = AppScreen.TOOLS
                    }
                )
            ),
            onDismiss = {
                showQuickActions = false
            }
        )
    }

    parserRuleEditorRequest?.let { request ->
        ParserRuleEditorSheet(
            title = request.title,
            initialDraft = request.draft,
            transactions = transactions,
            rawAlerts = rawAlerts,
            onDismiss = {
                parserRuleEditorRequest = null
            },
            onApply = { draft ->
                val selectedTransactionId = selectedTransaction?.id
                scope.launch(Dispatchers.IO) {
                    val result = applyParserAliasRuleDraft(
                        draft = draft,
                        replaceRuleId = request.replaceRuleId
                    )
                    val refreshedSelectedTransaction = selectedTransactionId?.let { selectedId ->
                        database.transactionDao()
                            .getAllOnce()
                            .firstOrNull { it.id == selectedId }
                    }
                    withContext(Dispatchers.Main) {
                        if (refreshedSelectedTransaction != null) {
                            selectedTransaction = refreshedSelectedTransaction
                        }
                        statusText = "Saved ${result.rulesSaved} parser aliases. Matched ${result.matchedTransactions}; renamed ${result.merchantUpdated}; category ${result.categoryUpdated}; treatment ${result.treatmentUpdated}; skipped ${result.skippedMerchantUserEdited} manual merchant edits."
                        parserRuleEditorRequest = null
                    }
                }
            }
        )
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
            allTransactions = transactions,
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
            onUpdateSpendingAttribution = { category, spendingMerchant ->
                scope.launch(Dispatchers.IO) {
                    val cleanedCategory = category.trim().ifBlank { null }
                    val cleanedSpendingMerchant = spendingMerchant.trim().ifBlank { null }
                    val updatedAt = System.currentTimeMillis()

                    database.transactionDao().updateSpendingAttribution(
                        transactionId = selectedTransaction!!.id,
                        categoryName = cleanedCategory,
                        spendingMerchantName = cleanedSpendingMerchant,
                        updatedAtEpochMs = updatedAt
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            categoryName = cleanedCategory,
                            spendingMerchantName = cleanedSpendingMerchant,
                            categoryUserEdited = true,
                            updatedAtEpochMs = updatedAt
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
            onApplyCategoryToSimilar = { matchPhrase, category, onComplete ->
                scope.launch(Dispatchers.IO) {
                    val cleanedPhrase = matchPhrase.trim()
                    val cleanedCategory = category.trim().ifBlank { null }

                    if (cleanedPhrase.isBlank()) {
                        withContext(Dispatchers.Main) {
                            onComplete(0)
                        }
                        return@launch
                    }

                    saveMergedRule(
                        sourceKey = selectedTransaction!!.sourceKey,
                        matchPhrase = cleanedPhrase,
                        categoryName = cleanedCategory
                    )

                    val updatedCount = database.transactionDao().updateCategoryForSimilarRawText(
                        sourceKey = selectedTransaction!!.sourceKey,
                        likePattern = "%$cleanedPhrase%",
                        categoryName = cleanedCategory,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = selectedTransaction!!.copy(
                            categoryName = cleanedCategory,
                            categoryUserEdited = true,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )

                        onComplete(updatedCount)
                    }
                }
            },
            onOpenParserRuleEditor = { draft ->
                parserRuleEditorRequest = ParserRuleEditorRequest(
                    title = "Fix parser rule",
                    draft = draft
                )
            }
        )
    } else if (activeScreen == AppScreen.SETUP) {
        SetupScreen(
            rawAlertCount = rawAlertCount,
            sources = sources,
            statusText = statusText,
            onBack = {
                activeScreen = AppScreen.TOOLS
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
            onNavigate = { screen ->
                activeScreen = screen
            },
            onQuickActions = {
                showQuickActions = true
            },
            onBack = {
                activeScreen = AppScreen.SUMMARY
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
            }
        )
    }

    else if (activeScreen == AppScreen.REVIEW_QUEUE) {
        ReviewQueueScreen(
            transactions = transactions,
            onNavigate = { screen ->
                activeScreen = screen
            },
            onQuickActions = {
                showQuickActions = true
            },
            onBack = {
                activeScreen = AppScreen.SUMMARY
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
            }
        )

    }

    else if (activeScreen == AppScreen.TRANSACTIONS) {
        TransactionReviewScreen(
            transactions = transactions,
            onNavigate = { screen ->
                activeScreen = screen
            },
            onQuickActions = {
                showQuickActions = true
            },
            onBack = {
                activeScreen = AppScreen.SUMMARY
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
            }
        )
    } else if (activeScreen == AppScreen.RULES) {
        RulesScreen(
            rules = activeRules,
            transactions = transactions,
            rawAlerts = rawAlerts,
            onBack = {
                activeScreen = AppScreen.TOOLS
            },
            onOpenParserRuleEditor = { rule ->
                parserRuleEditorRequest = ParserRuleEditorRequest(
                    title = "Edit parser rule",
                    draft = buildMerchantAliasDraftForRule(rule),
                    replaceRuleId = rule.id
                )
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
                merchantSummaryName(it).equals(selectedMerchant!!.merchantName, ignoreCase = true)
            }
            .sortedByDescending { it.occurredAtEpochMs }

        MerchantDetailScreen(
            merchant = selectedMerchant!!,
            transactions = merchantTransactions,
            allTransactions = transactions,
            rawAlerts = rawAlerts,
            onBack = {
                selectedMerchant = null
            },
            onUpdateMerchantCategory = { category, treatment, applyCategoryAutomatically, requiresReview ->
                scope.launch(Dispatchers.IO) {
                    val cleanedCategory = category.trim().ifBlank { null }
                    val cleanedTreatment = treatment.trim().ifBlank {
                        selectedMerchant!!.primaryTreatment
                    }
                    val now = System.currentTimeMillis()

                    if (applyCategoryAutomatically) {
                        database.transactionDao().updateCategoryForMerchantName(
                            merchantName = selectedMerchant!!.merchantName,
                            categoryName = cleanedCategory,
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
                            uncategorizedCount = if (applyCategoryAutomatically) 0 else selectedMerchant!!.uncategorizedCount
                        )
                    }
                }
            },
            onRenameMerchantGroup = { newName ->
                scope.launch(Dispatchers.IO) {
                    val oldName = selectedMerchant!!.merchantName
                    val cleanedName = newName.trim().ifBlank { oldName }
                    var updatedCount = 0
                    database.transactionDao()
                        .getAllOnce()
                        .filter { transaction ->
                            merchantSummaryName(transaction).equals(oldName, ignoreCase = true)
                        }
                        .forEach { transaction ->
                            val isSpendingAttribution = transaction.spendingMerchantName
                                ?.equals(oldName, ignoreCase = true)
                                ?: false
                            database.transactionDao().update(
                                if (isSpendingAttribution) {
                                    transaction.copy(
                                        spendingMerchantName = cleanedName,
                                        categoryUserEdited = true,
                                        updatedAtEpochMs = System.currentTimeMillis()
                                    )
                                } else {
                                    transaction.copy(
                                        merchantRaw = cleanedName,
                                        displayMerchantName = cleanedName,
                                        merchantUserEdited = true,
                                        updatedAtEpochMs = System.currentTimeMillis()
                                    )
                                }
                            )
                            updatedCount++
                        }

                    withContext(Dispatchers.Main) {
                        selectedMerchant = selectedMerchant!!.copy(merchantName = cleanedName)
                        statusText = "Renamed $updatedCount existing transactions to $cleanedName without creating a parser rule."
                    }
                }
            },
            onOpenParserRuleEditor = { draft ->
                parserRuleEditorRequest = ParserRuleEditorRequest(
                    title = "Fix merchant parser rule",
                    draft = draft
                )
            },
            onTransactionSelected = { transaction ->
                selectedTransaction = transaction
            }
        )
    } else if (activeScreen == AppScreen.MERCHANTS) {
        MerchantReviewInboxScreen(
            transactions = transactions,
            rules = activeRules,
            onBack = {
                activeScreen = AppScreen.REVIEW_QUEUE
            },
            onMerchantSelected = { merchant ->
                selectedMerchant = merchant
            },
            onApplyCategoryToMerchants = { merchantNames, option ->
                scope.launch(Dispatchers.IO) {
                    val result = applyMerchantCategoryBulk(
                        database = database,
                        merchantNames = merchantNames,
                        option = option
                    )

                    withContext(Dispatchers.Main) {
                        statusText = "Applied ${option.label} to ${result.merchantCount} merchants and ${result.transactionCount} transactions."
                    }
                }
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
            currentMonthReimbursementCents = currentMonthReimbursementCents,
            currentMonthMovementCents = currentMonthMovementCents,
            previousMonthSpendingCents = previousMonthSpendingCents,
            currentMonthGrossExpenseCents = currentMonthGrossExpenseCents,
            currentMonthExpenseCount = currentMonthExpenses.size,
            currentMonthCategorySummaries = currentMonthCategorySummaries,
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
            },
            onOpenQuickActions = {
                showQuickActions = true
            }
        )
    } else if (activeScreen == AppScreen.TOOLS) {
        ToolsScreen(
            rawAlertCount = rawAlertCount,
            sourceCount = sourceCount,
            transactionCount = transactionCount,
            activeRuleCount = activeRuleCount,
            transactions = transactions,
            statusText = statusText,
            onNavigate = { screen ->
                activeScreen = screen
            },
            onQuickActions = {
                showQuickActions = true
            },
            onBack = {
                activeScreen = AppScreen.SUMMARY
            },
            onOpenSetup = {
                activeScreen = AppScreen.SETUP
            },
            onOpenSources = {
                activeScreen = AppScreen.SOURCES
            },
            onOpenRules = {
                activeScreen = AppScreen.RULES
            },
            onMerchantSelected = { merchant ->
                selectedMerchant = merchant
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
                activeScreen = AppScreen.TOOLS
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

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    tonalElevation = 0.dp
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    tonalElevation = 0.dp
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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

    LedgerListRow(
        title = source.displayName
            ?: source.institutionName
            ?: "Unknown financial source",
        supportingText = "$categoryLabel - Type: $effectiveType - Messages: ${source.messageCount}",
        metadataText = "Sender ${source.sourceAddress} - ${"%.0f".format(source.detectionConfidence * 100)}% confidence",
        pillText = categoryLabel,
        leadingText = source.sourceAddress.take(2),
        trailingText = "Review",
        onClick = onClick
    )
    return

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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
    onNavigate: (AppScreen) -> Unit,
    onQuickActions: () -> Unit,
    onBack: () -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    var selectedFilter by remember {
        mutableStateOf(TransactionFilter.ALL)
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var selectedMonthStart by remember {
        mutableLongStateOf(getCurrentMonthStartEpochMs())
    }

    var monthFilterEnabled by remember {
        mutableStateOf(true)
    }

    val sortedTransactions = remember(transactions) {
        transactions.sortedByDescending { it.occurredAtEpochMs }
    }

    val selectedMonthEnd = remember(selectedMonthStart) {
        getNextMonthStartEpochMs(selectedMonthStart)
    }

    val filteredTransactions = remember(
        sortedTransactions,
        selectedFilter,
        searchText,
        selectedMonthStart,
        selectedMonthEnd,
        monthFilterEnabled
    ) {
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
                !TransactionTreatments.isInSpendingView(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending
                )
            }
        }

        val dateFiltered = if (monthFilterEnabled) {
            base.filter {
                it.occurredAtEpochMs >= selectedMonthStart &&
                    it.occurredAtEpochMs < selectedMonthEnd
            }
        } else {
            base
        }

        val query = searchText.trim().lowercase(Locale.US)
        if (query.isBlank()) {
            dateFiltered
        } else {
            dateFiltered.filter { transaction ->
                listOfNotNull(
                    transaction.displayMerchantName,
                    transaction.spendingMerchantName,
                    transaction.merchantRaw,
                    transaction.categoryName,
                    transaction.sourceInstitution,
                    transaction.accountingTreatment,
                    transaction.transactionType,
                    transaction.reviewStatus
                ).any { it.lowercase(Locale.US).contains(query) }
            }
        }
    }

    val totalCount = remember(sortedTransactions, monthFilterEnabled, selectedMonthStart, selectedMonthEnd) {
        sortedTransactions.count {
            !monthFilterEnabled ||
                (it.occurredAtEpochMs >= selectedMonthStart && it.occurredAtEpochMs < selectedMonthEnd)
        }
    }
    val visibleTransactionsForCounts = remember(sortedTransactions, monthFilterEnabled, selectedMonthStart, selectedMonthEnd) {
        if (monthFilterEnabled) {
            sortedTransactions.filter {
                it.occurredAtEpochMs >= selectedMonthStart && it.occurredAtEpochMs < selectedMonthEnd
            }
        } else {
            sortedTransactions
        }
    }
    val needsReviewCount = visibleTransactionsForCounts.count { it.reviewStatus == "NEEDS_REVIEW" }
    val excludedCount = visibleTransactionsForCounts.count {
        !TransactionTreatments.isInSpendingView(
            treatment = it.accountingTreatment,
            excludedFromSpending = it.excludedFromSpending
        )
    }
    val expenseCount = visibleTransactionsForCounts.count { it.accountingTreatment == TransactionTreatments.EXPENSE }
    val transferCount = visibleTransactionsForCounts.count {
        it.accountingTreatment in setOf(
            TransactionTreatments.TRANSFER,
            TransactionTreatments.PERSON_TO_PERSON
        )
    }
    val creditCardPaymentCount = visibleTransactionsForCounts.count {
        it.accountingTreatment == TransactionTreatments.CREDIT_CARD_PAYMENT
    }
    val filteredImpactCents = filteredTransactions.sumOf {
        TransactionTreatments.spendingImpactCents(
            treatment = it.accountingTreatment,
            excludedFromSpending = it.excludedFromSpending,
            amountCents = it.amountCents
        )
    }
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { activityDateHeaderLabel(it.occurredAtEpochMs) }
    }

    LedgerAppScaffold(
        title = "Activity",
        activeScreen = AppScreen.TRANSACTIONS,
        onNavigate = onNavigate,
        onQuickActions = onQuickActions,
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                ActivitySearchAndFilters(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                    monthLabel = if (monthFilterEnabled) formatMonthYear(selectedMonthStart) else "All months",
                    monthFilterEnabled = monthFilterEnabled,
                    onPreviousMonth = {
                        monthFilterEnabled = true
                        selectedMonthStart = getPreviousMonthStartEpochMs(selectedMonthStart)
                    },
                    onNextMonth = {
                        monthFilterEnabled = true
                        selectedMonthStart = getNextMonthStartEpochMs(selectedMonthStart)
                    },
                    onToggleAllMonths = {
                        monthFilterEnabled = !monthFilterEnabled
                    },
                    totalCount = totalCount,
                    needsReviewCount = needsReviewCount,
                    expenseCount = expenseCount,
                    transferCount = transferCount,
                    creditCardPaymentCount = creditCardPaymentCount,
                    excludedCount = excludedCount
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                ActivityListSummary(
                    filteredCount = filteredTransactions.size,
                    filteredImpactCents = filteredImpactCents
                )
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    EmptySectionText("No transactions found for this filter.")
                }
            } else {
                groupedTransactions.forEach { (dateLabel, dateTransactions) ->
                    item(key = "header-$dateLabel") {
                        ActivityDateHeader(dateLabel)
                    }

                    items(
                        items = dateTransactions,
                        key = { it.id }
                    ) { transaction ->
                        ActivityTransactionRow(
                            transaction = transaction,
                            onClick = { onTransactionSelected(transaction) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
fun ActivitySearchAndFilters(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    selectedFilter: TransactionFilter,
    onFilterSelected: (TransactionFilter) -> Unit,
    monthLabel: String,
    monthFilterEnabled: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleAllMonths: () -> Unit,
    totalCount: Int,
    needsReviewCount: Int,
    expenseCount: Int,
    transferCount: Int,
    creditCardPaymentCount: Int,
    excludedCount: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = { Text("Search transactions") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPreviousMonth,
                modifier = Modifier.weight(0.7f)
            ) {
                Text("<")
            }
            OutlinedButton(
                onClick = onToggleAllMonths,
                modifier = Modifier.weight(1.8f)
            ) {
                Text(monthLabel)
            }
            OutlinedButton(
                onClick = onNextMonth,
                modifier = Modifier.weight(0.7f)
            ) {
                Text(">")
            }
        }

        if (!monthFilterEnabled) {
            Text(
                text = "Showing every imported transaction",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val filterItems = listOf(
            TransactionFilter.ALL to "All $totalCount",
            TransactionFilter.NEEDS_REVIEW to "Needs review $needsReviewCount",
            TransactionFilter.EXPENSES to "Expenses $expenseCount",
            TransactionFilter.TRANSFERS to "Transfers $transferCount",
            TransactionFilter.CREDIT_CARD_PAYMENTS to "Card payments $creditCardPaymentCount",
            TransactionFilter.EXCLUDED_FROM_SPENDING to "Outside spending $excludedCount"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(
                items = filterItems,
                key = { it.first.name }
            ) { (filter, label) ->
                ActivityFilterChip(
                    label = label,
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) }
                )
            }
        }
    }
}

@Composable
fun ActivityFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (selected) colors.primary.copy(alpha = 0.12f) else colors.surface,
        contentColor = if (selected) colors.primary else colors.onSurfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) colors.primary.copy(alpha = 0.35f) else colors.outlineVariant
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun ActivityListSummary(
    filteredCount: Int,
    filteredImpactCents: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = "$filteredCount transactions",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Net impact ${formatSignedMoney(filteredImpactCents)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActivityDateHeader(dateLabel: String) {
    Text(
        text = dateLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun ActivityTransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val timeFormatter = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }
    val merchantName = transaction.displayMerchantName
        ?: transaction.merchantRaw
        ?: transaction.sourceInstitution
        ?: treatmentLabel(transaction.accountingTreatment)
    val categoryLabel = activityCategoryLabel(transaction)
    val categoryColor = spendingCategoryColor(categoryLabel, 0)
    val merchantColor = merchantAccentColor(merchantName, transaction.id.toInt())
    val sourceLabel = activitySourceLabel(transaction)
    val needsReview = transaction.reviewStatus == "NEEDS_REVIEW"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                color = merchantColor.copy(alpha = 0.14f),
                contentColor = merchantColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = categoryGlyph(categoryLabel),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = merchantName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    ActivitySmallBadge(
                        text = categoryLabel,
                        color = categoryColor
                    )
                    if (needsReview) {
                        ActivitySmallBadge(
                            text = "Needs review",
                            color = Color(0xFFFFA044)
                        )
                    }
                }

                Text(
                    text = listOf(
                        timeFormatter.format(Date(transaction.occurredAtEpochMs)),
                        sourceLabel
                    ).filter { it.isNotBlank() }.joinToString(" - "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = activityAmountLabel(transaction),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = ">",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActivitySmallBadge(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

fun activityDateHeaderLabel(epochMs: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(epochMs))
}

fun activityCategoryLabel(transaction: TransactionEntity): String {
    val category = transaction.categoryName
        ?.trim()
        ?.takeIf { !isVirtualUncategorizedCategory(it) && !it.equals("General", ignoreCase = true) }
    return category ?: treatmentLabel(transaction.accountingTreatment)
}

fun activitySourceLabel(transaction: TransactionEntity): String {
    return listOfNotNull(
        transaction.sourceInstitution?.takeIf { it.isNotBlank() },
        transaction.accountHint?.takeIf { it.isNotBlank() }
    ).joinToString(" - ").ifBlank { "SMS" }
}

fun activityAmountLabel(transaction: TransactionEntity): String {
    val amount = "${'$'}${"%.2f".format(Locale.US, transaction.amountCents / 100.0)}"
    return when (transaction.accountingTreatment) {
        TransactionTreatments.INCOME -> "+$amount"
        TransactionTreatments.REFUND,
        TransactionTreatments.REIMBURSEMENT -> "-$amount"
        else -> amount
    }
}

@Composable
fun TransactionSummaryCard(
    totalCount: Int,
    expenseCount: Int,
    needsReviewCount: Int,
    excludedCount: Int
) {
    StatStrip(
        items = listOf(
            StatStripItem("Total", totalCount.toString(), emphasized = true),
            StatStripItem("Expenses", expenseCount.toString()),
            StatStripItem("Review", needsReviewCount.toString()),
            StatStripItem("Other", excludedCount.toString())
        )
    )
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
    val spendingImpact = TransactionTreatments.spendingImpactCents(
        treatment = transaction.accountingTreatment,
        excludedFromSpending = transaction.excludedFromSpending,
        amountCents = transaction.amountCents
    )

    val sourceText = listOfNotNull(
        transaction.sourceInstitution,
        transaction.accountHint?.let { "Hint $it" },
        "Review ${transaction.reviewStatus}",
        "${"%.0f".format(transaction.parseConfidence * 100)}% confidence"
    ).joinToString(" - ")

    LedgerListRow(
        title = transaction.displayMerchantName
            ?: transaction.sourceInstitution
            ?: treatmentLabel(transaction.accountingTreatment),
        supportingText = formatter.format(Date(transaction.occurredAtEpochMs)),
        metadataText = sourceText,
        pillText = treatmentLabel(transaction.accountingTreatment),
        trailingText = "$${"%.2f".format(amount)}",
        trailingSupportingText = if (spendingImpact == 0L) {
            "Outside spending"
        } else {
            "Impact ${formatSignedMoney(spendingImpact)}"
        },
        leadingText = transaction.displayMerchantName
            ?.take(1)
            ?: transaction.sourceInstitution?.take(1)
            ?: "$",
        onClick = onClick
    )
    return

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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
    allTransactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onUpdateTransactionType: (String, Boolean) -> Unit,
    onUpdateReviewStatus: (String) -> Unit,
    onUpdateExcludedFromSpending: (Boolean) -> Unit,
    onUpdateMerchant: (String) -> Unit,
    onUpdateSpendingAttribution: (String, String) -> Unit,
    onApplyMerchantToSimilar: (String, String, (Int) -> Unit) -> Unit,
    onApplyCurrentClassificationToSimilar: (String, (Int) -> Unit) -> Unit,
    onApplyCategoryToSimilar: (String, String, (Int) -> Unit) -> Unit,
    onOpenParserRuleEditor: (MerchantAliasRuleDraft) -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    var showTechnicalDetails by remember(transaction.id) {
        mutableStateOf(false)
    }

    var showOriginalSms by remember(transaction.id) {
        mutableStateOf(false)
    }

    var showCategoryPicker by remember(transaction.id) {
        mutableStateOf(false)
    }

    var saveMessage by remember(transaction.id) {
        mutableStateOf<String?>(null)
    }

    var merchantDraft by remember(transaction.id, transaction.displayMerchantName, transaction.merchantRaw) {
        mutableStateOf(transaction.displayMerchantName ?: transaction.merchantRaw ?: "")
    }

    var categoryDraft by remember(transaction.id, transaction.categoryName) {
        mutableStateOf(transaction.categoryName.orEmpty())
    }

    var spendingMerchantDraft by remember(transaction.id, transaction.spendingMerchantName) {
        mutableStateOf(transaction.spendingMerchantName.orEmpty())
    }

    var treatmentDraft by remember(transaction.id, transaction.accountingTreatment) {
        mutableStateOf(transaction.accountingTreatment)
    }

    var excludedDraft by remember(transaction.id, transaction.excludedFromSpending) {
        mutableStateOf(transaction.excludedFromSpending)
    }

    var reviewStatusDraft by remember(transaction.id, transaction.reviewStatus) {
        mutableStateOf(transaction.reviewStatus)
    }

    val showSpendingAttributionByDefault = transaction.accountingTreatment in setOf(
        TransactionTreatments.PERSON_TO_PERSON,
        TransactionTreatments.TRANSFER,
        TransactionTreatments.REIMBURSEMENT
    ) || !transaction.spendingMerchantName.isNullOrBlank()

    val categoryOptions = remember(allTransactions) {
        transactionCategoryOptions(allTransactions)
    }

    if (showCategoryPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TransactionCategoryPickerSheet(
                options = categoryOptions,
                selectedCategory = categoryDraft,
                onSelectCategory = { category ->
                    categoryDraft = category
                    showCategoryPicker = false
                },
                onCreateCategory = { category ->
                    categoryDraft = category.trim()
                    showCategoryPicker = false
                },
                onClearCategory = {
                    categoryDraft = ""
                    showCategoryPicker = false
                },
                onCancel = {
                    showCategoryPicker = false
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(transaction.displayMerchantName ?: transaction.merchantRaw ?: "Transaction") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                TransactionDetailHeaderCard(
                    transaction = transaction,
                    formatter = formatter
                )
            }

            item {
                TransactionFactsCard(
                    transaction = transaction,
                    rawAlert = rawAlert,
                    formatter = formatter,
                    showOriginalSms = showOriginalSms,
                    onToggleOriginalSms = { showOriginalSms = !showOriginalSms }
                )
            }

            item {
                EditableTransactionDetailsCard(
                    transaction = transaction,
                    merchantDraft = merchantDraft,
                    categoryDraft = categoryDraft,
                    treatmentDraft = treatmentDraft,
                    excludedDraft = excludedDraft,
                    reviewStatusDraft = reviewStatusDraft,
                    onMerchantChange = { merchantDraft = it },
                    onChooseCategory = { showCategoryPicker = true },
                    onTreatmentChange = { treatment ->
                        treatmentDraft = treatment
                        excludedDraft = TransactionTreatments.defaultExcludedFromSpending(treatment)
                    },
                    onExcludedChange = { excludedDraft = it },
                    onReviewStatusChange = { reviewStatusDraft = it },
                    onReviewStatusAction = {
                        val nextStatus = if (reviewStatusDraft == "NEEDS_REVIEW") {
                            "REVIEWED"
                        } else {
                            "NEEDS_REVIEW"
                        }
                        reviewStatusDraft = nextStatus
                        onUpdateReviewStatus(nextStatus)
                        saveMessage = if (nextStatus == "REVIEWED") {
                            "Marked reviewed"
                        } else {
                            "Marked as needs review"
                        }
                    }
                )
            }

            if (showSpendingAttributionByDefault) {
                item {
                    SpendingAttributionEditorCard(
                        categoryDraft = categoryDraft,
                        spendingMerchantDraft = spendingMerchantDraft,
                        onChooseCategory = { showCategoryPicker = true },
                        onSpendingMerchantChange = { spendingMerchantDraft = it }
                    )
                }
            }

            if (
                transaction.accountingTreatment in TransactionTreatments.movementTreatments ||
                transaction.parserNotes.orEmpty().contains("Zelle", ignoreCase = true)
            ) {
                item {
                    TransferResolutionCard(
                        transaction = transaction,
                        onMarkPersonalTransfer = {
                            val treatment = if (transaction.accountingTreatment == TransactionTreatments.PERSON_TO_PERSON) {
                                TransactionTreatments.PERSON_TO_PERSON
                            } else {
                                TransactionTreatments.TRANSFER
                            }
                            treatmentDraft = treatment
                            excludedDraft = true
                            categoryDraft = "Transfer"
                            spendingMerchantDraft = ""
                            reviewStatusDraft = "REVIEWED"
                            onUpdateTransactionType(treatment, true)
                            onUpdateSpendingAttribution("Transfer", "")
                            onUpdateReviewStatus("REVIEWED")
                            saveMessage = "Saved as personal transfer"
                        },
                        onMarkReimbursement = { category, spendingMerchant ->
                            treatmentDraft = TransactionTreatments.REIMBURSEMENT
                            excludedDraft = false
                            categoryDraft = category
                            spendingMerchantDraft = spendingMerchant
                            reviewStatusDraft = "REVIEWED"
                            onUpdateTransactionType(TransactionTreatments.REIMBURSEMENT, false)
                            onUpdateSpendingAttribution(category, spendingMerchant)
                            onUpdateReviewStatus("REVIEWED")
                            saveMessage = "Saved as reimbursement"
                        },
                        onMarkExpense = { category, spendingMerchant ->
                            treatmentDraft = TransactionTreatments.EXPENSE
                            excludedDraft = false
                            categoryDraft = category
                            spendingMerchantDraft = spendingMerchant
                            reviewStatusDraft = "REVIEWED"
                            onUpdateTransactionType(TransactionTreatments.EXPENSE, false)
                            onUpdateSpendingAttribution(category, spendingMerchant)
                            onUpdateReviewStatus("REVIEWED")
                            saveMessage = "Saved as shared expense"
                        },
                        onMarkIncomeGift = {
                            treatmentDraft = TransactionTreatments.INCOME
                            excludedDraft = true
                            categoryDraft = "Income"
                            spendingMerchantDraft = ""
                            reviewStatusDraft = "REVIEWED"
                            onUpdateTransactionType(TransactionTreatments.INCOME, true)
                            onUpdateSpendingAttribution("Income", "")
                            onUpdateReviewStatus("REVIEWED")
                            saveMessage = "Saved as income or gift"
                        }
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        if (merchantDraft != (transaction.displayMerchantName ?: transaction.merchantRaw ?: "")) {
                            onUpdateMerchant(merchantDraft)
                        }
                        if (
                            categoryDraft != transaction.categoryName.orEmpty() ||
                            spendingMerchantDraft != transaction.spendingMerchantName.orEmpty()
                        ) {
                            onUpdateSpendingAttribution(categoryDraft, spendingMerchantDraft)
                        }
                        if (
                            treatmentDraft != transaction.accountingTreatment ||
                            excludedDraft != transaction.excludedFromSpending
                        ) {
                            onUpdateTransactionType(treatmentDraft, excludedDraft)
                        }
                        if (reviewStatusDraft != transaction.reviewStatus) {
                            onUpdateReviewStatus(reviewStatusDraft)
                        }
                        saveMessage = "Saved changes"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save changes")
                }
            }

            saveMessage?.let { message ->
                item {
                    InlineInfoPanel(
                        title = "Saved",
                        body = message
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showTechnicalDetails = !showTechnicalDetails },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showTechnicalDetails) "Hide advanced details" else "Advanced details")
                }
            }

            if (showTechnicalDetails) {
                if (!showSpendingAttributionByDefault) {
                    item {
                        SpendingAttributionEditorCard(
                            categoryDraft = categoryDraft,
                            spendingMerchantDraft = spendingMerchantDraft,
                            onChooseCategory = { showCategoryPicker = true },
                            onSpendingMerchantChange = { spendingMerchantDraft = it }
                        )
                    }
                }

                item {
                    TransactionTechnicalDetailsCard(
                        transaction = transaction,
                        rawAlert = rawAlert,
                        formatter = formatter
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
                        onClick = {
                            onOpenParserRuleEditor(
                                buildMerchantAliasDraftForTransaction(
                                    transaction = transaction,
                                    rawAlert = rawAlert,
                                    allTransactions = allTransactions,
                                    includeCategory = false,
                                    includeTreatment = false
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fix parser rule")
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionDetailHeaderCard(
    transaction: TransactionEntity,
    formatter: SimpleDateFormat
) {
    val merchant = transaction.displayMerchantName
        ?: transaction.merchantRaw
        ?: "Unknown merchant"
    val category = transaction.categoryName?.takeIf { it.isNotBlank() }
    val amount = transaction.amountCents / 100.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = merchant.take(1).uppercase(Locale.US),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = merchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = formatter.format(Date(transaction.occurredAtEpochMs)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!category.isNullOrBlank()) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    text = "$${"%.2f".format(amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = treatmentLabel(transaction.accountingTreatment),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.reviewStatus == "NEEDS_REVIEW") {
                    Text(
                        text = "Needs review",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionFactsCard(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity?,
    formatter: SimpleDateFormat,
    showOriginalSms: Boolean,
    onToggleOriginalSms: () -> Unit
) {
    val sourceText = listOfNotNull(
        transaction.sourceInstitution,
        transaction.accountHint?.let { "Account $it" }
    ).joinToString(" - ").ifBlank { transaction.sourceKey }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Transaction facts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            DetailRow("Amount", "$${"%.2f".format(transaction.amountCents / 100.0)}")
            DetailRow("Date", formatter.format(Date(transaction.occurredAtEpochMs)))
            DetailRow("Source / Account", sourceText)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "Original SMS",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                TextButton(onClick = onToggleOriginalSms) {
                    Text(if (showOriginalSms) "Hide" else "View")
                }
            }

            if (showOriginalSms) {
                Text(
                    text = rawAlert?.combinedText ?: "Original SMS was not found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EditableTransactionDetailsCard(
    transaction: TransactionEntity,
    merchantDraft: String,
    categoryDraft: String,
    treatmentDraft: String,
    excludedDraft: Boolean,
    reviewStatusDraft: String,
    onMerchantChange: (String) -> Unit,
    onChooseCategory: () -> Unit,
    onTreatmentChange: (String) -> Unit,
    onExcludedChange: (Boolean) -> Unit,
    onReviewStatusChange: (String) -> Unit,
    onReviewStatusAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Edit details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )

            OutlinedTextField(
                value = merchantDraft,
                onValueChange = onMerchantChange,
                label = { Text("Merchant") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            LedgerListRow(
                title = "Category",
                supportingText = if (categoryDraft.isBlank()) "Not assigned" else categoryDraft,
                trailingText = "Choose",
                leadingText = "C",
                onClick = onChooseCategory
            )

            Text(
                text = "Accounting treatment",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            TreatmentSelector(
                selectedTreatment = treatmentDraft,
                onTreatmentSelected = onTreatmentChange,
                treatments = listOf(
                    TransactionTreatments.EXPENSE,
                    TransactionTreatments.INCOME,
                    TransactionTreatments.TRANSFER,
                    TransactionTreatments.REFUND,
                    TransactionTreatments.REIMBURSEMENT,
                    TransactionTreatments.CREDIT_CARD_PAYMENT,
                    TransactionTreatments.PERSON_TO_PERSON,
                    TransactionTreatments.UNKNOWN
                )
            )

            Text(
                text = "Counts toward spending",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilterButton(
                    label = "Count",
                    selected = !excludedDraft,
                    onClick = { onExcludedChange(false) },
                    modifier = Modifier.weight(1f)
                )
                TransactionFilterButton(
                    label = "Outside",
                    selected = excludedDraft,
                    onClick = { onExcludedChange(true) },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Review status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilterButton(
                    label = "Reviewed",
                    selected = reviewStatusDraft != "NEEDS_REVIEW",
                    onClick = { onReviewStatusChange("REVIEWED") },
                    modifier = Modifier.weight(1f)
                )
                TransactionFilterButton(
                    label = "Needs review",
                    selected = reviewStatusDraft == "NEEDS_REVIEW",
                    onClick = { onReviewStatusChange("NEEDS_REVIEW") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = onReviewStatusAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (reviewStatusDraft == "NEEDS_REVIEW") {
                        "Mark reviewed"
                    } else {
                        "Mark as needs review"
                    }
                )
            }

            val spendingImpact = TransactionTreatments.spendingImpactCents(
                treatment = treatmentDraft,
                excludedFromSpending = excludedDraft,
                amountCents = transaction.amountCents
            )
            Text(
                text = if (spendingImpact == 0L) {
                    "Current spending impact: outside spending"
                } else {
                    "Current spending impact: ${formatSignedMoney(spendingImpact)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpendingAttributionEditorCard(
    categoryDraft: String,
    spendingMerchantDraft: String,
    onChooseCategory: () -> Unit,
    onSpendingMerchantChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Spending attribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = "Use this when a person-to-person item should reduce or count toward a specific category or bill.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LedgerListRow(
                title = "Category",
                supportingText = if (categoryDraft.isBlank()) "Not assigned" else categoryDraft,
                trailingText = "Choose",
                leadingText = "C",
                onClick = onChooseCategory
            )
            OutlinedTextField(
                value = spendingMerchantDraft,
                onValueChange = onSpendingMerchantChange,
                label = { Text("Spending merchant or bill (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun TransactionCategoryPickerSheet(
    options: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onCreateCategory: (String) -> Unit,
    onClearCategory: () -> Unit,
    onCancel: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("") }
    val customCategoryIsSelectable = remember(customCategory) {
        customCategory.isNotBlank() &&
                !isVirtualUncategorizedCategory(customCategory) &&
                !customCategory.equals("General", ignoreCase = true)
    }

    val visibleOptions = remember(options, searchText) {
        val query = searchText.trim().lowercase(Locale.US)
        if (query.isBlank()) {
            options
        } else {
            options.filter { it.lowercase(Locale.US).contains(query) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Select a category",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search categories") },
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = visibleOptions,
                key = { it }
            ) { option ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCategory(option) },
                    color = if (option.equals(selectedCategory, ignoreCase = true)) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        if (option.equals(selectedCategory, ignoreCase = true)) {
                            Text(
                                text = "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = customCategory,
            onValueChange = { customCategory = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Create new category") },
            singleLine = true
        )

        OutlinedButton(
            onClick = { onCreateCategory(customCategory) },
            enabled = customCategoryIsSelectable,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create and select")
        }

        OutlinedButton(
            onClick = onClearCategory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear category")
        }

        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

fun transactionCategoryOptions(transactions: List<TransactionEntity>): List<String> {
    val presets = listOf(
        "Groceries",
        "Restaurants",
        "Shopping",
        "Subscriptions",
        "Bills & Utilities",
        "Gas",
        "Healthcare",
        "Travel",
        "Entertainment",
        "Charity",
        "Home",
        "Personal Care",
        "Insurance",
        "Other"
    )
    val existing = transactions.mapNotNull { it.categoryName?.takeIf { category -> category.isNotBlank() } }
    return (presets + existing)
        .filterNot {
            isVirtualUncategorizedCategory(it) ||
                    it.equals("General", ignoreCase = true)
        }
        .distinctBy { it.lowercase(Locale.US) }
        .sortedWith(compareBy<String> { category ->
            presets.indexOfFirst { it.equals(category, ignoreCase = true) }.let { if (it == -1) Int.MAX_VALUE else it }
        }.thenBy { it.lowercase(Locale.US) })
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
    val spendingMerchant = transaction.spendingMerchantName?.takeIf { it.isNotBlank() }
    val category = if (transaction.categoryName.isNullOrBlank()) {
        "Not assigned"
    } else {
        transaction.categoryName
    }
    val spendingImpact = TransactionTreatments.spendingImpactCents(
        treatment = transaction.accountingTreatment,
        excludedFromSpending = transaction.excludedFromSpending,
        amountCents = transaction.amountCents
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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

            if (!spendingMerchant.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Spending merchant: $spendingMerchant",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (spendingImpact != 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Spending impact: ${formatSignedMoney(spendingImpact)}",
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
            DetailRow("Display merchant", transaction.displayMerchantName ?: "Not set")
            DetailRow("Spending merchant", transaction.spendingMerchantName ?: "Not set")
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
    transaction: TransactionEntity,
    onMarkPersonalTransfer: () -> Unit,
    onMarkReimbursement: (String, String) -> Unit,
    onMarkExpense: (String, String) -> Unit,
    onMarkIncomeGift: () -> Unit
) {
    var categoryText by remember(transaction.id, transaction.categoryName) {
        mutableStateOf(transaction.categoryName ?: "Bills & Utilities")
    }
    var spendingMerchantText by remember(transaction.id, transaction.spendingMerchantName) {
        mutableStateOf(transaction.spendingMerchantName ?: "")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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

            OutlinedTextField(
                value = categoryText,
                onValueChange = { categoryText = it },
                label = { Text("Spending category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = spendingMerchantText,
                onValueChange = { spendingMerchantText = it },
                label = { Text("Spending merchant or bill (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    onClick = {
                        onMarkReimbursement(
                            categoryText.ifBlank { "Other" },
                            spendingMerchantText
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reimbursement received")
                }

                OutlinedButton(
                    onClick = {
                        onMarkExpense(
                            categoryText.ifBlank { "Other" },
                            spendingMerchantText
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Shared expense paid")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onMarkIncomeGift,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Income or Gift")
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
                    onClick = { onUpdateTransactionType(TransactionTreatments.REFUND, false) },
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

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = { onUpdateTransactionType(TransactionTreatments.REIMBURSEMENT, false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reimbursement")
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

            val spendingImpact = TransactionTreatments.spendingImpactCents(
                treatment = transaction.accountingTreatment,
                excludedFromSpending = transaction.excludedFromSpending,
                amountCents = transaction.amountCents
            )
            val spendingState = if (spendingImpact == 0L) {
                "Outside spending"
            } else {
                "Spending impact ${formatSignedMoney(spendingImpact)}"
            }

            Text(
                text = "Current: ${treatmentLabel(transaction.accountingTreatment)} - ${transaction.reviewStatus} - $spendingState",
                style = MaterialTheme.typography.labelSmall
            )

            if (!transaction.categoryName.isNullOrBlank()) {
                Text(
                    text = "Category: ${transaction.categoryName}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun MerchantCorrectionCard(
    transaction: TransactionEntity,
    onUpdateMerchant: (String) -> Unit,
    onOpenParserRuleEditor: (() -> Unit)? = null
) {
    var merchantText by remember(transaction.id, transaction.displayMerchantName, transaction.merchantRaw) {
        mutableStateOf(
            transaction.displayMerchantName
                ?: transaction.merchantRaw
                ?: ""
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Merchant / Payee",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Edit display name changes this transaction only. Use parser rules when the SMS wording should be recognized in the future.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("Save Display Name")
            }

            if (onOpenParserRuleEditor != null) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onOpenParserRuleEditor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fix Parser Rule")
                }
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
    onApplyCategoryToSimilar: (String, String, (Int) -> Unit) -> Unit
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

    var resultText by remember(transaction.id) {
        mutableStateOf("")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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

            Button(
                onClick = {
                    resultText = "Applying category to similar transactions..."

                    onApplyCategoryToSimilar(
                        matchPhrase,
                        categoryText
                    ) { updatedCount ->
                        resultText = "Updated category on $updatedCount similar transactions."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Category to Similar")
            }

            Spacer(modifier = Modifier.height(8.dp))

            val currentImpact = TransactionTreatments.spendingImpactCents(
                treatment = transaction.accountingTreatment,
                excludedFromSpending = transaction.excludedFromSpending,
                amountCents = transaction.amountCents
            )
            Text(
                text = "Current classification: ${treatmentLabel(transaction.accountingTreatment)} - ${transaction.reviewStatus} - ${
                    if (currentImpact == 0L) "Outside spending" else "Impact ${formatSignedMoney(currentImpact)}"
                }",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Current category: ${
                    transaction.categoryName ?: "Not assigned"
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
    var spendingMerchantText by remember(transaction.id, transaction.spendingMerchantName) {
        mutableStateOf(transaction.spendingMerchantName ?: "")
    }

    val presets = listOf(
        "Groceries",
        "Restaurants",
        "Gas",
        "Shopping",
        "Bills & Utilities",
        "Subscriptions",
        "Healthcare",
        "Travel",
        "Charity",
        "Transfer",
        "Other"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
                                categoryText = preset
                                onUpdateCategory(categoryText, spendingMerchantText)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(preset)
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
                value = spendingMerchantText,
                onValueChange = { spendingMerchantText = it },
                label = { Text("Spending merchant or bill (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onUpdateCategory(categoryText, spendingMerchantText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Spending Attribution")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Current: ${
                    transaction.categoryName ?: "Not assigned"
                }",
                style = MaterialTheme.typography.labelSmall
            )

            if (!transaction.spendingMerchantName.isNullOrBlank()) {
                Text(
                    text = "Spending merchant: ${transaction.spendingMerchantName}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingSummaryScreen(
    transactions: List<TransactionEntity>,
    onNavigate: (AppScreen) -> Unit,
    onQuickActions: () -> Unit,
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

    val previousMonthStart = remember(selectedMonthStart) {
        getPreviousMonthStartEpochMs(selectedMonthStart)
    }

    val includedExpensesForMonth = remember(transactions, selectedMonthStart, selectedMonthEnd) {
        expenseTransactionsForRange(
            transactions = transactions,
            startEpochMs = selectedMonthStart,
            endEpochMs = selectedMonthEnd
        )
    }

    val previousMonthExpenses = remember(transactions, previousMonthStart, selectedMonthStart) {
        expenseTransactionsForRange(
            transactions = transactions,
            startEpochMs = previousMonthStart,
            endEpochMs = selectedMonthStart
        )
    }

    val totalExpenseCents = remember(includedExpensesForMonth) {
        includedExpensesForMonth.sumOf {
            TransactionTreatments.spendingImpactCents(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending,
                amountCents = it.amountCents
            )
        }
    }

    val previousMonthSpendingCents = remember(previousMonthExpenses) {
        previousMonthExpenses.sumOf {
            TransactionTreatments.spendingImpactCents(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending,
                amountCents = it.amountCents
            )
        }
    }

    val grossExpenseCents = remember(includedExpensesForMonth) {
        includedExpensesForMonth
            .filter { it.accountingTreatment == TransactionTreatments.EXPENSE }
            .sumOf { it.amountCents }
    }

    val offsetCents = remember(includedExpensesForMonth) {
        includedExpensesForMonth.sumOf {
            val impact = TransactionTreatments.spendingImpactCents(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending,
                amountCents = it.amountCents
            )
            if (impact < 0) -impact else 0
        }
    }

    val unassignedCount = remember(includedExpensesForMonth) {
        includedExpensesForMonth.count { isVirtualUncategorizedCategory(it.categoryName) }
    }

    val unassignedAmountCents = remember(includedExpensesForMonth) {
        includedExpensesForMonth
            .filter { isVirtualUncategorizedCategory(it.categoryName) }
            .sumOf {
                TransactionTreatments.spendingImpactCents(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending,
                    amountCents = it.amountCents
                )
            }
    }

    val categorySummaries = remember(includedExpensesForMonth) {
        categorySpendSummaries(includedExpensesForMonth)
    }

    val categoryBarTotalCents = remember(categorySummaries) {
        categorySummaries.sumOf { kotlin.math.abs(it.amountCents) }
    }

    val topMerchantSummaries = remember(includedExpensesForMonth) {
        merchantSummaries(includedExpensesForMonth)
            .filter { it.spendingAmountCents != 0L }
            .take(5)
    }

    val trendText = remember(totalExpenseCents, previousMonthSpendingCents) {
        spendingTrendText(totalExpenseCents, previousMonthSpendingCents)
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
                    val category = displayCategoryName(it.categoryName)

                    category == selected.categoryName
                }
                .sortedByDescending { it.occurredAtEpochMs }
        }
    }

    BackHandler(enabled = selectedCategorySummary != null) {
        selectedCategorySummary = null
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
        LedgerAppScaffold(
            title = "Spending",
            activeScreen = AppScreen.SUMMARY,
            onNavigate = onNavigate,
            onQuickActions = onQuickActions,
            onBack = onBack
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
                        grossExpenseCents = grossExpenseCents,
                        offsetCents = offsetCents,
                        includedExpenseCount = includedExpensesForMonth.size,
                        trendText = trendText
                    )
                }

                if (unassignedCount > 0) {
                    item {
                        UnassignedSpendingCallout(
                            transactionCount = unassignedCount,
                            amountCents = unassignedAmountCents,
                            onClick = {
                                onNavigate(AppScreen.REVIEW_QUEUE)
                            }
                        )
                    }
                }

                item {
                    SpendingCategoryBreakdownPanel(
                        categorySummaries = categorySummaries,
                        chartSummaries = categorySummaries,
                        totalExpenseCents = totalExpenseCents,
                        totalActivityCents = categoryBarTotalCents,
                        onCategorySelected = { summary ->
                            selectedCategorySummary = summary
                        }
                    )
                }

                item {
                    TopMerchantsPanel(
                        merchants = topMerchantSummaries,
                        onMerchantSelected = { merchantName ->
                            val transaction = includedExpensesForMonth.firstOrNull {
                                merchantSummaryName(it).equals(merchantName, ignoreCase = true)
                            }
                            if (transaction != null) {
                                onTransactionSelected(transaction)
                            }
                        }
                        )
                }
            }
        }
    }
}

@Composable
fun SpendingSummaryTopCard(
    totalExpenseCents: Long,
    grossExpenseCents: Long,
    offsetCents: Long,
    includedExpenseCount: Int,
    trendText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Total spending",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = formatSignedMoney(totalExpenseCents),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Text(
                text = trendText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = if (offsetCents > 0) {
                    "$includedExpenseCount transactions - ${formatSignedMoney(grossExpenseCents)} gross · ${formatSignedMoney(offsetCents)} offsets"
                } else {
                    "$includedExpenseCount spending transactions"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpendingCategoryBreakdownPanel(
    categorySummaries: List<CategorySpendSummary>,
    chartSummaries: List<CategorySpendSummary>,
    totalExpenseCents: Long,
    totalActivityCents: Long,
    onCategorySelected: (CategorySpendSummary) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "Spending by category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )

                Text(
                    text = "${categorySummaries.size} categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SpendingDonutChart(
                summaries = chartSummaries,
                centerText = formatSignedMoney(totalExpenseCents),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .padding(horizontal = 12.dp)
            )

            if (categorySummaries.isEmpty()) {
                Text(
                    text = "Assign categories to see your spending breakdown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                categorySummaries.forEachIndexed { index, summary ->
                    SpendingCategoryListRow(
                        summary = summary,
                        totalAmountCents = totalActivityCents,
                        accentColor = spendingCategoryColor(summary.categoryName, index),
                        onClick = { onCategorySelected(summary) }
                    )

                    if (index != categorySummaries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SpendingDonutChart(
    summaries: List<CategorySpendSummary>,
    centerText: String,
    modifier: Modifier = Modifier
) {
    val slices = summaries
        .filter { kotlin.math.abs(it.amountCents) > 0L }
        .take(7)
    val total = slices.sumOf { kotlin.math.abs(it.amountCents) }

    Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(size.width, size.height) * 0.78f
            val strokeWidth = diameter * 0.18f
            val topLeft = androidx.compose.ui.geometry.Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            if (total <= 0L) {
                drawArc(
                    color = Color(0xFFE9EEF0),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            } else {
                var startAngle = -90f
                slices.forEachIndexed { index, summary ->
                    val sweep = (kotlin.math.abs(summary.amountCents).toFloat() / total.toFloat()) * 360f
                    drawArc(
                        color = spendingCategoryColor(summary.categoryName, index),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 1.2f).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }

        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                text = centerText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                text = "Total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpendingCategoryListRow(
    summary: CategorySpendSummary,
    totalAmountCents: Long,
    accentColor: Color,
    onClick: () -> Unit
) {
    val share = if (totalAmountCents > 0) {
        kotlin.math.abs(summary.amountCents).toFloat() / totalAmountCents.toFloat()
    } else {
        0f
    }
    val offsetText = if (summary.refundOffsetCents > 0) {
        " - ${formatSignedMoney(-summary.refundOffsetCents)} offsets"
    } else {
        ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    contentColor = accentColor,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = categoryGlyph(summary.categoryName),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }

                Column {
                    Text(
                        text = summary.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        text = "${summary.transactionCount} transactions$offsetText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    text = formatSignedMoney(summary.amountCents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = "${"%.1f".format(share * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .height(5.dp)
                    .background(
                        color = accentColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onPreviousMonth) {
                Text("<")
            }

            Text(
                text = formatMonthYear(monthStartEpochMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = onCurrentMonth) {
                Text("This month")
            }

            OutlinedButton(onClick = onNextMonth) {
                Text(">")
            }
        }
    }
}

@Composable
fun UnassignedSpendingCallout(
    transactionCount: Int,
    amountCents: Long,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color(0xFFFFF8E8),
        contentColor = Color(0xFF4F3411),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFFFFE4AD),
                contentColor = Color(0xFFC17500),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Uncategorized spending",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = "$transactionCount transactions need a category",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    text = formatSignedMoney(amountCents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = "Review",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TopMerchantsPanel(
    merchants: List<MerchantSummary>,
    onMerchantSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Top merchants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )

            if (merchants.isEmpty()) {
                Text(
                    text = "No merchant spending for this month yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                merchants.forEachIndexed { index, merchant ->
                    TopMerchantRow(
                        merchant = merchant,
                        accentColor = merchantAccentColor(merchant.merchantName, index),
                        onClick = { onMerchantSelected(merchant.merchantName) }
                    )
                    if (index != merchants.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun TopMerchantRow(
    merchant: MerchantSummary,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Surface(
            color = accentColor.copy(alpha = 0.14f),
            contentColor = accentColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(
                text = merchant.merchantName.trim().take(1).uppercase(Locale.US).ifBlank { "?" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = merchant.merchantName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    merchant.categoryName?.takeIf { !isVirtualUncategorizedCategory(it) },
                    "${merchant.transactionCount} transactions"
                ).joinToString(" - "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Text(
            text = formatSignedMoney(merchant.spendingAmountCents),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}

fun spendingTrendText(currentCents: Long, previousCents: Long): String {
    if (currentCents == 0L && previousCents == 0L) {
        return "No spending activity for this month yet"
    }
    if (previousCents == 0L) {
        return "No previous month spending to compare"
    }

    val difference = currentCents - previousCents
    if (difference == 0L) {
        return "Same as previous month"
    }

    val percentage = kotlin.math.abs(difference).toDouble() / kotlin.math.abs(previousCents).toDouble() * 100.0
    return if (difference < 0) {
        "Down ${"%.1f".format(percentage)}% from previous month"
    } else {
        "Up ${"%.1f".format(percentage)}% from previous month"
    }
}

fun spendingCategoryColor(categoryName: String, index: Int): Color {
    val normalized = categoryName.lowercase(Locale.US)
    return when {
        "uncategorized" in normalized || "unassigned" in normalized -> Color(0xFFC9D1D8)
        "grocer" in normalized -> Color(0xFF43B86B)
        "restaurant" in normalized || "dining" in normalized -> Color(0xFFFFA044)
        "subscription" in normalized -> Color(0xFF9B6CF3)
        "transport" in normalized || "gas" in normalized -> Color(0xFF5D9CEC)
        "bill" in normalized || "utilit" in normalized -> Color(0xFFFFCE58)
        "shopping" in normalized -> Color(0xFF4FC3C7)
        "health" in normalized -> Color(0xFFE85D9A)
        "travel" in normalized -> Color(0xFF7FB3FF)
        "home" in normalized -> Color(0xFF5CC7A9)
        else -> listOf(
            Color(0xFF43B86B),
            Color(0xFFFFA044),
            Color(0xFF9B6CF3),
            Color(0xFF5D9CEC),
            Color(0xFFFFCE58),
            Color(0xFF4FC3C7),
            Color(0xFFC9D1D8)
        )[index % 7]
    }
}

fun merchantAccentColor(
    merchantName: String,
    index: Int
): Color {
    if (isVirtualUncategorizedCategory(merchantName)) {
        return Color(0xFFC9D1D8)
    }
    val palette = listOf(
        Color(0xFF21A66B),
        Color(0xFFFF8A3D),
        Color(0xFF7C5CFF),
        Color(0xFF3E8BFF),
        Color(0xFFE85D9A),
        Color(0xFF00A7A7),
        Color(0xFFFFB33F),
        Color(0xFF6E7BFF),
        Color(0xFF2DAE73),
        Color(0xFFE45757),
        Color(0xFF5A9BD5),
        Color(0xFF8A63D2)
    )
    return palette[index.mod(palette.size)]
}

fun categoryGlyph(categoryName: String): String {
    val normalized = categoryName.lowercase(Locale.US)
    return when {
        "uncategorized" in normalized || "unassigned" in normalized -> "?"
        "grocer" in normalized -> "G"
        "restaurant" in normalized || "dining" in normalized -> "R"
        "subscription" in normalized -> "S"
        "transport" in normalized || "gas" in normalized -> "T"
        "bill" in normalized || "utilit" in normalized -> "B"
        "shopping" in normalized -> "S"
        "health" in normalized -> "H"
        "travel" in normalized -> "T"
        "home" in normalized -> "H"
        else -> categoryName.trim().take(1).uppercase(Locale.US).ifBlank { "O" }
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
    val isUncategorizedDrilldown = isVirtualUncategorizedCategory(summary.categoryName)
    val merchantGroups = remember(transactions) {
        transactions
            .groupBy {
                it.spendingMerchantName?.takeIf { name -> name.isNotBlank() }
                    ?: it.displayMerchantName
                    ?: it.merchantRaw
                    ?: "Unknown merchant"
            }
            .map { (merchant, group) ->
                Triple(
                    merchant,
                    group.size,
                    group.sumOf {
                        TransactionTreatments.spendingImpactCents(
                            treatment = it.accountingTreatment,
                            excludedFromSpending = it.excludedFromSpending,
                            amountCents = it.amountCents
                        )
                    }
                )
            }
            .sortedByDescending { kotlin.math.abs(it.third) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Category Detail") },
                navigationIcon = {}
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
                    amountText = formatSignedMoney(summary.amountCents)
                )
            }

            item {
                if (!isUncategorizedDrilldown) {
                    Text(
                        text = "Merchants",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (transactions.isEmpty()) {
                item {
                    EmptySectionText("No transactions found for this category.")
                }
            } else {
                if (!isUncategorizedDrilldown) {
                    items(
                        items = merchantGroups,
                        key = { it.first }
                    ) { (merchantName, transactionCount, amountCents) ->
                        LedgerListRow(
                            title = merchantName,
                            supportingText = "$transactionCount transactions",
                            trailingText = formatSignedMoney(amountCents),
                            leadingText = merchantName.take(1)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }

                item {
                    Text(
                        text = if (isUncategorizedDrilldown) "Uncategorized transactions" else "Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = summary.categoryName,
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

    val spendingImpact = TransactionTreatments.spendingImpactCents(
        treatment = transaction.accountingTreatment,
        excludedFromSpending = transaction.excludedFromSpending,
        amountCents = transaction.amountCents
    )
    val amount = transaction.amountCents / 100.0
    val spendingMerchant = transaction.spendingMerchantName?.takeIf { it.isNotBlank() }
    val payerPayee = transaction.displayMerchantName ?: transaction.merchantRaw

    LedgerListRow(
        title = spendingMerchant
            ?: payerPayee
            ?: transaction.sourceInstitution
            ?: "Unknown merchant",
        supportingText = if (!spendingMerchant.isNullOrBlank() && !payerPayee.isNullOrBlank() && spendingMerchant != payerPayee) {
            "${formatter.format(Date(transaction.occurredAtEpochMs))} - via $payerPayee"
        } else {
            formatter.format(Date(transaction.occurredAtEpochMs))
        },
        metadataText = displayCategoryName(transaction.categoryName),
        pillText = if (transaction.reviewStatus == "NEEDS_REVIEW") "Review" else null,
        trailingText = formatSignedMoney(spendingImpact),
        leadingText = transaction.displayMerchantName?.take(1) ?: "$",
        onClick = onClick
    )
    return

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

            val categoryText = transaction.categoryName.orEmpty()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
    transactions: List<TransactionEntity>,
    onNavigate: (AppScreen) -> Unit,
    onQuickActions: () -> Unit,
    onBack: () -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    var selectedFilter by remember {
        mutableStateOf<ReviewQueueFilter?>(null)
    }

    val allIssueTransactions = remember(transactions) {
        transactions
            .filter { hasAnyReviewIssue(it) }
            .sortedByDescending { it.occurredAtEpochMs }
    }

    val needsReviewTransactions = remember(transactions) {
        transactions
            .filter { it.reviewStatus == "NEEDS_REVIEW" }
            .sortedByDescending { it.occurredAtEpochMs }
    }

    val missingCategoryTransactions = remember(transactions) {
        transactions
            .filter { hasMissingCategory(it) }
            .sortedByDescending { it.occurredAtEpochMs }
    }

    val possibleTransferTransactions = remember(transactions) {
        transactions
            .filter {
                it.accountingTreatment in setOf(
                    TransactionTreatments.PERSON_TO_PERSON,
                    TransactionTreatments.TRANSFER,
                    TransactionTreatments.REIMBURSEMENT
                ) && hasAnyReviewIssue(it)
            }
            .sortedByDescending { it.occurredAtEpochMs }
    }

    val filteredTransactions = remember(
        allIssueTransactions,
        needsReviewTransactions,
        missingCategoryTransactions,
        possibleTransferTransactions,
        selectedFilter
    ) {
        when (selectedFilter) {
            null -> emptyList()
            ReviewQueueFilter.ALL_ISSUES -> allIssueTransactions

            ReviewQueueFilter.NEEDS_REVIEW -> needsReviewTransactions

            ReviewQueueFilter.MISSING_MERCHANT -> allIssueTransactions.filter {
                hasMissingMerchant(it)
            }

            ReviewQueueFilter.MISSING_CATEGORY -> missingCategoryTransactions

            ReviewQueueFilter.POSSIBLE_TRANSFERS -> possibleTransferTransactions

            ReviewQueueFilter.LOW_CONFIDENCE -> allIssueTransactions.filter {
                hasLowConfidence(it)
            }
        }
    }

    val needsReviewCount = transactions.count { it.reviewStatus == "NEEDS_REVIEW" }
    val missingMerchantCount = transactions.count { hasMissingMerchant(it) }
    val missingCategoryCount = transactions.count { hasMissingCategory(it) }
    val lowConfidenceCount = transactions.count { hasLowConfidence(it) }
    val merchantCategoryCount = remember(transactions) {
        merchantSummaries(transactions).count { it.uncategorizedCount > 0 }
    }
    val missingCategoryAmountCents = remember(missingCategoryTransactions) {
        missingCategoryTransactions.sumOf {
            TransactionTreatments.spendingImpactCents(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending,
                amountCents = it.amountCents
            )
        }
    }
    val selectedListTitle = when (selectedFilter) {
        ReviewQueueFilter.ALL_ISSUES -> "All review items"
        ReviewQueueFilter.NEEDS_REVIEW -> "Transactions need review"
        ReviewQueueFilter.MISSING_MERCHANT -> "Missing merchant"
        ReviewQueueFilter.MISSING_CATEGORY -> "Uncategorized spending"
        ReviewQueueFilter.POSSIBLE_TRANSFERS -> "Possible transfers"
        ReviewQueueFilter.LOW_CONFIDENCE -> "Needs confirmation"
        null -> null
    }

    LedgerAppScaffold(
        title = "Review",
        activeScreen = AppScreen.REVIEW_QUEUE,
        onNavigate = onNavigate,
        onQuickActions = onQuickActions,
        onBack = onBack
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

                ReviewIntroPanel()
            }

            if (merchantCategoryCount > 0) {
                item {
                    ReviewTaskCard(
                        icon = "M",
                        title = "Merchants need categories",
                        description = "Set a category once and future transactions can follow it.",
                        metric = "$merchantCategoryCount merchants",
                        actionText = "Review merchants",
                        accentColor = Color(0xFFFFA726),
                        onClick = { onNavigate(AppScreen.MERCHANTS) }
                    )
                }
            }

            if (needsReviewCount > 0) {
                item {
                    ReviewTaskCard(
                        icon = "R",
                        title = "Transactions need review",
                        description = "Confirm transactions that need a decision or one-off edit.",
                        metric = "$needsReviewCount transactions",
                        actionText = "Review transactions",
                        accentColor = Color(0xFF8E63E7),
                        selected = selectedFilter == ReviewQueueFilter.NEEDS_REVIEW,
                        onClick = { selectedFilter = ReviewQueueFilter.NEEDS_REVIEW }
                    )
                }
            }

            if (missingCategoryCount > 0) {
                item {
                    ReviewTaskCard(
                        icon = "C",
                        title = "Uncategorized spending",
                        description = "Spending transactions without a category.",
                        metric = formatSignedMoney(missingCategoryAmountCents),
                        actionText = "$missingCategoryCount transactions",
                        accentColor = Color(0xFF64A9F5),
                        selected = selectedFilter == ReviewQueueFilter.MISSING_CATEGORY,
                        onClick = { selectedFilter = ReviewQueueFilter.MISSING_CATEGORY }
                    )
                }
            }

            if (possibleTransferTransactions.isNotEmpty()) {
                item {
                    ReviewTaskCard(
                        icon = "T",
                        title = "Possible transfers",
                        description = "Check person-to-person items, reimbursements, and transfers.",
                        metric = "${possibleTransferTransactions.size} transactions",
                        actionText = "Review items",
                        accentColor = Color(0xFF33A564),
                        selected = selectedFilter == ReviewQueueFilter.POSSIBLE_TRANSFERS,
                        onClick = { selectedFilter = ReviewQueueFilter.POSSIBLE_TRANSFERS }
                    )
                }
            }

            if (allIssueTransactions.isEmpty() && merchantCategoryCount == 0) {
                item {
                    InlineInfoPanel(
                        title = "All caught up",
                        body = "There are no review tasks waiting right now."
                    )
                }
            }

            if (allIssueTransactions.isNotEmpty()) {
                item {
                    ReviewSecondaryFilters(
                        allIssuesCount = allIssueTransactions.size,
                        missingMerchantCount = missingMerchantCount,
                        needsConfirmationCount = lowConfidenceCount,
                        onAllSelected = { selectedFilter = ReviewQueueFilter.ALL_ISSUES },
                        onMissingMerchantSelected = { selectedFilter = ReviewQueueFilter.MISSING_MERCHANT },
                        onNeedsConfirmationSelected = { selectedFilter = ReviewQueueFilter.LOW_CONFIDENCE }
                    )
                }
            }

            if (selectedFilter != null) {
                item {
                    ReviewSelectedListHeader(
                        title = selectedListTitle.orEmpty(),
                        count = filteredTransactions.size,
                        onClear = { selectedFilter = null }
                    )
                }

                if (filteredTransactions.isEmpty()) {
                    item {
                        EmptySectionText("No transactions found for this review task.")
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewIntroPanel() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Items that need your attention",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
        Text(
            text = "Keep your spending accurate without reviewing everything one by one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ReviewTaskCard(
    icon: String,
    title: String,
    description: String,
    metric: String,
    actionText: String,
    accentColor: Color,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (selected) accentColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.16f),
                    contentColor = accentColor,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        text = metric,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = accentColor
                    )
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = accentColor.copy(alpha = 0.10f),
                contentColor = accentColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun ReviewSecondaryFilters(
    allIssuesCount: Int,
    missingMerchantCount: Int,
    needsConfirmationCount: Int,
    onAllSelected: () -> Unit,
    onMissingMerchantSelected: () -> Unit,
    onNeedsConfirmationSelected: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "More review filters",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAllSelected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("All $allIssuesCount")
                }
                OutlinedButton(
                    onClick = onMissingMerchantSelected,
                    modifier = Modifier.weight(1f),
                    enabled = missingMerchantCount > 0
                ) {
                    Text("Merchant $missingMerchantCount")
                }
            }

            OutlinedButton(
                onClick = onNeedsConfirmationSelected,
                modifier = Modifier.fillMaxWidth(),
                enabled = needsConfirmationCount > 0
            ) {
                Text("Needs confirmation $needsConfirmationCount")
            }
        }
    }
}

@Composable
fun ReviewSelectedListHeader(
    title: String,
    count: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = "$count items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onClear) {
            Text("Hide")
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatStrip(
            items = listOf(
                StatStripItem("Needs review", needsReviewCount.toString(), emphasized = true),
                StatStripItem("Missing merchant", missingMerchantCount.toString()),
                StatStripItem("Missing category", missingCategoryCount.toString()),
                StatStripItem("Low confidence", lowConfidenceCount.toString())
            )
        )
        Text(
            text = "Showing $visibleCount of $totalIssueCount items needing attention",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
        if (hasLowConfidence(transaction)) add("Needs confirmation")
    }

    val categoryText = transaction.categoryName.orEmpty()

    LedgerListRow(
        title = transaction.displayMerchantName
            ?: transaction.merchantRaw
            ?: transaction.sourceInstitution
            ?: "Unknown merchant",
        supportingText = formatter.format(Date(transaction.occurredAtEpochMs)),
        metadataText = if (categoryText.isNotBlank()) {
            "${issues.joinToString(", ")} - $categoryText"
        } else {
            issues.joinToString(", ")
        },
        pillText = treatmentLabel(transaction.accountingTreatment),
        trailingText = "$${"%.2f".format(amount)}",
        trailingSupportingText = "Fix",
        leadingText = "!",
        onClick = onClick
    )
    return

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

            val categoryText = transaction.categoryName.orEmpty()

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
    currentMonthReimbursementCents: Long,
    currentMonthMovementCents: Long,
    previousMonthSpendingCents: Long,
    currentMonthGrossExpenseCents: Long,
    currentMonthExpenseCount: Int,
    currentMonthCategorySummaries: List<CategorySpendSummary>,
    topCategoryLabel: String,
    topMerchantLabel: String,
    onOpenSetup: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenReviewQueue: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenMerchants: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenQuickActions: () -> Unit
){
    LedgerAppScaffold(
        title = "Home",
        activeScreen = AppScreen.HOME,
        onNavigate = { screen ->
            when (screen) {
                AppScreen.SUMMARY -> onOpenSummary()
                AppScreen.REVIEW_QUEUE -> onOpenReviewQueue()
                AppScreen.TRANSACTIONS -> onOpenTransactions()
                AppScreen.TOOLS -> onOpenTools()
                else -> onOpenTools()
            }
        },
        onQuickActions = onOpenQuickActions
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
                    currentMonthReimbursementCents = currentMonthReimbursementCents,
                    currentMonthMovementCents = currentMonthMovementCents,
                    previousMonthSpendingCents = previousMonthSpendingCents,
                    currentMonthGrossExpenseCents = currentMonthGrossExpenseCents,
                    currentMonthExpenseCount = currentMonthExpenseCount,
                    currentMonthCategorySummaries = currentMonthCategorySummaries,
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
                ListSectionHeader(
                    title = "Daily workflow",
                    actionText = "Tools",
                    onActionClick = onOpenTools
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    tonalElevation = 0.dp
                ) {
                    Column {
                        LedgerListRow(
                            title = "Spending",
                            supportingText = "Monthly totals, categories, and drilldowns",
                            trailingText = "Open",
                            leadingText = "S",
                            onClick = onOpenSummary
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        LedgerListRow(
                            title = "Sources and rules",
                            supportingText = "$identifiedSourceCount active sources - $activeRuleCount saved rules",
                            trailingText = "Manage",
                            leadingText = "R",
                            onClick = onOpenTools
                        )
                    }
                }
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
    LedgerBottomNav(
        selectedDestination = com.example.ledgerlens.ui.LedgerNavDestination.SPENDING,
        onDestinationSelected = { destination ->
            when (destination.screen) {
                AppScreen.SUMMARY -> onOpenSummary()
                AppScreen.REVIEW_QUEUE -> onOpenReviewQueue()
                AppScreen.TRANSACTIONS -> onOpenTransactions()
                AppScreen.TOOLS -> onOpenHome()
                else -> onOpenSummary()
            }
        }
    )
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
    currentMonthReimbursementCents: Long,
    currentMonthMovementCents: Long,
    previousMonthSpendingCents: Long,
    currentMonthGrossExpenseCents: Long,
    currentMonthExpenseCount: Int,
    currentMonthCategorySummaries: List<CategorySpendSummary>,
    topCategoryLabel: String,
    topMerchantLabel: String
) {
    val currentMonthSpending = currentMonthSpendingCents / 100.0
    val currentMonthGrossExpenses = currentMonthGrossExpenseCents / 100.0
    val currentMonthIncome = currentMonthIncomeCents / 100.0
    val currentMonthRefunds = currentMonthRefundCents / 100.0
    val currentMonthReimbursements = currentMonthReimbursementCents / 100.0
    val currentMonthMovements = currentMonthMovementCents / 100.0
    val previousMonthSpending = previousMonthSpendingCents / 100.0
    val delta = currentMonthSpending - previousMonthSpending

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${formatMonthYear(getCurrentMonthStartEpochMs())} overview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatSignedMoney(currentMonthSpendingCents),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            text = "$currentMonthExpenseCount spending-impact transactions",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(
                            text = "Month change",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatSignedMoney(currentMonthSpendingCents - previousMonthSpendingCents),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (delta <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                MiniTrendStrip(
                    values = listOf(
                        previousMonthSpending.toFloat(),
                        (previousMonthSpending * 0.72).toFloat(),
                        (currentMonthSpending * 0.84).toFloat(),
                        (currentMonthSpending * 1.08).toFloat(),
                        currentMonthSpending.toFloat()
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricPanel(
                label = "Gross expenses",
                value = "$${"%.2f".format(currentMonthGrossExpenses)}",
                supportingText = "Before offsets",
                modifier = Modifier.weight(1f)
            )
            MetricPanel(
                label = "Refunds",
                value = "$${"%.2f".format(currentMonthRefunds)}",
                supportingText = "Merchant credits",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricPanel(
                label = "Reimbursements",
                value = "$${"%.2f".format(currentMonthReimbursements)}",
                supportingText = "Person-to-person offsets",
                modifier = Modifier.weight(1f)
            )
            MetricPanel(
                label = "Income",
                value = "$${"%.2f".format(currentMonthIncome)}",
                supportingText = "Deposits",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricPanel(
                label = "Payments",
                value = "$${"%.2f".format(currentMonthMovements)}",
                supportingText = "Transfers and card payments",
                modifier = Modifier.weight(1f)
            )
            MetricPanel(
                label = "Review inbox",
                value = reviewIssueCount.toString(),
                supportingText = "Items",
                modifier = Modifier.weight(1f),
                emphasized = reviewIssueCount > 0
            )
        }

        ListSectionHeader(title = "What stands out")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            tonalElevation = 0.dp
        ) {
            Column {
                LedgerListRow(
                    title = "Top category",
                    supportingText = topCategoryLabel,
                    leadingText = "C"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                LedgerListRow(
                    title = "Top merchant",
                    supportingText = topMerchantLabel,
                    leadingText = "M"
                )
            }
        }

        if (currentMonthCategorySummaries.isNotEmpty()) {
            val categoryBarTotalCents = currentMonthCategorySummaries.sumOf { kotlin.math.abs(it.amountCents) }
            ListSectionHeader(title = "Spend by category")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    currentMonthCategorySummaries.forEach { summary ->
                        val share = if (categoryBarTotalCents > 0) {
                            kotlin.math.abs(summary.amountCents).toFloat() / categoryBarTotalCents.toFloat()
                        } else {
                            0f
                        }
                        CategoryBarRow(
                            label = summary.categoryName,
                            amountText = formatSignedMoney(summary.amountCents),
                            supportingText = "${summary.transactionCount} transactions",
                            progress = share
                        )
                    }
                }
            }
        }

        InlineInfoPanel(
            title = "Setup status",
            body = "$identifiedSourceCount active sources, $uncategorizedSourceCount source decisions pending, $activeRuleCount saved rules. $rawAlertCount SMS imported, $transactionCount transactions parsed."
        )
        return@Column

        FinanceHeroCard(
            amountText = "$${"%.2f".format(currentMonthSpending)}",
            title = formatMonthYear(getCurrentMonthStartEpochMs()),
            subtitle = "$currentMonthExpenseCount spending-impact transactions",
            trendText = "Month change ${if (delta >= 0) "+" else ""}$${"%.2f".format(delta)}"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Income",
                value = "$${"%.2f".format(currentMonthIncome)}",
                supportingText = "Deposits this month",
                modifier = Modifier.weight(1f)
            )

            MetricTile(
                label = "Refunds",
                value = "$${"%.2f".format(currentMonthRefunds)}",
                supportingText = "Money returned",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Payments",
                value = "$${"%.2f".format(currentMonthMovements)}",
                supportingText = "Transfers and card payments",
                modifier = Modifier.weight(1f)
            )

            MetricTile(
                label = "Review",
                value = reviewIssueCount.toString(),
                supportingText = "Items needing attention",
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "What stands out",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Top category: $topCategoryLabel",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Top merchant: $topMerchantLabel",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$identifiedSourceCount active sources, $uncategorizedSourceCount source decisions pending, $activeRuleCount saved rules",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$rawAlertCount SMS imported, $transactionCount transactions parsed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    transactions: List<TransactionEntity>,
    rawAlerts: List<RawAlertEntity>,
    onBack: () -> Unit,
    onOpenParserRuleEditor: (TransactionRuleEntity) -> Unit,
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

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    tonalElevation = 0.dp
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
                    title = "Parser Rules",
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
                        onEditRule = { onOpenParserRuleEditor(rule) },
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
    onEditRule: (() -> Unit)? = null,
    onDisableRule: (TransactionRuleEntity) -> Unit,
    onDeleteRule: (TransactionRuleEntity) -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
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
                rule.categoryName,
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
                if (onEditRule != null) {
                    OutlinedButton(
                        onClick = onEditRule,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit")
                    }
                }

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
    transactions: List<TransactionEntity>,
    statusText: String,
    onNavigate: (AppScreen) -> Unit,
    onQuickActions: () -> Unit,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenRules: () -> Unit,
    onMerchantSelected: (MerchantSummary) -> Unit,
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
    var showCategories by remember { mutableStateOf(false) }
    var showMerchants by remember { mutableStateOf(false) }
    var showAdvancedTools by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val categoryItems = remember(transactions) {
        transactions
            .mapNotNull { transaction ->
                transaction.categoryName
                    ?.trim()
                    ?.takeIf {
                        !isVirtualUncategorizedCategory(it) &&
                            !it.equals("General", ignoreCase = true)
                    }
            }
            .distinctBy { it.lowercase(Locale.US) }
            .map { category ->
                val categoryTransactions = transactions.filter {
                    it.categoryName?.equals(category, ignoreCase = true) == true
                }
                MoreCategorySummary(
                    name = category,
                    transactionCount = categoryTransactions.size,
                    spendingImpactCents = categoryTransactions.sumOf {
                        TransactionTreatments.spendingImpactCents(
                            treatment = it.accountingTreatment,
                            excludedFromSpending = it.excludedFromSpending,
                            amountCents = it.amountCents
                        )
                    }
                )
            }
            .sortedWith(
                compareByDescending<MoreCategorySummary> { kotlin.math.abs(it.spendingImpactCents) }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
    }
    val merchantItems = remember(transactions) {
        merchantSummaries(transactions)
    }

    LedgerAppScaffold(
        title = "More",
        activeScreen = AppScreen.TOOLS,
        onNavigate = onNavigate,
        onQuickActions = onQuickActions
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                MoreSnapshotCard(
                    transactionCount = transactionCount,
                    sourceCount = sourceCount,
                    activeRuleCount = activeRuleCount,
                    rawAlertCount = rawAlertCount,
                    statusText = statusText
                )
            }

            item {
                MoreSectionHeader("Manage")
                MoreSectionCard {
                    MoreActionRow(
                        leadingText = "C",
                        title = "Categories",
                        supportingText = "View categories currently used in your transactions.",
                        trailingText = if (showCategories) "Hide" else categoryItems.size.toString(),
                        onClick = { showCategories = !showCategories }
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "M",
                        title = "Merchants",
                        supportingText = "Browse merchant and payee groups.",
                        trailingText = if (showMerchants) "Hide" else merchantItems.size.toString(),
                        onClick = { showMerchants = !showMerchants }
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "S",
                        title = "SMS Sources",
                        supportingText = "Review senders that LedgerLens can parse.",
                        onClick = onOpenSources
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "R",
                        title = "Rules",
                        supportingText = "Manage saved merchant defaults and parser aliases.",
                        trailingText = activeRuleCount.toString(),
                        onClick = onOpenRules
                    )
                }
            }

            if (showCategories) {
                item {
                    MoreSectionCard {
                        if (categoryItems.isEmpty()) {
                            MoreBodyText("No saved categories yet. Categories appear here after you assign them to merchants or transactions.")
                        } else {
                            categoryItems.take(12).forEachIndexed { index, category ->
                                MoreActionRow(
                                    leadingText = categoryGlyph(category.name),
                                    title = category.name,
                                    supportingText = "${category.transactionCount} transactions",
                                    trailingText = formatSignedMoney(category.spendingImpactCents),
                                    onClick = null
                                )
                                if (index < categoryItems.take(12).lastIndex) {
                                    MoreDivider()
                                }
                            }
                        }
                    }
                }
            }

            if (showMerchants) {
                item {
                    MoreSectionCard {
                        if (merchantItems.isEmpty()) {
                            MoreBodyText("No merchants or payees yet. They appear after transactions are parsed.")
                        } else {
                            merchantItems.take(12).forEachIndexed { index, merchant ->
                                MoreActionRow(
                                    leadingText = merchant.merchantName.take(1).uppercase(Locale.US),
                                    title = merchant.merchantName,
                                    supportingText = "${merchant.transactionCount} transactions",
                                    trailingText = formatSignedMoney(merchant.spendingAmountCents),
                                    onClick = { onMerchantSelected(merchant) }
                                )
                                if (index < merchantItems.take(12).lastIndex) {
                                    MoreDivider()
                                }
                            }
                        }
                    }
                }
            }

            item {
                MoreSectionHeader("Import & Export")
                MoreSectionCard {
                    MoreActionRow(
                        leadingText = "I",
                        title = "Import SMS / setup",
                        supportingText = "Review possible financial senders and setup SMS parsing.",
                        onClick = onOpenSetup
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "N",
                        title = "Refresh latest SMS",
                        supportingText = "Import new financial SMS messages.",
                        onClick = onRefreshLatestSms
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "H",
                        title = "Backfill SMS history",
                        supportingText = "Import older financial SMS messages.",
                        onClick = onBackfillSmsHistory
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "E",
                        title = "Export transactions",
                        supportingText = "Share a CSV of your transaction data.",
                        onClick = onExportTransactions
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "P",
                        title = "Export parser examples",
                        supportingText = "Share local SMS examples for improving merchant parsing.",
                        onClick = onExportParserCorpus
                    )
                }
            }

            item {
                MoreSectionHeader("Tools")
                MoreSectionCard {
                    MoreActionRow(
                        leadingText = "A",
                        title = "Reapply saved rules",
                        supportingText = "Update existing transactions using your saved rules.",
                        onClick = onReapplySavedRules
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "D",
                        title = "Advanced tools",
                        supportingText = "Source detection, reprocessing, and diagnostics.",
                        trailingText = if (showAdvancedTools) "Hide" else ">",
                        onClick = { showAdvancedTools = !showAdvancedTools }
                    )
                }
            }

            if (showAdvancedTools) {
                item {
                    MoreSectionCard {
                        MoreActionRow(
                            leadingText = "F",
                            title = "Detect SMS sources",
                            supportingText = "Find possible financial SMS senders from imported messages.",
                            onClick = onDetectSources
                        )
                        MoreDivider()
                        MoreActionRow(
                            leadingText = "P",
                            title = "Process approved sources",
                            supportingText = "Parse transactions from sources you approved.",
                            onClick = onParseIdentifiedSources
                        )
                        MoreDivider()
                        MoreActionRow(
                            leadingText = "B",
                            title = "Rebuild transactions",
                            supportingText = "Recreate parsed transactions using current parser and rules.",
                            onClick = onReparseTransactions
                        )
                        MoreDivider()
                        if (confirmClearAll) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Clear local test data?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Text(
                                    text = "This removes imported SMS, sources, rules, and transactions from this local database.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { confirmClearAll = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel")
                                    }
                                    Button(
                                        onClick = {
                                            confirmClearAll = false
                                            onClearAll()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Clear")
                                    }
                                }
                            }
                        } else {
                            MoreActionRow(
                                leadingText = "X",
                                title = "Clear local test data",
                                supportingText = "Remove imported SMS, sources, rules, and transactions.",
                                trailingText = "Confirm",
                                onClick = { confirmClearAll = true }
                            )
                        }
                    }
                }
            }

            item {
                MoreSectionHeader("App")
                MoreSectionCard {
                    MoreActionRow(
                        leadingText = "S",
                        title = "Settings",
                        supportingText = "Preferences will live here as the app grows.",
                        trailingText = "",
                        onClick = null
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "L",
                        title = "Privacy & security",
                        supportingText = "Your SMS and transaction data stay on this device.",
                        trailingText = "",
                        onClick = null
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "?",
                        title = "Help",
                        supportingText = "LedgerLens is still a local personal app.",
                        trailingText = "",
                        onClick = null
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "i",
                        title = "App information",
                        supportingText = "Package com.example.ledgerlens.",
                        trailingText = "",
                        onClick = null
                    )
                }
            }
        }
    }
}

data class MoreCategorySummary(
    val name: String,
    val transactionCount: Int,
    val spendingImpactCents: Long
)

@Composable
fun MoreSnapshotCard(
    transactionCount: Int,
    sourceCount: Int,
    activeRuleCount: Int,
    rawAlertCount: Int,
    statusText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "LedgerLens",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = "$transactionCount transactions from $sourceCount SMS sources",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                text = "$rawAlertCount imported SMS - $activeRuleCount saved rules",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (statusText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MoreSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun MoreSectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun MoreActionRow(
    leadingText: String,
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    trailingText: String = ">",
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable { onClick() }
            } else {
                Modifier
            }
        )
        .padding(horizontal = 12.dp, vertical = 10.dp)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Surface(
            color = moreAccentColor(title).copy(alpha = 0.14f),
            contentColor = moreAccentColor(title),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(
                text = leadingText.take(2),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Text(
            text = trailingText,
            style = MaterialTheme.typography.labelMedium,
            color = if (onClick != null) colors.primary else colors.onSurfaceVariant
        )
    }
}

@Composable
fun MoreBodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp)
    )
}

@Composable
fun MoreDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    )
}

fun moreAccentColor(key: String): Color {
    val normalized = key.lowercase(Locale.US)
    return when {
        "categor" in normalized -> Color(0xFF21A66B)
        "merchant" in normalized -> Color(0xFF9B6CF3)
        "source" in normalized || "sms" in normalized -> Color(0xFF5D9CEC)
        "rule" in normalized -> Color(0xFFFF8A3D)
        "import" in normalized || "refresh" in normalized || "backfill" in normalized -> Color(0xFF43B86B)
        "export" in normalized -> Color(0xFF7C5CFF)
        "advanced" in normalized || "detect" in normalized || "process" in normalized -> Color(0xFFFFB33F)
        "clear" in normalized -> Color(0xFFE85D5D)
        "privacy" in normalized -> Color(0xFF21A66B)
        else -> Color(0xFF6E7B8B)
    }
}

@Composable
fun ToolActionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        LedgerListRow(
            title = title,
            supportingText = description,
            trailingText = buttonText,
            leadingText = title.take(1),
            onClick = onClick
        )
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

    val activityAmount = merchant.totalAmountCents / 100.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = merchant.merchantName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )

                TreatmentChip(label = treatmentLabel(merchant.primaryTreatment))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Transactions: ${merchant.transactionCount} - Uncategorized expenses: ${merchant.uncategorizedCount}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Activity total: $${"%.2f".format(activityAmount)}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Net spending impact: ${formatSignedMoney(merchant.spendingAmountCents)}",
                style = MaterialTheme.typography.labelSmall
            )

            val categoryText = if (merchant.categoryName.isNullOrBlank()) {
                "No default category"
            } else {
                merchant.categoryName
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
    allTransactions: List<TransactionEntity>,
    rawAlerts: List<RawAlertEntity>,
    onBack: () -> Unit,
    onUpdateMerchantCategory: (String, String, Boolean, Boolean) -> Unit,
    onRenameMerchantGroup: (String) -> Unit,
    onOpenParserRuleEditor: (MerchantAliasRuleDraft) -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    val expenseTotal = transactions
        .sumOf {
            TransactionTreatments.spendingImpactCents(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending,
                amountCents = it.amountCents
            )
        }

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
                MerchantNameToolsCard(
                    merchant = merchant,
                    transactions = transactions,
                    allTransactions = allTransactions,
                    rawAlerts = rawAlerts,
                    onRenameMerchantGroup = onRenameMerchantGroup,
                    onOpenParserRuleEditor = onOpenParserRuleEditor
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
            Text("Net spending impact: ${formatSignedMoney(expenseTotalCents)}")
            Text("Usual treatment: ${treatmentLabel(merchant.primaryTreatment)}")
            Text("Uncategorized expenses: ${merchant.uncategorizedCount}")

            val categoryText = if (merchant.categoryName.isNullOrBlank()) {
                "No category assigned"
            } else {
                merchant.categoryName
            }

            Text("Current merchant category: $categoryText")
        }
    }
}

@Composable
fun MerchantNameToolsCard(
    merchant: MerchantSummary,
    transactions: List<TransactionEntity>,
    allTransactions: List<TransactionEntity>,
    rawAlerts: List<RawAlertEntity>,
    onRenameMerchantGroup: (String) -> Unit,
    onOpenParserRuleEditor: (MerchantAliasRuleDraft) -> Unit
) {
    var displayName by remember(merchant.merchantName) {
        mutableStateOf(merchant.merchantName)
    }
    val primaryTransaction = transactions.firstOrNull()
    val rawAlert = primaryTransaction?.let { transaction ->
        rawAlerts.firstOrNull { it.id == transaction.rawAlertId }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Merchant Name",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Rename this existing group for your records, or teach the parser aliases that should become this merchant in the future.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { onRenameMerchantGroup(displayName) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rename Existing Transactions")
            }
            OutlinedButton(
                onClick = {
                    if (primaryTransaction != null) {
                        onOpenParserRuleEditor(
                            buildMerchantAliasDraftForTransaction(
                                transaction = primaryTransaction,
                                rawAlert = rawAlert,
                                allTransactions = allTransactions,
                                includeCategory = false,
                                includeTreatment = false
                            ).copy(canonicalMerchantName = displayName)
                        )
                    }
                },
                enabled = primaryTransaction != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Fix Parser Rule")
            }
        }
    }
}

@Composable
fun MerchantCategoryAssignmentCard(
    merchant: MerchantSummary,
    onUpdateMerchantCategory: (String, String, Boolean, Boolean) -> Unit
) {
    var categoryText by remember(merchant.merchantName, merchant.categoryName) {
        mutableStateOf(merchant.categoryName ?: "")
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
        "Groceries",
        "Restaurants",
        "Gas",
        "Shopping",
        "Bills & Utilities",
        "Subscriptions",
        "Healthcare",
        "Travel",
        "Charity",
        "Transfer",
        "Other"
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
                                categoryText = preset
                                treatmentText = TransactionTreatments.EXPENSE
                                onUpdateMerchantCategory(
                                    categoryText,
                                    treatmentText,
                                    applyCategoryAutomatically,
                                    requiresReview
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(preset)
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
                listOf(TransactionTreatments.REFUND, TransactionTreatments.REIMBURSEMENT),
                listOf(TransactionTreatments.CREDIT_CARD_PAYMENT, TransactionTreatments.UNKNOWN),
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

            Button(
                onClick = {
                    onUpdateMerchantCategory(
                        categoryText,
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
    val spendingImpact = TransactionTreatments.spendingImpactCents(
        treatment = transaction.accountingTreatment,
        excludedFromSpending = transaction.excludedFromSpending,
        amountCents = transaction.amountCents
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "$${"%.2f".format(amount)} - ${treatmentLabel(transaction.accountingTreatment)}",
                style = MaterialTheme.typography.bodyLarge
            )

            if (spendingImpact != 0L) {
                Text(
                    text = "Spending impact: ${formatSignedMoney(spendingImpact)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (!transaction.spendingMerchantName.isNullOrBlank()) {
                Text(
                    text = "Spending merchant: ${transaction.spendingMerchantName}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = formatter.format(Date(transaction.occurredAtEpochMs)),
                style = MaterialTheme.typography.labelSmall
            )

            val categoryText = transaction.categoryName.orEmpty()

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
