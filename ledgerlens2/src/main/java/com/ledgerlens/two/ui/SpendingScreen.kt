package com.ledgerlens.two.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerlens.two.data.FinancialSource
import com.ledgerlens.two.data.LedgerTransaction
import com.ledgerlens.two.data.MonthlyBudget
import com.ledgerlens.two.domain.FinanceEngine
import com.ledgerlens.two.domain.Movement
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

private val palette = listOf(
    Color(0xFF2CAA81), Color(0xFF5E6CD7), Color(0xFFE09B57),
    Color(0xFF44A3BF), Color(0xFF9C76BC), Color(0xFFCA6D78), Color(0xFF5A9379)
)

@Composable
internal fun SpendingScreen(
    transactions: List<LedgerTransaction>,
    sources: List<FinancialSource>,
    budgets: List<MonthlyBudget>,
    month: String, currency: String, onMonth: (String) -> Unit,
    onReview: () -> Unit, onTransaction: (LedgerTransaction) -> Unit
) {
    val monthTx = remember(transactions, month, currency) {
        transactions.filter { monthOf(it) == month && it.currency == currency }
    }
    val eligible = remember(monthTx, sources) {
        monthTx.filter { impact(it, sources) != 0L }
    }
    val currentSpend = eligible.sumOf { impact(it, sources) }
    val gross = eligible.filter { impact(it, sources) > 0 }.sumOf { impact(it, sources) }
    val returns = eligible.filter { impact(it, sources) < 0 }.sumOf { -impact(it, sources) }
    val categoryTotals = eligible
        .groupBy { it.category?.takeIf(String::isNotBlank) ?: "Uncategorized" }
        .mapValues { (_, rows) -> rows.sumOf { impact(it, sources) } }
        .entries.sortedByDescending { it.value }
    val knownMerchants = eligible
        .groupBy { it.merchant?.ifBlank { null } ?: "Unidentified merchant" }
        .mapValues { (_, rows) -> rows.sumOf { impact(it, sources) } }
        .entries.sortedByDescending { it.value }.take(5)
    val current = YearMonth.now()
    val selected = YearMonth.parse(month)
    val previous = selected.minusMonths(1).toString()
    val dayCut = if (selected == current) LocalDate.now().dayOfMonth else 31
    val prevSpend = transactions.filter { t ->
        monthOf(t) == previous && t.currency == currency &&
            Instant.ofEpochMilli(t.occurredAtMs).atZone(ZoneId.systemDefault())
                .dayOfMonth <= dayCut
    }.sumOf { impact(it, sources) }
    val comparison = when {
        prevSpend <= 0L -> "No comparable previous period"
        else -> {
            val diff = (currentSpend - prevSpend) * 100.0 / abs(prevSpend)
            (if (diff < 0) "↓ " else "↑ ") +
                "%.1f".format(Locale.US, abs(diff)) + "% vs equivalent previous period"
        }
    }
    val reviewCount = monthTx.count { issues(it, sources).isNotEmpty() }
    val recurring = remember(transactions, sources, currency) {
        FinanceEngine.recurring(transactions.filter {
            it.currency == currency && it.kind in listOf(Movement.EXPENSE, Movement.HOLD) && impact(it, sources) > 0
        }.mapNotNull { t ->
            t.merchant?.let { Triple(it, t.amountMinor, t.occurredAtMs) }
        })
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        MonthPicker(month, onMonth)
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(DeepPine, Pine, Color(0xFF2A987D))),
                RoundedCornerShape(25.dp)
            )
        ) {
            Column(Modifier.padding(23.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NET SPENDING", color = Color(0xFFD3F4E9),
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 1.3.sp)
                Text(money(currentSpend, currency), color = Color.White,
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                Text(comparison, color = Color(0xFFD8F1E8),
                    style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = Color.White.copy(alpha = .25f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Gross purchases", color = Color(0xFFD8F1E8),
                            style = MaterialTheme.typography.labelSmall)
                        Text(money(gross, currency), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Refund offsets", color = Color(0xFFD8F1E8),
                            style = MaterialTheme.typography.labelSmall)
                        Text("−" + money(returns, currency), color = Color.White,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (reviewCount > 0) {
            Surface(onClick = onReview, shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFFF4E8)) {
                Row(Modifier.fillMaxWidth().padding(17.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$reviewCount transactions need attention",
                            color = Color(0xFF7B4D1D), fontWeight = FontWeight.SemiBold)
                        SmallLabel("Review unresolved sources, merchants, categories and payments")
                    }
                    Text("Review ›", color = Amber, fontWeight = FontWeight.Bold)
                }
            }
        }
        Panel(title = "Spending by category",
            supporting = "Charges and charge-or-hold alerts from approved SMS sources") {
            val chartValues = categoryTotals.filter { it.value > 0L }
            if (chartValues.isEmpty()) {
                SmallLabel("No expense or charge-or-hold alerts from approved sources for this month.")
            } else {
                val total = chartValues.sumOf { it.value }.toFloat().coerceAtLeast(1f)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(215.dp)) {
                        var angle = -90f
                        chartValues.forEachIndexed { idx, row ->
                            val sweep = (row.value.toFloat() / total) * 360f
                            drawArc(
                                palette[idx % palette.size], angle, sweep - 1.5f,
                                useCenter = false, style = Stroke(width = 34.dp.toPx(),
                                    cap = StrokeCap.Butt)
                            )
                            angle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(money(currentSpend, currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        SmallLabel("Net spending")
                    }
                }
                categoryTotals.forEachIndexed { idx, (name, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).background(palette[idx % palette.size],
                                RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(money(value, currency), fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(
                        progress = { (abs(value).toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = palette[idx % palette.size],
                        trackColor = Color(0xFFF1F4F2)
                    )
                }
            }
        }
        Panel(title = "Top merchants", supporting = "Ranked by actual spending, not review count") {
            if (knownMerchants.isEmpty()) {
                SmallLabel("Merchants appear after you approve sources and identify expenses.")
            }
            knownMerchants.forEachIndexed { index, (name, amount) ->
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((index + 1).toString().padStart(2, '0'), color = Pine,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(name, modifier = Modifier.widthIn(max = 205.dp),
                            maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(money(amount, currency), fontWeight = FontWeight.Bold)
                }
                if (index < knownMerchants.lastIndex) HorizontalDivider(color = CanvasColor)
            }
        }
        val monthBudgets = budgets.filter { it.month == month && it.currency == currency }
        Panel(title = "Monthly budgets", supporting = "Category limits are configurable in More") {
            if (monthBudgets.isEmpty()) SmallLabel("No category budgets set for this month.")
            monthBudgets.forEach { budget ->
                val spent = (categoryTotals.firstOrNull { it.key == budget.category }?.value ?: 0L)
                    .coerceAtLeast(0L)
                Text(budget.category, fontWeight = FontWeight.Medium)
                LinearProgressIndicator(
                    progress = { (spent.toFloat() / budget.limitMinor).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = if (spent > budget.limitMinor) Coral else Pine
                )
                SmallLabel(money(spent, currency) + " of " + money(budget.limitMinor, currency))
            }
        }
        Panel(title = "Recurring activity",
            supporting = "Suggestions only, based on similar charges in three or more months") {
            if (recurring.isEmpty()) SmallLabel("Not enough repeated monthly merchant history yet.")
            recurring.take(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.merchantKey, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(row.medianMinor, currency), fontWeight = FontWeight.Bold)
                        SmallLabel(row.months.toString() + " months")
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}
