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
@Composable
fun MerchantSummaryCard(
    merchant: MerchantSummary,
    onClick: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

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
                text = "Activity total: ${formatMoney(merchant.totalAmountCents, merchant.currency)}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Net spending impact: ${formatSignedMoney(merchant.spendingAmountCents, merchant.currency)}",
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
            Text("Net spending impact: ${formatSignedMoney(expenseTotalCents, merchant.currency)}")
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
                text = "How to count this",
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
                text = "${formatMoney(transaction.amountCents, transaction.currency)} - ${treatmentLabel(transaction.accountingTreatment)}",
                style = MaterialTheme.typography.bodyLarge
            )

            if (spendingImpact != 0L) {
                Text(
                    text = "Spending impact: ${formatSignedMoney(spendingImpact, transaction.currency)}",
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
