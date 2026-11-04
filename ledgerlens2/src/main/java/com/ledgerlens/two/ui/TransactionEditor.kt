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
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    evidence: String?,
    onDismiss: () -> Unit,
    onSave: (EditRequest) -> Unit,
    onSaveMerchantRule: (String, String) -> Unit,
    onDuplicate: (Boolean) -> Unit
) {
    var merchant by remember(transaction.id) { mutableStateOf(transaction.merchant.orEmpty()) }
    var accountHint by remember(transaction.id) { mutableStateOf(transaction.accountHint.orEmpty()) }
    var category by remember(transaction.id) { mutableStateOf(transaction.category.orEmpty()) }
    var kind by remember(transaction.id) { mutableStateOf(transaction.kind) }
    var excluded by remember(transaction.id) { mutableStateOf(transaction.excluded) }
    var showKinds by remember { mutableStateOf(false) }
    var amountText by remember(transaction.id) {
        mutableStateOf("%.2f".format(Locale.US, transaction.amountMinor / 100.0))
    }
    var dateText by remember(transaction.id) {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(transaction.occurredAtMs)))
    }
    var validationError by remember { mutableStateOf("") }
    var showOriginal by remember { mutableStateOf(false) }
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
                OutlinedTextField(
                    value = accountHint, onValueChange = { accountHint = it },
                    label = { Text("Account/card last 4 digits (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                SmallLabel("Recognition confidence: " +
                    (transaction.confidence * 100).toInt().toString() + "%")
                SmallLabel(transaction.reason)
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount (" + transaction.currency + ")") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateText, onValueChange = { dateText = it },
                    label = { Text("Transaction date/time (yyyy-MM-dd HH:mm)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                SmallLabel("Corrections are recorded in the local audit trail; the original SMS remains unchanged.")
                TextButton(onClick = { showOriginal = !showOriginal }) {
                    Text(if (showOriginal) "Hide original SMS evidence" else "Show original SMS evidence")
                }
                if (showOriginal) {
                    Text(evidence ?: "Message loading or unavailable",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (validationError.isNotBlank()) {
                    Text(validationError, color = Coral, style = MaterialTheme.typography.labelSmall)
                }
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
                val minor = runCatching {
                    BigDecimal(amountText.trim()).multiply(BigDecimal(100))
                        .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
                }.getOrNull()
                val correctedTime = runCatching {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                        isLenient = false
                    }.parse(dateText.trim())?.time
                }.getOrNull()
                if (minor == null || minor <= 0L || correctedTime == null) {
                    validationError = "Enter a valid positive amount and a valid date/time."
                } else {
                    validationError = ""
                    onSave(EditRequest(
                        merchant = merchant.trim().ifBlank { null },
                        accountHint = accountHint.trim().ifBlank { null },
                        category = category.trim().ifBlank { null },
                        kind = kind, excluded = excluded,
                        amountMinor = minor, occurredAtMs = correctedTime
                    ))
                }
            }) { Text("Save correction") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
