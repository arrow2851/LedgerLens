package com.ledgerlens.two.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.two.data.EditRequest
import com.ledgerlens.two.data.FinancialSource
import com.ledgerlens.two.data.LedgerTransaction
import com.ledgerlens.two.domain.Movement

private val treatments = listOf(
    Movement.EXPENSE to "Expense",
    Movement.INCOME to "Income",
    Movement.REFUND to "Refund",
    Movement.REIMBURSEMENT to "Reimbursement",
    Movement.CARD_PAYMENT to "Credit-card payment",
    Movement.TRANSFER to "Internal transfer",
    Movement.P2P_REVIEW to "Unresolved person-to-person payment",
    Movement.HOLD to "Unsettled card authorization",
    Movement.SCHEDULED to "Scheduled future payment"
)

@Composable
internal fun TransactionEditor(
    transaction: LedgerTransaction,
    sources: List<FinancialSource>,
    onDismiss: () -> Unit,
    onSave: (EditRequest) -> Unit,
    onSaveMerchantRule: (String, String) -> Unit,
    onDuplicate: (Boolean) -> Unit
) {
    var merchant by remember(transaction.id) { mutableStateOf(transaction.merchant.orEmpty()) }
    var category by remember(transaction.id) { mutableStateOf(transaction.category.orEmpty()) }
    var kind by remember(transaction.id) { mutableStateOf(transaction.kind) }
    var excluded by remember(transaction.id) { mutableStateOf(transaction.excluded) }
    var showKinds by remember { mutableStateOf(false) }
    val canAffectSpending = kind in listOf(
        Movement.EXPENSE, Movement.REFUND, Movement.REIMBURSEMENT
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Transaction details", fontWeight = FontWeight.Bold)
                SmallLabel(money(transaction.amountMinor, transaction.currency) +
                    " · " + dateLabel(transaction.occurredAtMs))
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                val flags = issues(transaction, sources)
                if (flags.isNotEmpty()) {
                    Text("Still needs: " + flags.joinToString(" • "),
                        color = Amber, style = MaterialTheme.typography.labelMedium)
                }
                SmallLabel("SMS sender: " + transaction.sender)
                SmallLabel("Recognition confidence: " +
                    (transaction.confidence * 100).toInt().toString() + "%")
                SmallLabel(transaction.reason)
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("Merchant or payee") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CategoryDropdown(category, onCategory = { category = it })
                SmallLabel("Financial treatment")
                Box {
                    OutlinedButton(onClick = { showKinds = true },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(treatments.firstOrNull { it.first == kind }?.second ?: kind,
                            modifier = Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(expanded = showKinds,
                        onDismissRequest = { showKinds = false }) {
                        treatments.forEach { (key, title) ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    kind = key
                                    excluded = key !in listOf(
                                        Movement.EXPENSE, Movement.REFUND, Movement.REIMBURSEMENT
                                    )
                                    showKinds = false
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Exclude from spending", fontWeight = FontWeight.Medium)
                        SmallLabel("Also always excluded if source is unapproved or treatment is not an expense/refund.")
                    }
                    Switch(
                        checked = excluded || !canAffectSpending,
                        onCheckedChange = { excluded = it },
                        enabled = canAffectSpending
                    )
                }
                if (transaction.duplicateOfId != null &&
                    !transaction.duplicateDismissed && !transaction.excluded) {
                    HorizontalDivider()
                    Text("Possible duplicate", fontWeight = FontWeight.Bold)
                    SmallLabel("Another financial alert has the same source, amount and time window. No rows have been deleted automatically.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onDuplicate(false) }) {
                            Text("Distinct charge")
                        }
                        Button(onClick = { onDuplicate(true) }) {
                            Text("Exclude duplicate")
                        }
                    }
                }
                HorizontalDivider()
                Text("Teach LedgerLens2", fontWeight = FontWeight.SemiBold)
                SmallLabel("Create a merchant default after confirming the category. It updates only non-user-edited transactions.")
                OutlinedButton(
                    onClick = { onSaveMerchantRule(merchant, category) },
                    enabled = merchant.isNotBlank() && category.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Apply category to this merchant's history") }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(EditRequest(
                    merchant = merchant.trim().ifBlank { null },
                    category = category.trim().ifBlank { null },
                    kind = kind, excluded = excluded
                ))
            }) { Text("Save correction") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
