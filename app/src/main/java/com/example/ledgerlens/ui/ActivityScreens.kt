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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    transactions: List<TransactionEntity>,
    onNavigate: (AppScreen) -> Unit,
    onSyncSmsAlerts: () -> Unit,
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
                it.reviewStatus == ReviewStatus.NEEDS_REVIEW
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
    val needsReviewCount = visibleTransactionsForCounts.count { it.reviewStatus == ReviewStatus.NEEDS_REVIEW }
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
    val filteredImpactByCurrency = filteredTransactions
        .groupBy { it.currency.uppercase(Locale.US) }
        .mapValues { (_, group) ->
            group.sumOf {
                TransactionTreatments.spendingImpactCents(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending,
                    amountCents = it.amountCents
                )
            }
        }
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { activityDateHeaderLabel(it.occurredAtEpochMs) }
    }

    LedgerAppScaffold(
        title = "Activity",
        activeScreen = AppScreen.TRANSACTIONS,
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
                    filteredImpactText = formatCurrencyTotals(filteredImpactByCurrency)
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
    filteredImpactText: String
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
            text = "Net impact $filteredImpactText",
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
    val needsReview = transaction.reviewStatus == ReviewStatus.NEEDS_REVIEW
    val showsTreatmentBadge = transaction.accountingTreatment != TransactionTreatments.EXPENSE
    val outsideSpending = !TransactionTreatments.isInSpendingView(
        treatment = transaction.accountingTreatment,
        excludedFromSpending = transaction.excludedFromSpending
    )

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
                    if (showsTreatmentBadge) {
                        ActivitySmallBadge(
                            text = treatmentLabel(transaction.accountingTreatment),
                            color = Color(0xFF7B8AA0)
                        )
                    }
                    if (outsideSpending && transaction.accountingTreatment == TransactionTreatments.EXPENSE) {
                        ActivitySmallBadge(
                            text = "Outside spending",
                            color = Color(0xFF7B8AA0)
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
    return when (transaction.accountingTreatment) {
        TransactionTreatments.INCOME -> formatMoney(transaction.amountCents, transaction.currency, signed = true)
        TransactionTreatments.REFUND,
        TransactionTreatments.REIMBURSEMENT -> formatMoney(-transaction.amountCents, transaction.currency, signed = true)
        else -> formatMoney(transaction.amountCents, transaction.currency)
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
        trailingText = formatMoney(transaction.amountCents, transaction.currency),
        trailingSupportingText = if (spendingImpact == 0L) {
            "Outside spending"
        } else {
            "Impact ${formatSignedMoney(spendingImpact, transaction.currency)}"
        },
        leadingText = transaction.displayMerchantName
            ?.take(1)
            ?: transaction.sourceInstitution?.take(1)
            ?: transaction.currency.take(1),
        onClick = onClick
    )
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

