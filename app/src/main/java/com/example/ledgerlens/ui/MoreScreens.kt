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
                        title = "Import via SMS",
                        supportingText = "Import alerts, then review detected senders in Sources.",
                        onClick = onRefreshLatestSms
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

