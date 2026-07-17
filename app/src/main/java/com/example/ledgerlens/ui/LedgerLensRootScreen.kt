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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.room.withTransaction
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.merchants.applyMerchantCategoryBulk
import com.example.ledgerlens.domain.parser.ParseMode
import com.example.ledgerlens.domain.parser.ParseRunResult
import com.example.ledgerlens.domain.parser.detectAndSaveSources
import com.example.ledgerlens.domain.parser.parseIdentifiedSourceTransactions
import com.example.ledgerlens.domain.parser.reapplySavedRulesToExistingTransactions
import com.example.ledgerlens.domain.parser.updateRawAlertStatusesForSource
import com.example.ledgerlens.domain.privacy.RawSmsRetention
import com.example.ledgerlens.domain.privacy.shouldRedactAfterSuccessfulParse
import com.example.ledgerlens.domain.rules.MERCHANT_DEFAULT_RULE_SOURCE_KEY
import com.example.ledgerlens.domain.rules.MerchantAliasApplyResult
import com.example.ledgerlens.domain.rules.MerchantAliasRuleDraft
import com.example.ledgerlens.domain.rules.applyMerchantAliasRuleToTransaction
import com.example.ledgerlens.domain.rules.buildMerchantAliasRules
import com.example.ledgerlens.domain.rules.normalizeAliasText
import com.example.ledgerlens.domain.rules.normalizeRulePhrase
import com.example.ledgerlens.domain.rules.previewMerchantAliasRule
import com.example.ledgerlens.domain.rules.RuleKind
import com.example.ledgerlens.domain.source.SourceDetector
import com.example.ledgerlens.domain.source.SourceReviewUseCase
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
import com.example.ledgerlens.domain.transactions.TransactionCorrectionUseCase
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
        requiresReview = transaction.reviewStatus == ReviewStatus.NEEDS_REVIEW
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
fun LedgerLensAppRoot(
    database: AppDatabase,
    syncStatusText: String,
    onSyncSmsAlerts: () -> Unit,
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit,
    onExportTransactions: () -> Unit,
    onExportParserDiagnostics: () -> Unit,
    rawSmsRetention: RawSmsRetention,
    onRawSmsRetentionChanged: (RawSmsRetention) -> Unit,
    onDeleteExportedFiles: () -> Unit
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
    val sourceReviewUseCase = remember(database) {
        SourceReviewUseCase(database)
    }
    val transactionCorrectionUseCase = remember(database) {
        TransactionCorrectionUseCase(database)
    }

    var statusText by remember {
        mutableStateOf("Import SMS, detect sources, then review uncategorized possible sources.")
    }

    LaunchedEffect(syncStatusText) {
        if (syncStatusText.isNotBlank()) {
            statusText = syncStatusText
        }
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

    var parserRuleEditorRequest by remember {
        mutableStateOf<ParserRuleEditorRequest?>(null)
    }

    val backAction = resolveLedgerBackAction(
        showSheet = parserRuleEditorRequest != null,
        hasSelectedTransaction = selectedTransaction != null,
        hasSelectedSource = selectedSource != null,
        hasSelectedMerchant = selectedMerchant != null,
        activeScreen = activeScreen
    )

    BackHandler(enabled = backAction != LedgerBackAction.EXIT_APP) {
        when (backAction) {
            LedgerBackAction.DISMISS_SHEET -> {
                parserRuleEditorRequest = null
            }
            LedgerBackAction.CLOSE_TRANSACTION_DETAIL -> selectedTransaction = null
            LedgerBackAction.CLOSE_SOURCE_DETAIL -> selectedSource = null
            LedgerBackAction.CLOSE_MERCHANT_DETAIL -> selectedMerchant = null
            LedgerBackAction.GO_REVIEW -> activeScreen = AppScreen.REVIEW_QUEUE
            LedgerBackAction.GO_MORE -> activeScreen = AppScreen.TOOLS
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
        return sourceReviewUseCase.useSender(
            source = source,
            accountType = accountType,
            redactRawSmsAfterParse = rawSmsRetention.shouldRedactAfterSuccessfulParse()
        )
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
                ruleKind = if (sourceKey == MERCHANT_DEFAULT_RULE_SOURCE_KEY) {
                    RuleKind.MERCHANT_DEFAULT
                } else if (transactionType != null && merchantName == null && categoryName == null) {
                    RuleKind.TREATMENT_OVERRIDE
                } else if (categoryName != null && merchantName == null) {
                    RuleKind.CATEGORY_OVERRIDE
                } else {
                    RuleKind.SOURCE_ALIAS
                },
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
                ruleKind = if (sourceKey == MERCHANT_DEFAULT_RULE_SOURCE_KEY) {
                    RuleKind.MERCHANT_DEFAULT
                } else {
                    existing.ruleKind
                },
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
                val source = selectedSource ?: return@SourceDetailScreen
                scope.launch(Dispatchers.IO) {
                    val result = confirmSourceAndParse(source, accountType)

                    withContext(Dispatchers.Main) {
                        statusText = "LedgerLens will use this sender. Parsed ${result.parsedCount} new transactions."
                        selectedSource = null
                    }
                }
            },
            onDismissAsNonSource = {
                val source = selectedSource ?: return@SourceDetailScreen
                scope.launch(Dispatchers.IO) {
                    val existingTransactions = sourceReviewUseCase.ignoreSender(source)

                    withContext(Dispatchers.Main) {
                        statusText = "Ignored future alerts from this sender. Existing parsed transactions were kept ($existingTransactions)."
                        selectedSource = null
                    }
                }
            },
            onMoveToUncategorized = {
                val source = selectedSource ?: return@SourceDetailScreen
                scope.launch(Dispatchers.IO) {
                    val existingTransactions = sourceReviewUseCase.moveSenderBackToReview(source)

                    withContext(Dispatchers.Main) {
                        statusText = "Moved sender back to review. Existing parsed transactions were kept ($existingTransactions)."
                        selectedSource = null
                    }
                }
            }
        )
    } else if (selectedTransaction != null) {
        val transactionForDetail = selectedTransaction ?: return
        val matchingRawAlert = rawAlerts.firstOrNull {
            it.id == transactionForDetail.rawAlertId
        }

        TransactionDetailScreen(
            transaction = transactionForDetail,
            rawAlert = matchingRawAlert,
            allTransactions = transactions,
            onBack = {
                selectedTransaction = null
            },
            onSaveTransactionDraft = { draft, onComplete ->
                val transactionId = transactionForDetail.id
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        transactionCorrectionUseCase.updateTransactionFromUserEdit(
                            transactionId = transactionId,
                            draft = draft
                        )
                    }.fold(
                        onSuccess = { updated ->
                            withContext(Dispatchers.Main) {
                                if (updated != null) {
                                    selectedTransaction = updated
                                }
                                onComplete(null)
                            }
                        },
                        onFailure = { error ->
                            withContext(Dispatchers.Main) {
                                onComplete(error)
                            }
                        }
                    )
                }
            },
            onApplyMerchantToSimilar = { matchPhrase, merchantName, onComplete ->
                val sourceKey = transactionForDetail.sourceKey
                val currentTransactionId = transactionForDetail.id
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
                        sourceKey = sourceKey,
                        matchPhrase = cleanedPhrase,
                        merchantName = cleanedMerchant
                    )

                    val updatedCount = database.transactionDao().updateMerchantForSimilarRawText(
                        sourceKey = sourceKey,
                        likePattern = "%$cleanedPhrase%",
                        merchantName = cleanedMerchant,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = database.transactionDao().getById(currentTransactionId)
                            ?: transactionForDetail.copy(
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
                val sourceKey = transactionForDetail.sourceKey
                val transactionType = transactionForDetail.transactionType
                val reviewStatus = transactionForDetail.reviewStatus
                val excludedFromSpending = transactionForDetail.excludedFromSpending
                scope.launch(Dispatchers.IO) {
                    val cleanedPhrase = matchPhrase.trim()

                    if (cleanedPhrase.isBlank()) {
                        withContext(Dispatchers.Main) {
                            onComplete(0)
                        }
                        return@launch
                    }

                    saveMergedRule(
                        sourceKey = sourceKey,
                        matchPhrase = cleanedPhrase,
                        transactionType = transactionType,
                        reviewStatus = reviewStatus,
                        excludedFromSpending = excludedFromSpending
                    )

                    val updatedCount = database.transactionDao().updateClassificationForSimilarRawText(
                        sourceKey = sourceKey,
                        likePattern = "%$cleanedPhrase%",
                        transactionType = transactionType,
                        reviewStatus = reviewStatus,
                        excludedFromSpending = excludedFromSpending,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        onComplete(updatedCount)
                    }
                }
            },
            onApplyCategoryToSimilar = { matchPhrase, category, onComplete ->
                val sourceKey = transactionForDetail.sourceKey
                val currentTransactionId = transactionForDetail.id
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
                        sourceKey = sourceKey,
                        matchPhrase = cleanedPhrase,
                        categoryName = cleanedCategory
                    )

                    val updatedCount = database.transactionDao().updateCategoryForSimilarRawText(
                        sourceKey = sourceKey,
                        likePattern = "%$cleanedPhrase%",
                        categoryName = cleanedCategory,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        selectedTransaction = database.transactionDao().getById(currentTransactionId)
                            ?: transactionForDetail.copy(
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
                    title = "Teach the app to recognise this sender",
                    draft = draft
                )
            }
        )
    } else if (activeScreen == AppScreen.SUMMARY) {
        SpendingSummaryScreen(
            transactions = transactions,
            rawAlertCount = rawAlertCount,
            pendingSourceReviewCount = uncategorizedSourceCount,
            approvedSourceCount = identifiedSourceCount,
            reviewIssueCount = reviewIssueCount,
            onNavigate = { screen ->
                activeScreen = screen
            },
            onSyncSmsAlerts = {
                statusText = "Syncing SMS alerts..."
                onSyncSmsAlerts()
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
            onSyncSmsAlerts = {
                statusText = "Syncing SMS alerts..."
                onSyncSmsAlerts()
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
            onSyncSmsAlerts = {
                statusText = "Syncing SMS alerts..."
                onSyncSmsAlerts()
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
        val merchantForDetail = selectedMerchant ?: return
        val merchantTransactions = transactions
            .filter {
                merchantSummaryName(it).equals(merchantForDetail.merchantName, ignoreCase = true) &&
                    it.currency.equals(merchantForDetail.currency, ignoreCase = true)
            }
            .sortedByDescending { it.occurredAtEpochMs }

        MerchantDetailScreen(
            merchant = merchantForDetail,
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
                        merchantForDetail.primaryTreatment
                    }
                    val now = System.currentTimeMillis()

                    if (applyCategoryAutomatically) {
                        database.transactionDao().updateCategoryForMerchantName(
                            merchantName = merchantForDetail.merchantName,
                            categoryName = cleanedCategory,
                            updatedAtEpochMs = now
                        )
                    }

                    database.transactionDao().updateTreatmentForMerchantName(
                        merchantName = merchantForDetail.merchantName,
                        accountingTreatment = cleanedTreatment,
                        excludedFromSpending = TransactionTreatments.defaultExcludedFromSpending(cleanedTreatment),
                        updatedAtEpochMs = now
                    )

                    saveMergedRule(
                        sourceKey = MERCHANT_DEFAULT_RULE_SOURCE_KEY,
                        matchPhrase = merchantForDetail.merchantName,
                        merchantName = merchantForDetail.merchantName,
                        categoryName = cleanedCategory,
                        transactionType = cleanedTreatment,
                        excludedFromSpending = TransactionTreatments.defaultExcludedFromSpending(cleanedTreatment),
                        applyCategoryAutomatically = applyCategoryAutomatically,
                        requiresReview = requiresReview
                    )

                    withContext(Dispatchers.Main) {
                        selectedMerchant = merchantForDetail.copy(
                            primaryTreatment = cleanedTreatment,
                            categoryName = cleanedCategory,
                            uncategorizedCount = if (applyCategoryAutomatically) 0 else merchantForDetail.uncategorizedCount
                        )
                    }
                }
            },
            onRenameMerchantGroup = { newName ->
                scope.launch(Dispatchers.IO) {
                    val oldName = merchantForDetail.merchantName
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
                        selectedMerchant = merchantForDetail.copy(merchantName = cleanedName)
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
            onSyncSmsAlerts = {
                statusText = "Syncing SMS alerts..."
                onSyncSmsAlerts()
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
                    val result = parseIdentifiedSourceTransactions(
                        database = database,
                        mode = ParseMode.NEW_PENDING_ONLY,
                        redactRawSmsAfterParse = rawSmsRetention.shouldRedactAfterSuccessfulParse()
                    )

                    withContext(Dispatchers.Main) {
                        statusText =
                            "Matched ${result.matchedAlertCount} SMS from identified sources. Parsed ${result.parsedCount}, skipped existing ${result.skippedCount}, ignored ${result.ignoredNonTransactionCount}, failed ${result.failedCount}."
                    }
                }
            },
            onReparseTransactions = {
                statusText = "Reparsing transactions from identified sources..."

                scope.launch(Dispatchers.IO) {
                    var result = ParseRunResult()
                    database.withTransaction {
                        result = parseIdentifiedSourceTransactions(
                            database = database,
                            mode = ParseMode.REPARSE_ALL_APPROVED,
                            redactRawSmsAfterParse = rawSmsRetention.shouldRedactAfterSuccessfulParse()
                        )
                    }

                    withContext(Dispatchers.Main) {
                        statusText =
                            "Reprocessed ${result.matchedAlertCount} SMS. Created ${result.parsedCount}, updated ${result.changedCount}, unchanged ${result.unchangedCount}, ignored ${result.ignoredNonTransactionCount}, failed ${result.failedCount}."
                    }
                }
            },
            onReapplySavedRules = {
                statusText = "Reapplying saved rules to existing transactions..."

                scope.launch(Dispatchers.IO) {
                    val result = reapplySavedRulesToExistingTransactions(database)

                    withContext(Dispatchers.Main) {
                        statusText = "Reapplied saved rules: scanned ${result.scanned}, changed ${result.changed}, unchanged ${result.unchanged}, skipped ${result.skipped}."
                    }
                }
            },
            onExportTransactions = {
                statusText = "Opening transaction export..."
                onExportTransactions()
            },
            onExportParserDiagnostics = {
                statusText = "Opening parser diagnostics export..."
                onExportParserDiagnostics()
            },
            rawSmsRetention = rawSmsRetention,
            onRawSmsRetentionChanged = onRawSmsRetentionChanged,
            onDeleteExportedFiles = onDeleteExportedFiles,
            onClearAll = {
                scope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        database.transactionDao().deleteAll()
                        database.transactionRuleDao().deleteAll()
                        database.financialSourceDao().deleteAll()
                        database.rawAlertDao().deleteAll()
                    }

                    withContext(Dispatchers.Main) {
                        statusText = "Deleted imported alerts, transactions, banks/cards, and rules from this device."
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

