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

