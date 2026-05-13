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


