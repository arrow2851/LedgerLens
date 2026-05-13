package com.example.ledgerlens.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.merchants.applyMerchantCategoryBulk
import com.example.ledgerlens.domain.parser.ParseRunResult
import com.example.ledgerlens.domain.parser.detectAndSaveSources
import com.example.ledgerlens.domain.parser.parseIdentifiedSourceTransactions
import com.example.ledgerlens.domain.parser.reapplySavedRulesToExistingTransactions
import com.example.ledgerlens.domain.parser.updateRawAlertStatusesForSource
import com.example.ledgerlens.domain.rules.MERCHANT_DEFAULT_RULE_SOURCE_KEY
import com.example.ledgerlens.domain.rules.MerchantAliasApplyResult
import com.example.ledgerlens.domain.rules.MerchantAliasRuleDraft
import com.example.ledgerlens.domain.rules.applyMerchantAliasRuleToTransaction
import com.example.ledgerlens.domain.rules.buildMerchantAliasRules
import com.example.ledgerlens.domain.rules.normalizeAliasText
import com.example.ledgerlens.domain.rules.normalizeRulePhrase
import com.example.ledgerlens.domain.rules.previewMerchantAliasRule
import com.example.ledgerlens.domain.source.SourceDetector
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
import com.example.ledgerlens.ui.components.MetricPanel
import com.example.ledgerlens.ui.components.MetricTile
import com.example.ledgerlens.ui.components.MiniTrendStrip
import com.example.ledgerlens.ui.components.QuickActionItem
import com.example.ledgerlens.ui.components.QuickActionSheet
import com.example.ledgerlens.ui.components.StatStrip
import com.example.ledgerlens.ui.components.StatStripItem
import com.example.ledgerlens.ui.components.TreatmentChip
import com.example.ledgerlens.ui.components.TreatmentSelector
import com.example.ledgerlens.ui.merchants.MerchantReviewScreen as MerchantReviewInboxScreen
import com.example.ledgerlens.ui.rules.ParserRuleEditorSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

