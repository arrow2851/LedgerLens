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
fun SourceListScreen(
    sources: List<FinancialSourceEntity>,
    onBack: () -> Unit,
    onSourceSelected: (FinancialSourceEntity) -> Unit
) {
    var searchText by remember {
        mutableStateOf("")
    }

    val visibleSources = remember(sources, searchText) {
        val query = searchText.trim().lowercase(Locale.US)
        if (query.isBlank()) {
            sources
        } else {
            sources.filter { source ->
                listOfNotNull(
                    source.displayName,
                    source.institutionName,
                    source.sourceAddress,
                    source.accountHint,
                    source.suggestedAccountType,
                    source.confirmedAccountType,
                    source.sampleMessage
                ).any { it.lowercase(Locale.US).contains(query) }
            }
        }
    }

    val uncategorizedSources = visibleSources.filter { !it.userConfirmed && !it.ignored }
    val identifiedSources = visibleSources.filter { it.userConfirmed && !it.ignored }
    val nonSources = visibleSources.filter { it.ignored }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
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

                Text(
                    text = "Review SMS senders and classify them as identified sources, non-sources, or uncategorized possible sources.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search sources") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                SourceSectionHeader(
                    title = "Uncategorized Possible Sources",
                    count = uncategorizedSources.size
                )
            }

            if (uncategorizedSources.isEmpty()) {
                item {
                    EmptySectionText("No uncategorized possible sources.")
                }
            } else {
                items(
                    items = uncategorizedSources,
                    key = { it.sourceKey }
                ) { source ->
                    SourceCompactCard(
                        source = source,
                        categoryLabel = "Uncategorized",
                        onClick = { onSourceSelected(source) }
                    )
                }
            }

            item {
                SourceSectionHeader(
                    title = "Identified Sources",
                    count = identifiedSources.size
                )
            }

            if (identifiedSources.isEmpty()) {
                item {
                    EmptySectionText("No identified sources yet.")
                }
            } else {
                items(
                    items = identifiedSources,
                    key = { it.sourceKey }
                ) { source ->
                    SourceCompactCard(
                        source = source,
                        categoryLabel = "Identified",
                        onClick = { onSourceSelected(source) }
                    )
                }
            }

            item {
                SourceSectionHeader(
                    title = "Non-Sources",
                    count = nonSources.size
                )
            }

            if (nonSources.isEmpty()) {
                item {
                    EmptySectionText("No non-sources yet.")
                }
            } else {
                items(
                    items = nonSources,
                    key = { it.sourceKey }
                ) { source ->
                    SourceCompactCard(
                        source = source,
                        categoryLabel = "Non-Source",
                        onClick = { onSourceSelected(source) }
                    )
                }
            }
        }
    }
}

@Composable
fun SourceSectionHeader(
    title: String,
    count: Int
) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun EmptySectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SourceCompactCard(
    source: FinancialSourceEntity,
    categoryLabel: String,
    onClick: () -> Unit
) {
    val effectiveType = source.confirmedAccountType
        ?: source.suggestedAccountType

    LedgerListRow(
        title = source.displayName
            ?: source.institutionName
            ?: "Unknown financial source",
        supportingText = "$categoryLabel - Type: $effectiveType - Messages: ${source.messageCount}",
        metadataText = "Sender ${source.sourceAddress} - ${"%.0f".format(source.detectionConfidence * 100)}% confidence",
        pillText = categoryLabel,
        leadingText = source.sourceAddress.take(2),
        trailingText = "Review",
        onClick = onClick
    )
    return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = source.displayName
                    ?: source.institutionName
                    ?: "Unknown financial source",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$categoryLabel • Type: $effectiveType • Messages: ${source.messageCount}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Sender: ${source.sourceAddress}",
                style = MaterialTheme.typography.labelSmall
            )

            if (!source.accountHint.isNullOrBlank()) {
                Text(
                    text = "Detected account/card hints: ${source.accountHint}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = "Confidence: ${"%.0f".format(source.detectionConfidence * 100)}%",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Tap to review SMS and change category",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailScreen(
    source: FinancialSourceEntity,
    matchingAlerts: List<RawAlertEntity>,
    onBack: () -> Unit,
    onMarkSourceType: (String) -> Unit,
    onDismissAsNonSource: () -> Unit,
    onMoveToUncategorized: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    }

    val effectiveType = source.confirmedAccountType
        ?: source.suggestedAccountType

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source Detail") },
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

                SourceDetailSummaryCard(
                    source = source,
                    effectiveType = effectiveType,
                    matchingCount = matchingAlerts.size
                )
            }

            item {
                SourceActionCard(
                    onMarkSourceType = onMarkSourceType,
                    onDismissAsNonSource = onDismissAsNonSource,
                    onMoveToUncategorized = onMoveToUncategorized
                )
            }

            item {
                Text(
                    text = "Matching SMS (${matchingAlerts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (matchingAlerts.isEmpty()) {
                item {
                    EmptySectionText("No matching SMS found for this source.")
                }
            } else {
                items(
                    items = matchingAlerts,
                    key = { it.notificationKey }
                ) { alert ->
                    SmsMessageCard(
                        alert = alert,
                        formatter = formatter
                    )
                }
            }
        }
    }
}

@Composable
fun SourceDetailSummaryCard(
    source: FinancialSourceEntity,
    effectiveType: String,
    matchingCount: Int
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
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
                text = source.displayName
                    ?: source.institutionName
                    ?: "Unknown financial source",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text("Sender: ${source.sourceAddress}")
            Text("Suggested type: ${source.suggestedAccountType}")
            Text("Confirmed type: ${source.confirmedAccountType ?: "Not confirmed"}")
            Text("Effective type: $effectiveType")
            Text("Matching SMS: $matchingCount")

            if (!source.accountHint.isNullOrBlank()) {
                Text("Detected account/card hints: ${source.accountHint}")
            }

            Text("Confidence: ${"%.0f".format(source.detectionConfidence * 100)}%")
            Text("First seen: ${formatter.format(Date(source.firstSeenEpochMs))}")
            Text("Last seen: ${formatter.format(Date(source.lastSeenEpochMs))}")

            val category = when {
                source.ignored -> "Non-Source"
                source.userConfirmed -> "Identified Source"
                else -> "Uncategorized Possible Source"
            }

            Text("Current category: $category")
        }
    }
}

@Composable
fun SourceActionCard(
    onMarkSourceType: (String) -> Unit,
    onDismissAsNonSource: () -> Unit,
    onMoveToUncategorized: () -> Unit
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
                text = "Choose Source Category",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onMarkSourceType("CREDIT_CARD") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Credit Card")
                }

                Button(
                    onClick = { onMarkSourceType("CHECKING") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Checking")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onMarkSourceType("SAVINGS") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Savings")
                }

                Button(
                    onClick = { onMarkSourceType("DEBIT_CARD") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Debit Card")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onMarkSourceType("UNKNOWN") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Valid Source, Type Unknown")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDismissAsNonSource,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss as Non-Source")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onMoveToUncategorized,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Move Back to Uncategorized")
            }
        }
    }
}

@Composable
fun SmsMessageCard(
    alert: RawAlertEntity,
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
                text = formatter.format(Date(alert.postTimeEpochMs)),
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Status: ${alert.processingStatus}",
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = alert.combinedText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

