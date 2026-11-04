package com.ledgerlens.two.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerlens.two.LedgerViewModel
import com.ledgerlens.two.data.EditRequest
import com.ledgerlens.two.data.FinancialSource
import com.ledgerlens.two.data.LedgerTransaction
import com.ledgerlens.two.domain.FinanceEngine
import com.ledgerlens.two.domain.Movement
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.util.Date
import java.util.Locale

internal val Pine = Color(0xFF11695D)
internal val DeepPine = Color(0xFF08463D)
internal val Ink = Color(0xFF172823)
internal val Muted = Color(0xFF587069)
internal val CanvasColor = Color(0xFFF3F7F5)
internal val Amber = Color(0xFFA5661D)
internal val Violet = Color(0xFF735AC4)
internal val Coral = Color(0xFFB65748)

private val modernScheme = lightColorScheme(
    primary = Pine, onPrimary = Color.White, primaryContainer = Color(0xFFD7F1E6),
    onPrimaryContainer = DeepPine, secondary = Violet, secondaryContainer = Color(0xFFEDE7FF),
    background = CanvasColor, onBackground = Ink, surface = Color.White,
    onSurface = Ink, surfaceVariant = Color(0xFFE7EFEB), onSurfaceVariant = Muted,
    outline = Color(0xFFC1D1C9), error = Coral
)

internal fun isApproved(t: LedgerTransaction, sources: List<FinancialSource>): Boolean =
    sources.any { it.sender == t.sourceKey && it.approved }

internal fun impact(t: LedgerTransaction, sources: List<FinancialSource>): Long =
    FinanceEngine.impact(t.kind, t.amountMinor, t.excluded, isApproved(t, sources))

/** Separate, non-destructive issue flags. Resolving a category must not clear a transfer issue. */
internal fun issues(t: LedgerTransaction, sources: List<FinancialSource>): List<String> = buildList {
    if (!isApproved(t, sources)) add("Source approval")
    if (t.kind == Movement.P2P_REVIEW) add("Payment type")
    if (t.kind == Movement.EXPENSE && t.merchant.isNullOrBlank()) add("Merchant")
    if (t.kind == Movement.EXPENSE && t.category.isNullOrBlank()) add("Category")
    if (t.duplicateOfId != null && !t.duplicateDismissed && !t.excluded) add("Possible duplicate")
    if (t.confidence < .70 && !t.verified) add("Low confidence")
}

internal fun monthOf(t: LedgerTransaction): String = FinanceEngine.monthKey(t.occurredAtMs)
internal fun dateLabel(epoch: Long): String =
    SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(epoch))
internal fun money(minor: Long, currency: String, signed: Boolean = false): String =
    FinanceEngine.formatMoney(minor, currency, signed)

internal fun monthText(month: String): String = runCatching {
    val m = YearMonth.parse(month)
    m.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + m.year
}.getOrElse { month }

@Composable
internal fun Panel(
    title: String? = null,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White, shadowElevation = 1.dp,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            if (supporting != null) {
                Text(supporting, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            content()
        }
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(text.uppercase(Locale.getDefault()), color = Pine, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
}

@Composable
internal fun SmallLabel(text: String, color: Color = Muted) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
internal fun MonthPicker(
    month: String, onMonth: (String) -> Unit, modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = { onMonth(YearMonth.parse(month).minusMonths(1).toString()) }) {
            Text("‹", fontSize = 22.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(monthText(month), fontWeight = FontWeight.Bold)
            TextButton(onClick = { onMonth(YearMonth.now().toString()) }) {
                Text("This month")
            }
        }
        OutlinedButton(onClick = { onMonth(YearMonth.parse(month).plusMonths(1).toString()) }) {
            Text("›", fontSize = 22.sp)
        }
    }
}

@Composable
internal fun CurrencyPicker(
    selected: String, available: List<String>, onSelect: (String) -> Unit
) {
    if (available.size <= 1) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        available.forEach { code ->
            FilterChip(
                selected = selected == code, onClick = { onSelect(code) },
                label = { Text(code) }
            )
        }
    }
}

@Composable
fun LedgerApp(
    model: LedgerViewModel,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onExportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    val transactions by model.transactions.collectAsState()
    val sources by model.sources.collectAsState()
    val rules by model.rules.collectAsState()
    val budgets by model.budgets.collectAsState()
    val notice by model.notice.collectAsState()
    val busy by model.busy.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val currencies = remember(transactions) {
        (transactions.map { it.currency } + "USD").distinct().sorted()
    }
    var currency by rememberSaveable { mutableStateOf("USD") }
    var focusedTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    val allIssueCount = remember(transactions, sources) {
        transactions.count { issues(it, sources).isNotEmpty() }
    }
    MaterialTheme(colorScheme = modernScheme, typography = Typography()) {
        Scaffold(
            containerColor = CanvasColor,
            topBar = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().background(CanvasColor)
                            .padding(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("LedgerLens2", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold, color = DeepPine)
                            Text("Your money, made clear", color = Muted,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        if (busy) CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 3.dp)
                        else AssistChip(onClick = onScan, label = { Text("Sync SMS") })
                    }
                    if (notice.isNotBlank()) {
                        Text(
                            notice, modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 5.dp),
                            color = Muted, style = MaterialTheme.typography.labelSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
                    listOf("Spending", "Review", "Activity", "More").forEachIndexed { index, name ->
                        val icon = when (index) {
                            0 -> Icons.Default.AccountBalanceWallet
                            1 -> Icons.Default.AssignmentTurnedIn
                            2 -> Icons.Default.ReceiptLong
                            else -> Icons.Default.MoreHoriz
                        }
                        NavigationBarItem(
                            selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icon, contentDescription = name) },
                            label = {
                                Text(if (index == 1 && allIssueCount > 0)
                                    name + " " + allIssueCount else name)
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                CurrencyPicker(currency, currencies, onSelect = { currency = it })
                when (tab) {
                    0 -> SpendingScreen(transactions, sources, budgets, month, currency,
                        onMonth = { month = it }, onReview = { tab = 1 },
                        onTransaction = { focusedTransaction = it })
                    1 -> ReviewScreen(transactions, sources, currency,
                        onSources = { tab = 3 },
                        onTransaction = { focusedTransaction = it })
                    2 -> ActivityScreen(transactions, sources, month, currency,
                        onMonth = { month = it },
                        onTransaction = { focusedTransaction = it })
                    else -> MoreScreen(
                        transactions = transactions, sources = sources, rules = rules,
                        budgets = budgets, month = month, currency = currency,
                        onSource = model::approveSource,
                        onRuleToggle = model::setRuleEnabled,
                        onBudget = model::saveBudget,
                        onImport = onImport, onScan = onScan,
                        onExportCsv = onExportCsv, onExportBackup = onExportBackup,
                        onRestore = onRestoreBackup, onDelete = model::clearAppData
                    )
                }
            }
        }
        focusedTransaction?.let { current ->
            TransactionEditor(
                transaction = current, sources = sources,
                onDismiss = { focusedTransaction = null },
                onSave = { draft ->
                    model.editTransaction(current.id, draft)
                    focusedTransaction = null
                },
                onSaveMerchantRule = { merchant, category ->
                    model.saveMerchantDefault(merchant, category)
                    focusedTransaction = null
                },
                onDuplicate = { duplicate ->
                    model.decideDuplicate(current.id, duplicate)
                    focusedTransaction = null
                }
            )
        }
    }
}
