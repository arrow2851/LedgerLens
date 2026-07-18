package com.example.ledgerlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.TransactionEntity
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class LedgerLensActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getInstance(applicationContext)

        setContent {
            LedgerLensTheme {
                LedgerLensApp(database = database)
            }
        }
    }
}

private val Ink = Color(0xFF15313A)
private val InkMuted = Color(0xFF6A7C80)
private val Background = Color(0xFFF3F7F4)
private val SurfaceColor = Color(0xFFFFFFFF)
private val Line = Color(0xFFDDE8E2)
private val Teal = Color(0xFF08766E)
private val TealSoft = Color(0xFFE7F5F1)
private val DeepTeal = Color(0xFF123F49)
private val Amber = Color(0xFFC57524)
private val AmberSoft = Color(0xFFFFF3E3)
private val Positive = Color(0xFF2D7D5B)
private val Negative = Color(0xFFA64C59)

private val categoryColors = mapOf(
    "Groceries" to Color(0xFF159B8E),
    "Dining" to Color(0xFFB85B73),
    "Transport" to Color(0xFF4B83BA),
    "Shopping" to Color(0xFF765CAD),
    "Bills" to Color(0xFFB58A30),
    "Other" to Color(0xFF7C8B90),
)

@Composable
private fun LedgerLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            onPrimary = Color.White,
            primaryContainer = TealSoft,
            onPrimaryContainer = DeepTeal,
            background = Background,
            onBackground = Ink,
            surface = SurfaceColor,
            onSurface = Ink,
            surfaceVariant = Color(0xFFEEF4F1),
            onSurfaceVariant = InkMuted,
            outline = Line,
            error = Negative,
        ),
        typography = MaterialTheme.typography.copy(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.7).sp,
            ),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            ),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
        ),
        content = content,
    )
}

private enum class MainTab(val label: String, val glyph: String) {
    OVERVIEW("Overview", "⌂"),
    ACTIVITY("Activity", "≡"),
    REVIEW("Review", "✓"),
    INSIGHTS("Insights", "◔"),
}

private enum class ActivityFilter(val label: String) {
    ALL("All"),
    SPENDING("Spending"),
    INCOME("Income"),
    MOVED("Money moved"),
    REVIEW("Needs review"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerLensApp(database: AppDatabase) {
    val transactions by database.transactionDao().observeAll().collectAsState(initial = emptyList())
    var selectedTab by remember { mutableStateOf(MainTab.OVERVIEW) }
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    val reviewCount = transactions.count(::needsReview)

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "LedgerLens",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = selectedTab.label.uppercase(Locale.US),
                            color = InkMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 14.dp, end = 8.dp)
                            .size(42.dp)
                            .background(
                                color = Teal,
                                shape = RoundedCornerShape(15.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("LL", color = Color.White, fontWeight = FontWeight.Black)
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier.padding(end = 14.dp),
                        color = TealSoft,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = "On-device",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            color = Teal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    scrolledContainerColor = Background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = SurfaceColor,
                tonalElevation = 0.dp,
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Text(
                                    text = tab.glyph,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (tab == MainTab.REVIEW && reviewCount > 0) {
                                    Surface(
                                        modifier = Modifier
                                            .padding(start = 14.dp)
                                            .size(18.dp),
                                        color = Negative,
                                        shape = CircleShape,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = reviewCount.coerceAtMost(99).toString(),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.OVERVIEW -> OverviewScreen(
                transactions = transactions,
                modifier = Modifier.padding(innerPadding),
                onTransactionClick = { selectedTransaction = it },
                onReviewClick = { selectedTab = MainTab.REVIEW },
            )

            MainTab.ACTIVITY -> ActivityScreen(
                transactions = transactions,
                modifier = Modifier.padding(innerPadding),
                onTransactionClick = { selectedTransaction = it },
            )

            MainTab.REVIEW -> ReviewScreen(
                transactions = transactions,
                database = database,
                modifier = Modifier.padding(innerPadding),
                onTransactionClick = { selectedTransaction = it },
            )

            MainTab.INSIGHTS -> InsightsScreen(
                transactions = transactions,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    selectedTransaction?.let { transaction ->
        TransactionEditorDialog(
            transaction = transaction,
            database = database,
            onDismiss = { selectedTransaction = null },
        )
    }
}

@Composable
private fun OverviewScreen(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier,
    onTransactionClick: (TransactionEntity) -> Unit,
    onReviewClick: () -> Unit,
) {
    val monthTransactions = remember(transactions) { currentMonthTransactions(transactions) }
    val categories = remember(monthTransactions) { buildCategorySummaries(monthTransactions) }
    val total = categories.sumOf { it.amountCents }
    val reviewCount = transactions.count(::needsReview)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonthControl()
        }

        item {
            CategoryHero(
                totalCents = total,
                categories = categories,
            )
        }

        if (reviewCount > 0) {
            item {
                ReviewBanner(
                    count = reviewCount,
                    onClick = onReviewClick,
                )
            }
        }

        item {
            SectionHeader(
                title = "Recent activity",
                supporting = "${monthTransactions.size} this month",
            )
        }

        if (monthTransactions.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No activity yet",
                    body = "Your identified financial alerts will appear here after import and parsing.",
                )
            }
        } else {
            items(monthTransactions.take(6), key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                )
            }
        }
    }
}

@Composable
private fun MonthControl() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = SurfaceColor,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        ) {
            Text(
                text = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()),
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Included spending only",
            color = InkMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CategoryHero(
    totalCents: Long,
    categories: List<CategorySummary>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            Text(
                text = "THIS MONTH",
                color = Teal,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
            )
            Text(
                text = "Where your money went",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = formatMoney(totalCents),
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )

            if (categories.isEmpty()) {
                Text(
                    text = "Category composition will appear once transactions are identified.",
                    modifier = Modifier.padding(top = 10.dp),
                    color = InkMuted,
                )
            } else {
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryDonut(
                        categories = categories,
                        totalCents = max(1L, totalCents),
                        modifier = Modifier.size(142.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        categories.take(5).forEach { category ->
                            CategoryLegendRow(
                                category = category,
                                totalCents = max(1L, totalCents),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDonut(
    categories: List<CategorySummary>,
    totalCents: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            var startAngle = -90f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

            categories.forEach { category ->
                val sweep = (category.amountCents.toFloat() / totalCents.toFloat()) * 360f
                drawArc(
                    color = category.color,
                    startAngle = startAngle,
                    sweepAngle = sweep.coerceAtLeast(1.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = categories.firstOrNull()?.name ?: "Spending",
                color = InkMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = categories.firstOrNull()?.let { formatMoney(it.amountCents) } ?: "$0",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CategoryLegendRow(
    category: CategorySummary,
    totalCents: Long,
) {
    val share = ((category.amountCents.toDouble() / totalCents.toDouble()) * 100).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(category.color, CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${category.count} transactions",
                color = InkMuted,
                fontSize = 9.sp,
            )
        }
        Text(
            text = "$share%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReviewBanner(
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = AmberSoft,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBD8BF)),
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFFFE9CE), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = Amber, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$count transaction${if (count == 1) "" else "s"} need review",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    text = "Confirm merchant and category details to keep totals accurate.",
                    color = InkMuted,
                    fontSize = 10.sp,
                )
            }
            Text(
                text = "Review",
                color = Teal,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ActivityScreen(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier,
    onTransactionClick: (TransactionEntity) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }
    val filtered = remember(transactions, selectedFilter) {
        transactions.filter { transaction ->
            when (selectedFilter) {
                ActivityFilter.ALL -> true
                ActivityFilter.SPENDING -> spendingContribution(transaction) != 0L
                ActivityFilter.INCOME -> transaction.transactionType.equals("INCOME", ignoreCase = true)
                ActivityFilter.MOVED -> isMoneyMovement(transaction)
                ActivityFilter.REVIEW -> needsReview(transaction)
            }
        }
    }
    val grouped = remember(filtered) { groupTransactions(filtered) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            ActivitySummary(transactions)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label, fontSize = 11.sp) },
                    )
                }
            }
        }

        if (grouped.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Nothing matches this filter",
                    body = "Try a different activity filter.",
                )
            }
        } else {
            grouped.forEach { group ->
                item(key = "header-${group.label}") {
                    SectionHeader(
                        title = group.label,
                        supporting = "${group.transactions.size} item${if (group.transactions.size == 1) "" else "s"}",
                    )
                }
                items(group.transactions, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        onClick = { onTransactionClick(transaction) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivitySummary(transactions: List<TransactionEntity>) {
    val month = currentMonthTransactions(transactions)
    val spending = month.sumOf(::spendingContribution)
    val income = month
        .filter { it.transactionType.equals("INCOME", ignoreCase = true) }
        .sumOf { abs(it.amountCents) }
    val moved = month.filter(::isMoneyMovement).sumOf { abs(it.amountCents) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DeepTeal),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            Text(
                text = "JULY ACTIVITY",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
            )
            Text(
                text = formatMoney(spending),
                modifier = Modifier.padding(top = 3.dp),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${month.size} transactions · ${month.count(::needsReview)} need review",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DarkMetric("Money in", formatMoney(income), Modifier.weight(1f))
                DarkMetric("Moved", formatMoney(moved), Modifier.weight(1f))
                DarkMetric("Review", month.count(::needsReview).toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DarkMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.09f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label.uppercase(Locale.US),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = value,
                modifier = Modifier.padding(top = 3.dp),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ReviewScreen(
    transactions: List<TransactionEntity>,
    database: AppDatabase,
    modifier: Modifier = Modifier,
    onTransactionClick: (TransactionEntity) -> Unit,
) {
    val reviewItems = remember(transactions) { transactions.filter(::needsReview) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FOCUSED REVIEW",
                            color = Teal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.1.sp,
                        )
                        Text(
                            text = "Keep the ledger clean",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "Only uncertain items appear here. Open one to correct its merchant or category.",
                            modifier = Modifier.padding(top = 5.dp),
                            color = InkMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Surface(
                        color = AmberSoft,
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = reviewItems.size.toString(),
                                color = Amber,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "LEFT",
                                color = Amber,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }

        if (reviewItems.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "All caught up",
                    body = "There are no transactions waiting for review.",
                )
            }
        } else {
            items(reviewItems, key = { it.id }) { transaction ->
                ReviewTransactionCard(
                    transaction = transaction,
                    onOpen = { onTransactionClick(transaction) },
                    onConfirm = {
                        scope.launch {
                            database.transactionDao().updateReviewStatus(
                                transactionId = transaction.id,
                                reviewStatus = "REVIEWED",
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReviewTransactionCard(
    transaction: TransactionEntity,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(21.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(transaction = transaction)
                Spacer(modifier = Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = merchantName(transaction),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = reviewReason(transaction),
                        color = Amber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = signedAmount(transaction),
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Line,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text("Edit details", fontSize = 11.sp)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text("Confirm", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun InsightsScreen(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier,
) {
    val month = remember(transactions) { currentMonthTransactions(transactions) }
    val categories = remember(month) { buildCategorySummaries(month) }
    val total = categories.sumOf { it.amountCents }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepTeal),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(17.dp)) {
                    Text(
                        text = "CLEAR EXPLANATIONS",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp,
                    )
                    Text(
                        text = "July at a glance",
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Insights use confirmed spending only. Income and money moved are kept separate.",
                        modifier = Modifier.padding(top = 5.dp),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }
            }
        }

        item {
            SectionHeader("Category distribution", formatMoney(total))
        }

        if (categories.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Not enough data yet",
                    body = "Insights will become available as transactions are categorized.",
                )
            }
        } else {
            items(categories, key = { it.name }) { category ->
                InsightCategoryRow(category = category, totalCents = max(1L, total))
            }
        }

        item {
            TrustBreakdown(transactions = month)
        }
    }
}

@Composable
private fun InsightCategoryRow(
    category: CategorySummary,
    totalCents: Long,
) {
    val share = category.amountCents.toFloat() / totalCents.toFloat()

    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(category.color, CircleShape),
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = category.name,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatMoney(category.amountCents),
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(7.dp)
                    .background(Color(0xFFEAF0ED), CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .height(7.dp)
                        .background(category.color, CircleShape),
                )
            }
            Text(
                text = "${(share * 100).toInt()}% of spending · ${category.count} transactions",
                modifier = Modifier.padding(top = 6.dp),
                color = InkMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun TrustBreakdown(transactions: List<TransactionEntity>) {
    val included = transactions.sumOf { max(0L, spendingContribution(it)) }
    val refunds = transactions.sumOf { minOf(0L, spendingContribution(it)) }
    val moved = transactions.filter(::isMoneyMovement).sumOf { abs(it.amountCents) }
    val income = transactions
        .filter { it.transactionType.equals("INCOME", ignoreCase = true) }
        .sumOf { abs(it.amountCents) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                text = "HOW TOTALS ARE BUILT",
                color = Teal,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
            )
            Text(
                text = "Clear boundaries, accurate spending",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            TrustRow("Included spending", included, Teal)
            TrustRow("Refunds", refunds, Positive)
            TrustRow("Money moved", moved, categoryColors.getValue("Other"))
            TrustRow("Income", income, categoryColors.getValue("Transport"))
        }
    }
}

@Composable
private fun TrustRow(
    label: String,
    amountCents: Long,
    color: Color,
) {
    Row(
        modifier = Modifier.padding(top = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(9.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = formatMoney(amountCents),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = SurfaceColor,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryMark(transaction = transaction)
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = merchantName(transaction),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = transactionSubtitle(transaction),
                    color = InkMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (needsReview(transaction)) {
                    Text(
                        text = "Needs review",
                        modifier = Modifier.padding(top = 3.dp),
                        color = Amber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = signedAmount(transaction),
                    color = when {
                        spendingContribution(transaction) < 0 -> Positive
                        transaction.transactionType.equals("INCOME", ignoreCase = true) -> Positive
                        else -> Ink
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Text(
                    text = spendingEffectLabel(transaction),
                    color = InkMuted,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun CategoryMark(transaction: TransactionEntity) {
    val category = normalizedCategory(transaction.categoryName)
    val color = categoryColors[category] ?: categoryColors.getValue("Other")
    val glyph = when {
        transaction.transactionType.equals("INCOME", ignoreCase = true) -> "+"
        isMoneyMovement(transaction) -> "↔"
        else -> category.take(2).uppercase(Locale.US)
    }

    Box(
        modifier = Modifier
            .size(45.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    supporting: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = supporting,
            color = InkMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceColor,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                text = body,
                modifier = Modifier.padding(top = 5.dp),
                color = InkMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TransactionEditorDialog(
    transaction: TransactionEntity,
    database: AppDatabase,
    onDismiss: () -> Unit,
) {
    var merchant by remember(transaction.id) {
        mutableStateOf(transaction.displayMerchantName ?: transaction.merchantRaw.orEmpty())
    }
    var category by remember(transaction.id) {
        mutableStateOf(transaction.categoryName.orEmpty())
    }
    var applyCategoryToMerchant by remember(transaction.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Edit transaction")
                Text(
                    text = signedAmount(transaction),
                    color = InkMuted,
                    fontSize = 13.sp,
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Merchant") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    label = { Text("Category") },
                    placeholder = { Text("Groceries, Dining, Transport…") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clickable { applyCategoryToMerchant = !applyCategoryToMerchant },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        color = if (applyCategoryToMerchant) Teal else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (applyCategoryToMerchant) Teal else Line,
                        ),
                    ) {
                        if (applyCategoryToMerchant) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✓", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = "Apply this category to existing transactions from the same merchant",
                        fontSize = 10.sp,
                        color = InkMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        database.transactionDao().updateMerchant(
                            transactionId = transaction.id,
                            merchantRaw = merchant.trim().ifBlank { null },
                            displayMerchantName = merchant.trim().ifBlank { null },
                            updatedAtEpochMs = now,
                        )
                        database.transactionDao().updateCategory(
                            transactionId = transaction.id,
                            categoryName = category.trim().ifBlank { null },
                            subcategoryName = transaction.subcategoryName,
                            updatedAtEpochMs = now,
                        )
                        database.transactionDao().updateReviewStatus(
                            transactionId = transaction.id,
                            reviewStatus = "REVIEWED",
                            updatedAtEpochMs = now,
                        )
                        if (applyCategoryToMerchant && merchant.isNotBlank()) {
                            database.transactionDao().updateCategoryForMerchantName(
                                merchantName = merchant.trim(),
                                categoryName = category.trim().ifBlank { null },
                                subcategoryName = transaction.subcategoryName,
                                updatedAtEpochMs = now,
                            )
                        }
                        onDismiss()
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = SurfaceColor,
    )
}

private data class CategorySummary(
    val name: String,
    val amountCents: Long,
    val count: Int,
    val color: Color,
)

private data class TransactionGroup(
    val label: String,
    val transactions: List<TransactionEntity>,
)

private fun currentMonthTransactions(
    transactions: List<TransactionEntity>,
    nowEpochMs: Long = System.currentTimeMillis(),
): List<TransactionEntity> {
    val now = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
    return transactions.filter { transaction ->
        val calendar = Calendar.getInstance().apply { timeInMillis = transaction.occurredAtEpochMs }
        calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            calendar.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    }
}

private fun buildCategorySummaries(
    transactions: List<TransactionEntity>,
): List<CategorySummary> {
    return transactions
        .mapNotNull { transaction ->
            val contribution = spendingContribution(transaction)
            if (contribution <= 0L) null
            else normalizedCategory(transaction.categoryName) to contribution
        }
        .groupBy({ it.first }, { it.second })
        .map { (category, amounts) ->
            CategorySummary(
                name = category,
                amountCents = amounts.sum(),
                count = amounts.size,
                color = categoryColors[category] ?: categoryColors.getValue("Other"),
            )
        }
        .sortedByDescending { it.amountCents }
}

private fun groupTransactions(
    transactions: List<TransactionEntity>,
): List<TransactionGroup> {
    val dayFormat = SimpleDateFormat("MMM d", Locale.US)
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun sameDay(epochMs: Long, calendar: Calendar): Boolean {
        val value = Calendar.getInstance().apply { timeInMillis = epochMs }
        return value.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
            value.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
    }

    return transactions
        .groupBy { transaction ->
            when {
                sameDay(transaction.occurredAtEpochMs, today) -> "Today"
                sameDay(transaction.occurredAtEpochMs, yesterday) -> "Yesterday"
                else -> dayFormat.format(Date(transaction.occurredAtEpochMs))
            }
        }
        .map { (label, values) -> TransactionGroup(label, values) }
}

private fun spendingContribution(transaction: TransactionEntity): Long {
    if (transaction.excludedFromSpending) return 0L

    val type = transaction.transactionType.uppercase(Locale.US)
    val amount = abs(transaction.amountCents)

    return when (type) {
        "REFUND", "REVERSAL", "REIMBURSEMENT" -> -amount
        "INCOME", "TRANSFER", "CREDIT_CARD_PAYMENT", "INFORMATIONAL" -> 0L
        "EXPENSE", "PURCHASE", "FEE", "CASH_WITHDRAWAL" -> amount
        else -> if (type.contains("EXPENSE") || type.contains("PURCHASE")) amount else 0L
    }
}

private fun isMoneyMovement(transaction: TransactionEntity): Boolean {
    val type = transaction.transactionType.uppercase(Locale.US)
    return type == "TRANSFER" ||
        type == "CREDIT_CARD_PAYMENT" ||
        transaction.excludedFromSpending
}

private fun needsReview(transaction: TransactionEntity): Boolean {
    val review = transaction.reviewStatus.uppercase(Locale.US)
    return review != "REVIEWED" &&
        review != "CONFIRMED" &&
        review != "APPROVED"
}

private fun merchantName(transaction: TransactionEntity): String {
    return transaction.displayMerchantName
        ?.takeIf { it.isNotBlank() }
        ?: transaction.merchantRaw?.takeIf { it.isNotBlank() }
        ?: "Unassigned merchant"
}

private fun reviewReason(transaction: TransactionEntity): String {
    return when {
        merchantName(transaction) == "Unassigned merchant" -> "Merchant name needed"
        transaction.categoryName.isNullOrBlank() && spendingContribution(transaction) != 0L ->
            "Category needed"
        transaction.parseConfidence < 0.75 -> "Low-confidence match"
        else -> "Confirmation needed"
    }
}

private fun normalizedCategory(category: String?): String {
    val raw = category?.trim().orEmpty()
    if (raw.isBlank()) return "Other"

    return categoryColors.keys.firstOrNull { it.equals(raw, ignoreCase = true) } ?: "Other"
}

private fun transactionSubtitle(transaction: TransactionEntity): String {
    val category = normalizedCategory(transaction.categoryName)
    val account = transaction.accountHint
        ?.takeIf { it.isNotBlank() }
        ?: transaction.sourceInstitution?.takeIf { it.isNotBlank() }
        ?: "Account not identified"
    return "$category · $account"
}

private fun spendingEffectLabel(transaction: TransactionEntity): String {
    val contribution = spendingContribution(transaction)
    return when {
        contribution > 0L -> "Spending"
        contribution < 0L -> "Reduces spending"
        transaction.transactionType.equals("INCOME", ignoreCase = true) -> "Money in"
        else -> "Not spending"
    }
}

private fun signedAmount(transaction: TransactionEntity): String {
    val amount = formatMoney(abs(transaction.amountCents))
    return when {
        transaction.transactionType.equals("INCOME", ignoreCase = true) -> "+$amount"
        spendingContribution(transaction) < 0L -> "+$amount"
        else -> "−$amount"
    }
}

private fun formatMoney(amountCents: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(amountCents.toDouble() / 100.0)
}
