package com.ledgerlens.two.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerlens.two.data.FinancialSource
import com.ledgerlens.two.data.LedgerTransaction
import com.ledgerlens.two.domain.Movement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TransactionRow(
    t: LedgerTransaction,
    sources: List<FinancialSource>,
    onOpen: () -> Unit,
    showIssues: Boolean = false
) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(17.dp), color = Color.White) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(
                    if (t.kind == Movement.EXPENSE) Color(0xFFE0F4E9)
                    else Color(0xFFEAE8FC), RoundedCornerShape(13.dp)
                ), contentAlignment = Alignment.Center
            ) {
                Text((t.merchant?.firstOrNull() ?: '?').uppercaseChar().toString(),
                    color = if (t.kind == Movement.EXPENSE) Pine else Violet,
                    fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(t.merchant?.takeIf { it.isNotBlank() } ?: when (t.kind) {
                    Movement.INCOME -> "Incoming deposit"
                    Movement.CARD_PAYMENT -> "Card payment"
                    Movement.TRANSFER -> "Transfer"
                    Movement.P2P_REVIEW -> "Payment to classify"
                    Movement.HOLD -> "Pending card hold"
                    Movement.SCHEDULED -> "Scheduled payment"
                    else -> "Merchant to confirm"
                },
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                SmallLabel((t.category ?: t.kind.replace('_', ' ').lowercase()
                    .replaceFirstChar(Char::uppercaseChar)) +
                    " · " + dateLabel(t.occurredAtMs))
                if (showIssues) {
                    val flags = issues(t, sources)
                    if (flags.isNotEmpty()) {
                        Text(flags.take(3).joinToString(" • "), color = Amber,
                            style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.width(5.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(money(t.amountMinor, t.currency), fontWeight = FontWeight.Bold)
                if (!isApproved(t, sources)) SmallLabel("Unapproved")
                else if (impact(t, sources) == 0L) SmallLabel("Outside spending")
                else SmallLabel("In spending", Pine)
            }
        }
    }
}

@Composable
internal fun ReviewScreen(
    transactions: List<LedgerTransaction>,
    sources: List<FinancialSource>,
    currency: String,
    onSources: () -> Unit,
    onTransaction: (LedgerTransaction) -> Unit
) {
    val all = remember(transactions, sources, currency) {
        transactions.filter { it.currency == currency && issues(it, sources).isNotEmpty() }
    }
    var filter by remember { mutableStateOf("All") }
    val types = listOf("All", "Source approval", "Payment type", "Category",
        "Merchant", "Possible duplicate", "Low confidence")
    val filtered = all.filter { filter == "All" || filter in issues(it, sources) }
    val categoryCost = all.filter { "Category" in issues(it, sources) }
        .sumOf { if (isApproved(it, sources)) it.amountMinor else 0L }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Eyebrow("Make one decision, teach the app")
            Text("Review center", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold)
        }
        item {
            Panel(title = "Work that needs your attention",
                supporting = "Independent issue flags prevent category edits from hiding payment uncertainty") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(all.size.toString(), style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold, color = Pine)
                        SmallLabel("Unique transactions")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(categoryCost, currency),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        SmallLabel("Uncategorized approved expenses")
                    }
                }
                val pendingSources = sources.count { !it.approved }
                if (pendingSources > 0) {
                    Button(onClick = onSources, modifier = Modifier.fillMaxWidth()) {
                        Text("Review " + pendingSources + " unapproved SMS sources")
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(types) { kind ->
                    FilterChip(
                        selected = filter == kind, onClick = { filter = kind },
                        label = {
                            val count = if (kind == "All") all.size
                                else all.count { kind in issues(it, sources) }
                            Text(kind + " " + count)
                        }
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Panel(title = "Queue clear") {
                    SmallLabel("No transactions in this review category.")
                }
            }
        } else {
            items(filtered, key = { it.id }) { transaction ->
                TransactionRow(
                    t = transaction, sources = sources,
                    showIssues = true, onOpen = { onTransaction(transaction) }
                )
            }
        }
    }
}

@Composable
internal fun ActivityScreen(
    transactions: List<LedgerTransaction>,
    sources: List<FinancialSource>,
    month: String, currency: String, onMonth: (String) -> Unit,
    onTransaction: (LedgerTransaction) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var allMonths by remember { mutableStateOf(false) }
    val options = listOf("All", "Expenses", "Income", "Transfers", "Refunds", "Pending", "Review")
    val filtered = remember(transactions, sources, month, currency, allMonths, search, filter) {
        transactions.filter { t ->
            t.currency == currency && (allMonths || monthOf(t) == month) &&
                (search.isBlank() || listOfNotNull(t.merchant, t.category, t.kind, t.sender)
                    .any { it.contains(search.trim(), ignoreCase = true) }) &&
                when (filter) {
                    "Expenses" -> t.kind == Movement.EXPENSE
                    "Income" -> t.kind == Movement.INCOME
                    "Transfers" -> t.kind in listOf(
                        Movement.TRANSFER, Movement.P2P_REVIEW, Movement.CARD_PAYMENT
                    )
                    "Refunds" -> t.kind in listOf(Movement.REFUND, Movement.REIMBURSEMENT)
                    "Pending" -> t.kind in listOf(Movement.HOLD, Movement.SCHEDULED)
                    "Review" -> issues(t, sources).isNotEmpty()
                    else -> true
                }
        }
    }
    val sum = filtered.sumOf { impact(it, sources) }
    val group = filtered.groupBy {
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(it.occurredAtMs))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Eyebrow("Searchable financial history")
            Text("Activity", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold)
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Find a merchant, category or bank") },
                placeholder = { Text("Search transactions") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            if (!allMonths) MonthPicker(month, onMonth)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = allMonths, onCheckedChange = { allMonths = it })
                Spacer(Modifier.width(10.dp))
                Text("All months", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(options) { name ->
                    FilterChip(selected = filter == name, onClick = { filter = name },
                        label = { Text(name) })
                }
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(filtered.size.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold)
                        SmallLabel("Transactions")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(sum, currency, true),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold)
                        SmallLabel("Net spending impact")
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item { Panel { SmallLabel("No transactions match this search or time period.") } }
        }
        group.forEach { (date, rows) ->
            item(key = "date-" + date) {
                Spacer(Modifier.height(2.dp))
                Text(date, color = Muted, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            items(rows, key = { it.id }) { t ->
                TransactionRow(t, sources, onOpen = { onTransaction(t) })
            }
        }
    }
}
