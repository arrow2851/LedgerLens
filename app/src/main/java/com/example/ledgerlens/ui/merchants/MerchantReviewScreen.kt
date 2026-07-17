package com.example.ledgerlens.ui.merchants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.merchants.CategoryOption
import com.example.ledgerlens.domain.merchants.MerchantReviewItem
import com.example.ledgerlens.domain.merchants.buildCategoryCatalog
import com.example.ledgerlens.domain.merchants.buildMerchantReviewItems
import com.example.ledgerlens.domain.merchants.optionMatchesSuggestion
import com.example.ledgerlens.domain.summary.MerchantSummary
import com.example.ledgerlens.domain.summary.isVirtualUncategorizedCategory
import com.example.ledgerlens.domain.summary.treatmentLabel
import com.example.ledgerlens.ui.formatMoney
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MerchantSelectionFilter {
    ALL,
    SELECTED,
    UNSELECTED
}

private enum class MerchantSortMode {
    PRIORITY,
    AMOUNT,
    LATEST,
    NAME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantReviewScreen(
    transactions: List<TransactionEntity>,
    rules: List<TransactionRuleEntity>,
    onBack: () -> Unit,
    onMerchantSelected: (MerchantSummary) -> Unit,
    onApplyCategoryToMerchants: (Set<String>, CategoryOption) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CategoryOption?>(null) }
    var selectedMerchantNames by remember { mutableStateOf(emptySet<String>()) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var customOptions by remember { mutableStateOf(emptyList<CategoryOption>()) }
    var selectionFilter by remember { mutableStateOf(MerchantSelectionFilter.ALL) }
    var sortMode by remember { mutableStateOf(MerchantSortMode.PRIORITY) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val categoryOptions = remember(transactions, rules, customOptions) {
        buildCategoryCatalog(
            transactions = transactions,
            rules = rules,
            customOptions = customOptions
        )
    }
    val reviewItems = remember(transactions, rules) {
        buildMerchantReviewItems(
            transactions = transactions,
            rules = rules
        )
    }
    val needsCategoryItems = remember(reviewItems) {
        reviewItems.filter { it.needsCategory }
    }

    val visibleItems = remember(
        needsCategoryItems,
        searchText,
        selectedCategory,
        selectedMerchantNames,
        selectionFilter,
        sortMode
    ) {
        val query = searchText.trim().lowercase(Locale.US)
        needsCategoryItems
            .filter { item ->
                query.isBlank() ||
                        item.summary.merchantName.lowercase(Locale.US).contains(query) ||
                        item.suggestion.label.lowercase(Locale.US).contains(query)
            }
            .filter { item ->
                when (selectionFilter) {
                    MerchantSelectionFilter.ALL -> true
                    MerchantSelectionFilter.SELECTED -> item.summary.merchantName in selectedMerchantNames
                    MerchantSelectionFilter.UNSELECTED -> item.summary.merchantName !in selectedMerchantNames
                }
            }
            .let { items ->
                when (sortMode) {
                    MerchantSortMode.PRIORITY -> items.sortedWith(
                        compareByDescending<MerchantReviewItem> { item ->
                            selectedCategory?.let { option ->
                                optionMatchesSuggestion(option, item.suggestion)
                            } ?: false
                        }.thenByDescending { it.summary.uncategorizedCount }
                            .thenByDescending { kotlin.math.abs(it.summary.spendingAmountCents) }
                            .thenBy { it.summary.merchantName.lowercase(Locale.US) }
                    )

                    MerchantSortMode.AMOUNT -> items.sortedByDescending { kotlin.math.abs(it.summary.spendingAmountCents) }
                    MerchantSortMode.LATEST -> items.sortedByDescending { it.summary.latestTransactionEpochMs }
                    MerchantSortMode.NAME -> items.sortedBy { it.summary.merchantName.lowercase(Locale.US) }
                }
            }
    }

    val selectedSuggestedCount = selectedCategory?.let { option ->
        visibleItems.count { optionMatchesSuggestion(option, it.suggestion) }
    } ?: 0

    if (showCategoryPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CategoryPickerSheet(
                options = categoryOptions,
                selectedCategory = selectedCategory,
                selectedMerchantCount = selectedMerchantNames.size,
                onSelectCategory = { option ->
                    selectedCategory = option
                    showCategoryPicker = false
                },
                onCreateCategory = { category ->
                    val option = CategoryOption(
                        categoryName = category.trim(),
                        accountingTreatment = TransactionTreatments.EXPENSE,
                        source = "Custom"
                    )
                    if (!isVirtualUncategorizedCategory(option.categoryName) &&
                        !option.categoryName.equals("General", ignoreCase = true)
                    ) {
                        customOptions = customOptions + option
                        selectedCategory = option
                        showCategoryPicker = false
                    }
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
                title = { Text("Merchant Review") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        bottomBar = {
            MerchantBulkActionBar(
                selectedCount = selectedMerchantNames.size,
                selectedCategory = selectedCategory,
                onChooseCategory = { showCategoryPicker = true },
                onChangeCategory = { showCategoryPicker = true },
                onApply = {
                    val category = selectedCategory
                    val merchantNames = selectedMerchantNames
                    if (category != null && merchantNames.isNotEmpty()) {
                        onApplyCategoryToMerchants(merchantNames, category)
                        selectedMerchantNames = emptySet()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Applied ${category.label} to ${merchantNames.size} merchants"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                MerchantReviewHeader(
                    merchantCount = needsCategoryItems.size,
                    selectedCount = selectedMerchantNames.size
                )
            }

            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search merchants") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                MerchantReviewControls(
                    selectedFilter = selectionFilter,
                    selectedCount = selectedMerchantNames.size,
                    unselectedCount = (needsCategoryItems.size - selectedMerchantNames.size).coerceAtLeast(0),
                    sortMode = sortMode,
                    onFilterSelected = { selectionFilter = it },
                    onSortChanged = {
                        sortMode = when (sortMode) {
                            MerchantSortMode.PRIORITY -> MerchantSortMode.AMOUNT
                            MerchantSortMode.AMOUNT -> MerchantSortMode.LATEST
                            MerchantSortMode.LATEST -> MerchantSortMode.NAME
                            MerchantSortMode.NAME -> MerchantSortMode.PRIORITY
                        }
                    }
                )
            }

            if (selectedCategory != null && selectedSuggestedCount > 0) {
                item {
                    LikelyMatchesPanel(
                        category = selectedCategory!!,
                        suggestedCount = selectedSuggestedCount,
                        onSelectSuggested = {
                            val category = selectedCategory ?: return@LikelyMatchesPanel
                            selectedMerchantNames = visibleItems
                                .filter { optionMatchesSuggestion(category, it.suggestion) }
                                .map { it.summary.merchantName }
                                .toSet()
                        }
                    )
                }
            }

            if (visibleItems.isEmpty()) {
                item {
                    EmptyReviewState(
                        text = if (needsCategoryItems.isEmpty()) {
                            "All merchant categories are assigned."
                        } else {
                            "No merchants match this view."
                        }
                    )
                }
            } else {
                items(
                    items = visibleItems,
                    key = { it.summary.merchantName }
                ) { item ->
                    SelectableMerchantRow(
                        item = item,
                        selected = item.summary.merchantName in selectedMerchantNames,
                        selectedCategory = selectedCategory,
                        onToggle = {
                            selectedMerchantNames = if (item.summary.merchantName in selectedMerchantNames) {
                                selectedMerchantNames - item.summary.merchantName
                            } else {
                                selectedMerchantNames + item.summary.merchantName
                            }
                        },
                        onOpenDetail = {
                            onMerchantSelected(item.summary)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MerchantReviewHeader(
    merchantCount: Int,
    selectedCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "M",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$merchantCount merchants",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Assign a category to each merchant once so future transactions are easier to review.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MerchantReviewControls(
    selectedFilter: MerchantSelectionFilter,
    selectedCount: Int,
    unselectedCount: Int,
    sortMode: MerchantSortMode,
    onFilterSelected: (MerchantSelectionFilter) -> Unit,
    onSortChanged: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MerchantFilterChip(
                label = "All",
                selected = selectedFilter == MerchantSelectionFilter.ALL,
                onClick = { onFilterSelected(MerchantSelectionFilter.ALL) },
                modifier = Modifier.weight(1f)
            )
            MerchantFilterChip(
                label = "Selected $selectedCount",
                selected = selectedFilter == MerchantSelectionFilter.SELECTED,
                onClick = { onFilterSelected(MerchantSelectionFilter.SELECTED) },
                modifier = Modifier.weight(1f)
            )
            MerchantFilterChip(
                label = "Unselected $unselectedCount",
                selected = selectedFilter == MerchantSelectionFilter.UNSELECTED,
                onClick = { onFilterSelected(MerchantSelectionFilter.UNSELECTED) },
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedButton(
            onClick = onSortChanged,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sort: ${sortMode.label()}")
        }
    }
}

@Composable
private fun MerchantFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LikelyMatchesPanel(
    category: CategoryOption,
    suggestedCount: Int,
    onSelectSuggested: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Likely matches for ${category.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$suggestedCount merchants look like a good fit.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onSelectSuggested) {
                Text("Select")
            }
        }
    }
}

@Composable
private fun SelectableMerchantRow(
    item: MerchantReviewItem,
    selected: Boolean,
    selectedCategory: CategoryOption?,
    onToggle: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }
    val suggestedForSelected = selectedCategory?.let {
        optionMatchesSuggestion(it, item.suggestion)
    } ?: false

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() }
            )

            MerchantInitial(
                merchantName = item.summary.merchantName,
                highlighted = selected || suggestedForSelected,
                accentColor = merchantInitialColor(item.summary.merchantName)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.summary.merchantName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.summary.transactionCount} transactions - ${formatMoney(item.summary.spendingAmountCents, item.summary.currency)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Latest ${formatter.format(Date(item.summary.latestTransactionEpochMs))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.suggestion.categoryName != null) {
                    Text(
                        text = "Likely match: ${item.suggestion.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (suggestedForSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TreatmentPill(treatmentLabel(item.summary.primaryTreatment))
                TextButton(onClick = onOpenDetail) {
                    Text("Details")
                }
            }
        }
    }
}

@Composable
private fun MerchantInitial(
    merchantName: String,
    highlighted: Boolean,
    accentColor: Color
) {
    val initial = merchantName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) {
                    accentColor
                } else {
                    accentColor.copy(alpha = 0.14f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                accentColor
            },
            fontWeight = FontWeight.Bold
        )
    }
}

private fun merchantInitialColor(merchantName: String): Color {
    if (isVirtualUncategorizedCategory(merchantName)) {
        return Color(0xFFC9D1D8)
    }
    val palette = listOf(
        Color(0xFF21A66B),
        Color(0xFFFF8A3D),
        Color(0xFF7C5CFF),
        Color(0xFF3E8BFF),
        Color(0xFFE85D9A),
        Color(0xFF00A7A7),
        Color(0xFFFFB33F),
        Color(0xFF6E7BFF),
        Color(0xFF2DAE73),
        Color(0xFFE45757),
        Color(0xFF5A9BD5),
        Color(0xFF8A63D2)
    )
    val index = merchantName.lowercase(Locale.US).fold(0) { acc, char ->
        (acc * 31 + char.code).and(Int.MAX_VALUE)
    }
    return palette[index % palette.size]
}

@Composable
private fun TreatmentPill(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun MerchantBulkActionBar(
    selectedCount: Int,
    selectedCategory: CategoryOption?,
    onChooseCategory: () -> Unit,
    onChangeCategory: () -> Unit,
    onApply: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedCategory?.label ?: "No category selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$selectedCount merchants selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedCategory != null) {
                    TextButton(onClick = onChangeCategory) {
                        Text("Change")
                    }
                }
                Button(
                    onClick = if (selectedCategory == null) onChooseCategory else onApply,
                    enabled = selectedCategory == null || selectedCount > 0
                ) {
                    Text(if (selectedCategory == null) "Choose category" else "Apply category")
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerSheet(
    options: List<CategoryOption>,
    selectedCategory: CategoryOption?,
    selectedMerchantCount: Int,
    onSelectCategory: (CategoryOption) -> Unit,
    onCreateCategory: (String) -> Unit,
    onCancel: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("") }
    var draftCategory by remember(selectedCategory) { mutableStateOf(selectedCategory) }

    val cleanOptions = remember(options) {
        options.filterNot {
            isVirtualUncategorizedCategory(it.categoryName) ||
                    it.categoryName.equals("General", ignoreCase = true)
        }
    }
    val customCategoryIsSelectable = remember(customCategory) {
        customCategory.isNotBlank() &&
                !isVirtualUncategorizedCategory(customCategory) &&
                !customCategory.equals("General", ignoreCase = true)
    }
    val visibleOptions = remember(cleanOptions, searchText) {
        val query = searchText.trim().lowercase(Locale.US)
        if (query.isBlank()) {
            cleanOptions
        } else {
            cleanOptions.filter { option ->
                option.label.lowercase(Locale.US).contains(query) ||
                        treatmentLabel(option.accountingTreatment).lowercase(Locale.US).contains(query)
            }
        }
    }
    val commonOptions = remember(cleanOptions) {
        val preferred = listOf(
            "Groceries",
            "Dining & Restaurants",
            "Subscriptions",
            "Gas & Transport",
            "Utilities",
            "Shopping",
            "Other"
        )
        preferred.mapNotNull { category ->
            cleanOptions.firstOrNull {
                it.categoryName.equals(category, ignoreCase = true)
            }
        }.distinctBy { it.categoryName.lowercase(Locale.US) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Select a category",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (selectedMerchantCount > 0) {
                "Apply this category to $selectedMerchantCount selected merchants."
            } else {
                "Choose the category that best describes these merchants."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search categories") },
            singleLine = true
        )

        if (commonOptions.isNotEmpty() && searchText.isBlank()) {
            Text(
                text = "Commonly used",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            commonOptions.chunked(3).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { option ->
                        CategoryQuickOption(
                            option = option,
                            selected = draftCategory?.matches(option) == true,
                            onClick = { draftCategory = option },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowOptions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Text(
            text = "All categories",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = visibleOptions,
                key = { "${it.categoryName}|${it.accountingTreatment}|${it.source}" }
            ) { option ->
                CategoryOptionRow(
                    option = option,
                    selected = draftCategory?.matches(option) == true,
                    onClick = { draftCategory = option }
                )
            }
        }

        HorizontalDivider()

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
            Text("Create new category")
        }

        Button(
            onClick = {
                draftCategory?.let(onSelectCategory)
            },
            enabled = draftCategory != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply category")
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun CategoryQuickOption(
    option: CategoryOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = categoryInitial(option.categoryName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = option.categoryName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryOptionRow(
    option: CategoryOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = categoryOptionDescription(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (selected) "Selected" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyReviewState(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun MerchantSortMode.label(): String {
    return when (this) {
        MerchantSortMode.PRIORITY -> "Recommended"
        MerchantSortMode.AMOUNT -> "Amount"
        MerchantSortMode.LATEST -> "Latest"
        MerchantSortMode.NAME -> "Name"
    }
}

private fun CategoryOption.matches(other: CategoryOption): Boolean {
    return categoryName.equals(other.categoryName, ignoreCase = true) &&
            accountingTreatment == other.accountingTreatment
}

private fun categoryOptionDescription(option: CategoryOption): String {
    val treatment = treatmentLabel(option.accountingTreatment)
    return if (option.accountingTreatment == TransactionTreatments.EXPENSE) {
        when (option.source) {
            "Preset" -> "Common category"
            "Custom" -> "Custom category"
            "Existing" -> "Used before"
            "Rule" -> "Saved rule"
            else -> option.source
        }
    } else {
        treatment
    }
}

private fun categoryInitial(categoryName: String): String {
    return categoryName.trim().take(1).uppercase(Locale.US).ifBlank { "O" }
}
