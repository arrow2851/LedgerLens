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

