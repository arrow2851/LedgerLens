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
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.ReviewStatus
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

fun defaultReviewQueueFilter(transactions: List<TransactionEntity>): ReviewQueueFilter? {
    val issueTransactions = transactions.filter { hasAnyReviewIssue(it) }
    return when {
        transactions.any { hasMissingCategory(it) } -> ReviewQueueFilter.MISSING_CATEGORY
        transactions.any { it.reviewStatus == ReviewStatus.NEEDS_REVIEW } -> ReviewQueueFilter.NEEDS_REVIEW
        issueTransactions.any { hasLowConfidence(it) } -> ReviewQueueFilter.LOW_CONFIDENCE
        issueTransactions.any {
            it.accountingTreatment in setOf(
                TransactionTreatments.PERSON_TO_PERSON,
                TransactionTreatments.TRANSFER,
                TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER,
                TransactionTreatments.REIMBURSEMENT
            )
        } -> ReviewQueueFilter.POSSIBLE_TRANSFERS
        issueTransactions.any { hasMissingMerchant(it) } -> ReviewQueueFilter.MISSING_MERCHANT
        issueTransactions.isNotEmpty() -> ReviewQueueFilter.ALL_ISSUES
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
    transactions: List<TransactionEntity>,
    onNavigate: (AppScreen) -> Unit,
    onSyncSmsAlerts: () -> Unit,
    onBack: () -> Unit,
    onTransactionSelected: (TransactionEntity) -> Unit
) {
    var selectedFilter by remember {
        mutableStateOf<ReviewQueueFilter?>(null)
    }

    val defaultFilter = remember(transactions) {
        defaultReviewQueueFilter(transactions)
    }

    LaunchedEffect(defaultFilter) {
        if (selectedFilter == null) {
            selectedFilter = defaultFilter
        }
    }

    val allIssueTransactions = remember(transactions) {
        transactions
            .filter { hasAnyReviewIssue(it) }
            .sortedByDescending { it.occurredAtEpochMs }
    }

    val needsReviewTransactions = remember(transactions) {
        transactions
            .filter { it.reviewStatus == ReviewStatus.NEEDS_REVIEW }
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
                    TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER,
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

    val needsReviewCount = transactions.count { it.reviewStatus == ReviewStatus.NEEDS_REVIEW }
    val missingMerchantCount = transactions.count { hasMissingMerchant(it) }
    val missingCategoryCount = transactions.count { hasMissingCategory(it) }
    val lowConfidenceCount = transactions.count { hasLowConfidence(it) }
    val merchantCategoryCount = remember(transactions) {
        merchantSummaries(transactions).count { it.uncategorizedCount > 0 }
    }
    val missingCategoryAmountText = remember(missingCategoryTransactions) {
        val byCurrency = missingCategoryTransactions
            .groupBy { it.currency.uppercase(Locale.US) }
            .mapValues { entry ->
                entry.value.sumOf {
                    TransactionTreatments.spendingImpactCents(
                        treatment = it.accountingTreatment,
                        excludedFromSpending = it.excludedFromSpending,
                        amountCents = it.amountCents
                    )
                }
        }
        formatCurrencyTotals(byCurrency)
    }

    val selectedListTitle = when (selectedFilter) {
        ReviewQueueFilter.ALL_ISSUES -> "All review items"
        ReviewQueueFilter.NEEDS_REVIEW -> "Transactions need review"
        ReviewQueueFilter.MISSING_MERCHANT -> "Missing merchant"
        ReviewQueueFilter.MISSING_CATEGORY -> "Uncategorized spending"
        ReviewQueueFilter.POSSIBLE_TRANSFERS -> "Possible payments"
        ReviewQueueFilter.LOW_CONFIDENCE -> "Needs confirmation"
        null -> null
    }

    LedgerAppScaffold(
        title = "Review",
        activeScreen = AppScreen.REVIEW_QUEUE,
        onNavigate = onNavigate,
        onSyncSmsAlerts = onSyncSmsAlerts,
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
                        metric = missingCategoryAmountText,
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
                        title = "Possible payments",
                        description = "Check person-to-person items, possible payments, reimbursements, and transfers.",
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
                        title = "Nothing needs review",
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

    val issues = buildList {
        if (transaction.reviewStatus == ReviewStatus.NEEDS_REVIEW) add("Needs review")
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
        trailingText = formatMoney(transaction.amountCents, transaction.currency),
        trailingSupportingText = "Fix",
        leadingText = "!",
        onClick = onClick
    )
}

