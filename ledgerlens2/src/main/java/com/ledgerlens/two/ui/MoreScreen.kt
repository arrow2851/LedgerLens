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
import com.ledgerlens.two.data.FinancialSource
import com.ledgerlens.two.data.LedgerTransaction
import com.ledgerlens.two.data.MerchantRule
import com.ledgerlens.two.data.MonthlyBudget
import com.ledgerlens.two.domain.FinanceEngine
import java.util.Locale

@Composable
internal fun CategoryDropdown(
    category: String, onCategory: (String) -> Unit, title: String = "Category"
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        SmallLabel(title)
        Box {
            OutlinedButton(onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()) {
                Text(category.ifBlank { "Choose a category" },
                    modifier = Modifier.weight(1f))
                Text("⌄")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Uncategorized") }, onClick = {
                    onCategory(""); expanded = false
                })
                FinanceEngine.categories.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        onCategory(name); expanded = false
                    })
                }
            }
        }
    }
}

@Composable
internal fun MoreScreen(
    transactions: List<LedgerTransaction>,
    sources: List<FinancialSource>,
    rules: List<MerchantRule>,
    budgets: List<MonthlyBudget>,
    month: String,
    currency: String,
    onSource: (String, Boolean) -> Unit,
    onRuleToggle: (String, Boolean) -> Unit,
    onMerchantRule: (String, String) -> Unit,
    onBudget: (String, String, String, String) -> Unit,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onExportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var expandSources by remember { mutableStateOf(true) }
    var expandRules by remember { mutableStateOf(false) }
    var expandMerchants by remember { mutableStateOf(false) }
    var budgetCategory by remember { mutableStateOf(FinanceEngine.categories.first()) }
    var budgetValue by remember { mutableStateOf("") }
    var selectedMerchant by remember { mutableStateOf("") }
    var selectedMerchantCategory by remember { mutableStateOf(FinanceEngine.categories.first()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val merchantGroups = remember(transactions) {
        transactions.filter { it.merchantKey.isNotBlank() }
            .groupBy { it.merchantKey }.toList()
            .sortedByDescending { (_, rows) -> rows.count { it.category.isNullOrBlank() } }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Erase LedgerLens2 data?") },
        text = { Text("This deletes imported SMS, sources, transactions, rules and budgets in LedgerLens2 only. The original LedgerLens remains untouched. Export a full backup first if needed.") },
        confirmButton = {
            Button(onClick = { confirmDelete = false; onDelete() },
                colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
                Text("Erase LedgerLens2")
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
        }
    )
    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        title = { Text("Restore a complete backup?") },
        text = { Text("Restoring a valid LedgerLens2 schema-1 JSON will replace this app's current local data. Export a backup first. LedgerLens 1.0 stays separate.") },
        confirmButton = {
            Button(onClick = { confirmRestore = false; onRestore() }) { Text("Select backup JSON") }
        },
        dismissButton = {
            TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
        }
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(5.dp))
        Eyebrow("Your settings and sources")
        Text("More", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold)
        Panel(title = "LedgerLens2 snapshot",
            supporting = "Local-first and independent of LedgerLens 1.0") {
            Text(transactions.size.toString() + " transactions",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            SmallLabel(sources.size.toString() + " SMS sources · " +
                rules.count { it.active } + " active merchant rules · " +
                budgets.size + " category budgets")
            SmallLabel("Android package: com.ledgerlens.two · Database: v1")
        }
        Panel(title = "Banks & SMS sources",
            supporting = "A source must be approved before its transactions affect your spending") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                SmallLabel(sources.count { it.approved }.toString() + " approved of " +
                    sources.size + " discovered")
                TextButton(onClick = { expandSources = !expandSources }) {
                    Text(if (expandSources) "Collapse" else "Manage")
                }
            }
            if (expandSources) {
                if (sources.isEmpty()) SmallLabel("Import SMS to discover source candidates.")
                sources.forEach { source ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(source.institution ?: "Unrecognized sender",
                                fontWeight = FontWeight.SemiBold)
                            SmallLabel(source.sender + " · " +
                                transactions.count { it.sourceKey == source.sender } + " candidates")
                        }
                        Switch(checked = source.approved,
                            onCheckedChange = { onSource(source.sender, it) })
                    }
                    HorizontalDivider(color = CanvasColor)
                }
            }
        }
        Panel(title = "Merchant intelligence",
            supporting = "Save a category once; apply it to matching, unedited history and future SMS") {
            OutlinedTextField(
                value = selectedMerchant,
                onValueChange = { selectedMerchant = it },
                label = { Text("Merchant to remember") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            CategoryDropdown(selectedMerchantCategory,
                onCategory = { selectedMerchantCategory = it })
            Button(
                onClick = {
                    onMerchantRule(selectedMerchant, selectedMerchantCategory)
                    selectedMerchant = ""
                },
                enabled = selectedMerchant.isNotBlank() && selectedMerchantCategory.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save merchant category rule") }
            TextButton(onClick = { expandMerchants = !expandMerchants }) {
                Text(if (expandMerchants) "Hide merchant groups" else
                    "Browse " + merchantGroups.size + " merchant groups")
            }
            if (expandMerchants) {
                merchantGroups.forEach { (_, rows) ->
                    val name = rows.firstOrNull()?.merchant.orEmpty()
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        SmallLabel(rows.size.toString() + " transactions")
                    }
                }
            }
        }
        Panel(title = "Saved merchant rules") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallLabel(rules.size.toString() + " saved defaults")
                TextButton(onClick = { expandRules = !expandRules }) {
                    Text(if (expandRules) "Hide" else "Manage")
                }
            }
            if (expandRules) {
                if (rules.isEmpty()) SmallLabel("No saved rules. Create one above or from a transaction.")
                rules.forEach { rule ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.merchantDisplay, fontWeight = FontWeight.Medium)
                            SmallLabel(rule.category)
                        }
                        Switch(checked = rule.active,
                            onCheckedChange = { onRuleToggle(rule.merchantKey, it) })
                    }
                }
            }
        }
        Panel(title = "Category budgets",
            supporting = "Current selection: " + monthText(month) + " · " + currency) {
            CategoryDropdown(budgetCategory, onCategory = { budgetCategory = it })
            OutlinedTextField(
                value = budgetValue,
                onValueChange = { budgetValue = it },
                label = { Text("Monthly budget amount (" + currency + ")") },
                placeholder = { Text("250.00") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { onBudget(month, budgetCategory, currency, budgetValue) },
                enabled = budgetCategory.isNotBlank() &&
                    budgetValue.toDoubleOrNull()?.let { it > 0.0 } == true,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save monthly budget") }
            budgets.filter { it.month == month && it.currency == currency }.forEach { budget ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(budget.category)
                    Text(money(budget.limitMinor, budget.currency), fontWeight = FontWeight.Bold)
                }
            }
        }
        Panel(title = "Import & export", supporting = "All operations happen on this device") {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text("Read SMS from this phone")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("Import Source Scout raw SMS JSON")
            }
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                Text("Export transaction CSV")
            }
            OutlinedButton(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Export complete LedgerLens2 backup")
            }
            TextButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Restore LedgerLens2 backup")
            }
            SmallLabel("Complete JSON backups include parsed SMS bodies and your decisions. They are not encrypted. CSV is for analysis, not restore.")
        }
        Panel(title = "Privacy & reset",
            supporting = "No internet permission, no cloud sync, no access to LedgerLens 1.0") {
            SmallLabel("Only messages that pass financial posting checks are stored. Non-financial messages, promotions and OTPs are not imported. SMS is read only with permission.")
            OutlinedButton(onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)) {
                Text("Delete LedgerLens2 local data")
            }
        }
        Spacer(Modifier.height(25.dp))
    }
}
