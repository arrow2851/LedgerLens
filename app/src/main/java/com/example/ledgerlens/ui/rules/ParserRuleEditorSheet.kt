package com.example.ledgerlens.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.rules.MerchantAliasRuleDraft
import com.example.ledgerlens.domain.rules.previewMerchantAliasRule
import com.example.ledgerlens.domain.summary.treatmentLabel
import com.example.ledgerlens.ui.components.LedgerListRow
import com.example.ledgerlens.ui.components.SmallPill
import com.example.ledgerlens.ui.components.TreatmentSelector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRuleEditorSheet(
    title: String,
    initialDraft: MerchantAliasRuleDraft,
    transactions: List<TransactionEntity>,
    rawAlerts: List<RawAlertEntity>,
    onDismiss: () -> Unit,
    onApply: (MerchantAliasRuleDraft) -> Unit
) {
    var canonicalName by remember(initialDraft) {
        mutableStateOf(initialDraft.canonicalMerchantName)
    }
    var aliasText by remember(initialDraft) {
        mutableStateOf(initialDraft.aliases.joinToString("\n"))
    }
    var applyCategory by remember(initialDraft) {
        mutableStateOf(initialDraft.applyCategory)
    }
    var categoryName by remember(initialDraft) {
        mutableStateOf(initialDraft.categoryName.orEmpty())
    }
    var applyTreatment by remember(initialDraft) {
        mutableStateOf(initialDraft.applyTreatment)
    }
    var treatment by remember(initialDraft) {
        mutableStateOf(initialDraft.transactionType ?: TransactionTreatments.EXPENSE)
    }
    var requiresReview by remember(initialDraft) {
        mutableStateOf(initialDraft.requiresReview)
    }
    var active by remember(initialDraft) {
        mutableStateOf(initialDraft.active)
    }

    val draft = MerchantAliasRuleDraft(
        sourceKey = initialDraft.sourceKey,
        canonicalMerchantName = canonicalName,
        aliases = aliasText.lines(),
        applyCategory = applyCategory,
        categoryName = categoryName,
        applyTreatment = applyTreatment,
        transactionType = treatment,
        requiresReview = requiresReview,
        active = active
    )
    val rawAlertsById = remember(rawAlerts) {
        rawAlerts.associateBy { it.id }
    }
    val previewItems = remember(draft, transactions, rawAlertsById) {
        previewMerchantAliasRule(
            draft = draft,
            transactions = transactions,
            rawAlertsById = rawAlertsById
        )
    }
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Teach LedgerLens aliases that should become one merchant/payee. Existing transactions are only updated after this preview.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = canonicalName,
                onValueChange = { canonicalName = it },
                label = { Text("Canonical merchant or payee") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = aliasText,
                onValueChange = { aliasText = it },
                label = { Text("Aliases, one per line") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            CheckboxRow(
                checked = applyCategory,
                label = "Also apply category when not manually edited",
                onCheckedChange = { applyCategory = it }
            )

            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                enabled = applyCategory,
                label = { Text("Category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            CheckboxRow(
                checked = applyTreatment,
                label = "Also apply accounting treatment when not manually edited",
                onCheckedChange = { applyTreatment = it }
            )

            if (applyTreatment) {
                TreatmentSelector(
                    selectedTreatment = treatment,
                    onTreatmentSelected = { treatment = it },
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
            }

            CheckboxRow(
                checked = requiresReview,
                label = "Require review after this rule matches",
                onCheckedChange = { requiresReview = it }
            )

            CheckboxRow(
                checked = active,
                label = "Rule active",
                onCheckedChange = { active = it }
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        SmallPill("${previewItems.size} matches")
                    }

                    val merchantUpdates = previewItems.count { it.willUpdateMerchant }
                    val skippedMerchant = previewItems.count { it.skippedMerchantUserEdited }
                    Text(
                        text = "$merchantUpdates merchant names will update. $skippedMerchant manually edited names will be skipped.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    previewItems.take(6).forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                        val transaction = item.transaction
                        LedgerListRow(
                            title = transaction.displayMerchantName
                                ?: transaction.merchantRaw
                                ?: "Unknown merchant",
                            supportingText = formatter.format(Date(transaction.occurredAtEpochMs)),
                            metadataText = "Matched ${item.matchedAliases.joinToString(", ")}",
                            trailingText = "$${"%.2f".format(Locale.US, transaction.amountCents / 100.0)}",
                            trailingSupportingText = treatmentLabel(transaction.accountingTreatment),
                            leadingText = "M"
                        )
                    }

                    if (previewItems.size > 6) {
                        Text(
                            text = "And ${previewItems.size - 6} more.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onApply(draft)
                    },
                    enabled = canonicalName.isNotBlank() && draft.cleanedAliases.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save rule")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
