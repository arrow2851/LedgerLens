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
import com.example.ledgerlens.domain.privacy.RawSmsRetention
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
fun ToolsScreen(
    rawAlertCount: Int,
    sourceCount: Int,
    transactionCount: Int,
    activeRuleCount: Int,
    transactions: List<TransactionEntity>,
    statusText: String,
    onNavigate: (AppScreen) -> Unit,
    onSyncSmsAlerts: () -> Unit,
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
    onExportParserDiagnostics: () -> Unit,
    rawSmsRetention: RawSmsRetention,
    onRawSmsRetentionChanged: (RawSmsRetention) -> Unit,
    onDeleteExportedFiles: () -> Unit,
    onClearAll: () -> Unit
) {
    var showCategories by remember { mutableStateOf(false) }
    var showMerchants by remember { mutableStateOf(false) }
    var showAdvancedTools by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmParserDiagnosticsExport by remember { mutableStateOf(false) }

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
                categoryTransactions
                    .groupBy { it.currency.uppercase(Locale.US) }
                    .map { (currency, currencyTransactions) ->
                        MoreCategorySummary(
                            name = category,
                            currency = currency,
                            transactionCount = currencyTransactions.size,
                            spendingImpactCents = currencyTransactions.sumOf {
                                TransactionTreatments.spendingImpactCents(
                                    treatment = it.accountingTreatment,
                                    excludedFromSpending = it.excludedFromSpending,
                                    amountCents = it.amountCents
                                )
                            }
                        )
                    }
            }
            .flatten()
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
        onSyncSmsAlerts = onSyncSmsAlerts
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
                        title = "Banks & cards",
                        supportingText = "Choose which SMS senders LedgerLens should use.",
                        onClick = onOpenSources
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "R",
                        title = "Saved rules",
                        supportingText = "Advanced merchant defaults and parser aliases.",
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
                                    supportingText = "${category.transactionCount} transactions - ${category.currency}",
                                    trailingText = formatSignedMoney(category.spendingImpactCents, category.currency),
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
                                    supportingText = "${merchant.transactionCount} transactions - ${merchant.currency}",
                                    trailingText = formatSignedMoney(merchant.spendingAmountCents, merchant.currency),
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
                        leadingText = "S",
                        title = "Sync SMS alerts",
                        supportingText = "Import new financial SMS alerts and update transactions.",
                        onClick = onRefreshLatestSms
                    )
                    MoreDivider()
                    MoreActionRow(
                        leadingText = "E",
                        title = "Export transactions",
                        supportingText = "Share a CSV of your transaction data.",
                        onClick = onExportTransactions
                    )
                }
            }

            item {
                MoreSectionHeader("Privacy")
                MoreSectionCard {
                    MoreActionRow(
                        leadingText = "P",
                        title = "Privacy & security",
                        supportingText = "Message text and transactions stay local on this device.",
                        trailingText = if (showPrivacy) "Hide" else ">",
                        onClick = { showPrivacy = !showPrivacy }
                    )
                }
            }

            if (showPrivacy) {
                item {
                    PrivacyPanel(
                        rawSmsRetention = rawSmsRetention,
                        onRawSmsRetentionChanged = onRawSmsRetentionChanged,
                        onDeleteExportedFiles = onDeleteExportedFiles,
                        onDeleteAllData = { confirmClearAll = true }
                    )
                }
            }

            item {
                MoreSectionHeader("Advanced")
                MoreSectionCard {
                    MoreActionRow(
                        leadingText = "A",
                        title = "Advanced tools",
                        supportingText = "Full history sync, reprocessing, rules, diagnostics, and deletion.",
                        trailingText = if (showAdvancedTools) "Hide" else ">",
                        onClick = { showAdvancedTools = !showAdvancedTools }
                    )
                }
            }

            if (showAdvancedTools) {
                item {
                    MoreSectionCard {
                        MoreActionRow(
                            leadingText = "H",
                            title = "Full SMS history sync",
                            supportingText = "Import older financial SMS messages, then update senders and transactions.",
                            onClick = onBackfillSmsHistory
                        )
                        MoreDivider()
                        MoreActionRow(
                            leadingText = "F",
                            title = "Detect banks & cards",
                            supportingText = "Find possible financial SMS senders from imported messages.",
                            onClick = onDetectSources
                        )
                        MoreDivider()
                        MoreActionRow(
                            leadingText = "P",
                            title = "Process approved senders",
                            supportingText = "Parse transactions from senders you approved.",
                            onClick = onParseIdentifiedSources
                        )
                        MoreDivider()
                        MoreActionRow(
                            leadingText = "A",
                            title = "Reapply saved rules",
                            supportingText = "Update existing transactions using saved rules without overwriting manual edits.",
                            onClick = onReapplySavedRules
                        )
                        MoreDivider()
                        MoreActionRow(
                            leadingText = "B",
                            title = "Reprocess approved senders",
                            supportingText = "Update parser-owned details while preserving your manual edits.",
                            onClick = onReparseTransactions
                        )
                        MoreDivider()
                        if (confirmParserDiagnosticsExport) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Export parser diagnostics?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Text(
                                    text = "This advanced export is redacted by default, but it still may include merchants, balances, account/card hints, sender IDs, and other financial details.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { confirmParserDiagnosticsExport = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel")
                                    }
                                    Button(
                                        onClick = {
                                            confirmParserDiagnosticsExport = false
                                            onExportParserDiagnostics()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Export")
                                    }
                                }
                            }
                        } else {
                            MoreActionRow(
                                leadingText = "D",
                                title = "Export parser diagnostics",
                                supportingText = "Advanced troubleshooting export. Raw SMS text is redacted by default.",
                                trailingText = "Warn",
                                onClick = { confirmParserDiagnosticsExport = true }
                            )
                        }
                        MoreDivider()
                        if (confirmClearAll) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Delete all LedgerLens data?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Text(
                                    text = "Removes $rawAlertCount imported SMS alerts, $transactionCount transactions, $sourceCount banks/cards, and $activeRuleCount rules from this device. This cannot be undone.",
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
                                        Text("Delete")
                                    }
                                }
                            }
                        } else {
                            MoreActionRow(
                                leadingText = "X",
                                title = "Delete all LedgerLens data",
                                supportingText = "Removes imported alerts, transactions, banks/cards, rules, and local app data from this device.",
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
                        leadingText = "?",
                        title = "Help",
                        supportingText = "How sync, review, spending, and exports work.",
                        trailingText = if (showHelp) "Hide" else ">",
                        onClick = { showHelp = !showHelp }
                    )
                }
            }

            if (showHelp) {
                item {
                    HelpPanel()
                }
            }
        }
    }
}

data class MoreCategorySummary(
    val name: String,
    val currency: String,
    val transactionCount: Int,
    val spendingImpactCents: Long
)

@Composable
fun PrivacyPanel(
    rawSmsRetention: RawSmsRetention,
    onRawSmsRetentionChanged: (RawSmsRetention) -> Unit,
    onDeleteExportedFiles: () -> Unit,
    onDeleteAllData: () -> Unit
) {
    MoreSectionCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Privacy & security",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            MoreBodyText("LedgerLens stores SMS alerts, parsed transactions, banks/cards, and rules locally on this device. Android backup is disabled for this app.")
            MoreBodyText("Transaction CSV exports do not include original SMS text. Parser diagnostics are advanced exports and are redacted by default.")
            MoreBodyText("Delete all data removes imported alerts, transactions, banks/cards, and rules from this device.")

            MoreDivider()
            MoreBodyText("Original SMS text")
            MoreActionRow(
                leadingText = if (rawSmsRetention == RawSmsRetention.KEEP_FOR_AUDIT) "✓" else "",
                title = "Keep for audit/debugging",
                supportingText = "Transaction detail can show the message LedgerLens used.",
                onClick = { onRawSmsRetentionChanged(RawSmsRetention.KEEP_FOR_AUDIT) }
            )
            MoreDivider()
            MoreActionRow(
                leadingText = if (rawSmsRetention == RawSmsRetention.REDACT_AFTER_PARSE) "✓" else "",
                title = "Redact after successful parse",
                supportingText = "Replace stored original SMS text after a transaction is created.",
                onClick = { onRawSmsRetentionChanged(RawSmsRetention.REDACT_AFTER_PARSE) }
            )
            MoreDivider()
            MoreActionRow(
                leadingText = "E",
                title = "Delete exported files",
                supportingText = "Remove LedgerLens CSV and diagnostics files from app-controlled export folders.",
                onClick = onDeleteExportedFiles
            )
            MoreDivider()
            MoreActionRow(
                leadingText = "X",
                title = "Delete all LedgerLens data",
                supportingText = "Shows a confirmation before removing local LedgerLens data.",
                trailingText = "Confirm",
                onClick = onDeleteAllData
            )
        }
    }
}

@Composable
fun HelpPanel() {
    MoreSectionCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "How LedgerLens works",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            MoreBodyText("Sync SMS alerts imports financial-looking inbox messages and updates transactions from senders you approved.")
            MoreBodyText("Banks & cards lets you choose which SMS senders LedgerLens should use. Ignored senders are not parsed in future syncs.")
            MoreBodyText("Review is where you fix merchants, categories, uncertain transactions, possible transfers, card payments, refunds, and reimbursements.")
            MoreBodyText("Spending includes expenses and subtracts refunds/reimbursements. Transfers, card payments, income, and excluded items do not inflate spending.")
            MoreBodyText("Export transactions creates a CSV without original SMS text. Parser diagnostics are for troubleshooting and live under Advanced.")
        }
    }
}

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
                text = "$transactionCount transactions from $sourceCount banks/cards",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                text = "$rawAlertCount imported SMS alerts - $activeRuleCount saved rules",
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

