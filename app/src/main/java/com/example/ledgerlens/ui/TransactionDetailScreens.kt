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
import com.example.ledgerlens.domain.transactions.TransactionEditDraft
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
fun TransactionDetailScreen(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity?,
    allTransactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onSaveTransactionDraft: (TransactionEditDraft, (Throwable?) -> Unit) -> Unit,
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

    var saveState by remember(transaction.id) {
        mutableStateOf<TransactionDetailSaveState>(TransactionDetailSaveState.Idle)
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
                        saveState = TransactionDetailSaveState.Saving
                        onSaveTransactionDraft(
                            currentDraft(
                                merchantDraft = merchantDraft,
                                categoryDraft = categoryDraft,
                                treatmentDraft = treatmentDraft,
                                excludedDraft = excludedDraft,
                                reviewStatusDraft = nextStatus,
                                spendingMerchantDraft = spendingMerchantDraft
                            )
                        ) { error ->
                            saveState = if (error == null) {
                                TransactionDetailSaveState.Saved(
                                    if (nextStatus == "REVIEWED") "Marked reviewed" else "Marked as needs review"
                                )
                            } else {
                                TransactionDetailSaveState.Failed(error.message ?: "Could not save review status.")
                            }
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
                            saveState = TransactionDetailSaveState.Saving
                            onSaveTransactionDraft(
                                currentDraft(merchantDraft, "Transfer", treatment, true, "REVIEWED", "")
                            ) { error ->
                                saveState = if (error == null) {
                                    TransactionDetailSaveState.Saved("Saved as personal transfer")
                                } else {
                                    TransactionDetailSaveState.Failed(error.message ?: "Could not save transfer.")
                                }
                            }
                        },
                        onMarkReimbursement = { category, spendingMerchant ->
                            treatmentDraft = TransactionTreatments.REIMBURSEMENT
                            excludedDraft = false
                            categoryDraft = category
                            spendingMerchantDraft = spendingMerchant
                            reviewStatusDraft = "REVIEWED"
                            saveState = TransactionDetailSaveState.Saving
                            onSaveTransactionDraft(
                                currentDraft(merchantDraft, category, TransactionTreatments.REIMBURSEMENT, false, "REVIEWED", spendingMerchant)
                            ) { error ->
                                saveState = if (error == null) {
                                    TransactionDetailSaveState.Saved("Saved as reimbursement")
                                } else {
                                    TransactionDetailSaveState.Failed(error.message ?: "Could not save reimbursement.")
                                }
                            }
                        },
                        onMarkExpense = { category, spendingMerchant ->
                            treatmentDraft = TransactionTreatments.EXPENSE
                            excludedDraft = false
                            categoryDraft = category
                            spendingMerchantDraft = spendingMerchant
                            reviewStatusDraft = "REVIEWED"
                            saveState = TransactionDetailSaveState.Saving
                            onSaveTransactionDraft(
                                currentDraft(merchantDraft, category, TransactionTreatments.EXPENSE, false, "REVIEWED", spendingMerchant)
                            ) { error ->
                                saveState = if (error == null) {
                                    TransactionDetailSaveState.Saved("Saved as shared expense")
                                } else {
                                    TransactionDetailSaveState.Failed(error.message ?: "Could not save shared expense.")
                                }
                            }
                        },
                        onMarkIncomeGift = {
                            treatmentDraft = TransactionTreatments.INCOME
                            excludedDraft = true
                            categoryDraft = "Income"
                            spendingMerchantDraft = ""
                            reviewStatusDraft = "REVIEWED"
                            saveState = TransactionDetailSaveState.Saving
                            onSaveTransactionDraft(
                                currentDraft(merchantDraft, "Income", TransactionTreatments.INCOME, true, "REVIEWED", "")
                            ) { error ->
                                saveState = if (error == null) {
                                    TransactionDetailSaveState.Saved("Saved as income or gift")
                                } else {
                                    TransactionDetailSaveState.Failed(error.message ?: "Could not save income/gift.")
                                }
                            }
                        }
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        saveState = TransactionDetailSaveState.Saving
                        onSaveTransactionDraft(
                            currentDraft(
                                merchantDraft = merchantDraft,
                                categoryDraft = categoryDraft,
                                treatmentDraft = treatmentDraft,
                                excludedDraft = excludedDraft,
                                reviewStatusDraft = reviewStatusDraft,
                                spendingMerchantDraft = spendingMerchantDraft
                            )
                        ) { error ->
                            saveState = if (error == null) {
                                TransactionDetailSaveState.Saved("Saved changes")
                            } else {
                                TransactionDetailSaveState.Failed(error.message ?: "Could not save changes.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saveState is TransactionDetailSaveState.Saving) "Saving..." else "Save changes")
                }
            }

            when (val state = saveState) {
                TransactionDetailSaveState.Idle -> Unit
                TransactionDetailSaveState.Saving -> {
                    item {
                        InlineInfoPanel(
                            title = "Saving",
                            body = "Saving changes..."
                        )
                    }
                }
                is TransactionDetailSaveState.Saved -> {
                    item {
                        InlineInfoPanel(
                            title = "Saved",
                            body = state.message
                        )
                    }
                }
                is TransactionDetailSaveState.Failed -> {
                    item {
                        InlineInfoPanel(
                            title = "Could not save",
                            body = state.message
                        )
                    }
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

sealed interface TransactionDetailSaveState {
    data object Idle : TransactionDetailSaveState
    data object Saving : TransactionDetailSaveState
    data class Saved(val message: String) : TransactionDetailSaveState
    data class Failed(val message: String) : TransactionDetailSaveState
}

private fun currentDraft(
    merchantDraft: String,
    categoryDraft: String,
    treatmentDraft: String,
    excludedDraft: Boolean,
    reviewStatusDraft: String,
    spendingMerchantDraft: String
): TransactionEditDraft {
    return TransactionEditDraft(
        merchantName = merchantDraft,
        categoryName = categoryDraft,
        accountingTreatment = treatmentDraft,
        excludedFromSpending = excludedDraft,
        reviewStatus = reviewStatusDraft,
        spendingMerchantName = spendingMerchantDraft
    )
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
                    text = formatMoney(transaction.amountCents, transaction.currency),
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
    ).joinToString(" - ").ifBlank { "SMS alert" }

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
            DetailRow("Amount", formatMoney(transaction.amountCents, transaction.currency))
            DetailRow("Date", formatter.format(Date(transaction.occurredAtEpochMs)))
            DetailRow("Source / Account", sourceText)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "Message used",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                TextButton(onClick = onToggleOriginalSms) {
                    Text(if (showOriginalSms) "Hide" else "View")
                }
            }

            if (showOriginalSms) {
                Text(
                    text = rawAlert?.combinedText ?: "Message was not found.",
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
                text = "How to count this",
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
                text = "Included in spending",
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
                    "Current spending impact: ${formatSignedMoney(spendingImpact, transaction.currency)}"
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
                text = "Message used",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (rawAlert == null) {
                Text(
                    text = "Message was not found.",
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
                    if (currentImpact == 0L) {
                        "Outside spending"
                    } else {
                        "Impact ${formatSignedMoney(currentImpact, transaction.currency)}"
                    }
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
                    text = "Message preview:",
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


