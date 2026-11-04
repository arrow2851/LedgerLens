package com.ledgerlens.two

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerlens.two.data.EditRequest
import com.ledgerlens.two.data.LedgerDatabase
import com.ledgerlens.two.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.RoundingMode

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(LedgerDatabase.get(application))
    val transactions = repository.transactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList()
    )
    val sources = repository.sources.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList()
    )
    val rules = repository.rules.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList()
    )
    val budgets = repository.budgets.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList()
    )
    val notice = MutableStateFlow("LedgerLens2 is independent from LedgerLens 1.0")
    val busy = MutableStateFlow(false)

    private fun work(label: String, job: suspend () -> String) {
        viewModelScope.launch {
            if (busy.value) return@launch
            busy.value = true
            notice.value = label
            try {
                notice.value = withContext(Dispatchers.IO) { job() }
            } catch (e: Exception) {
                notice.value = label + " failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                busy.value = false
            }
        }
    }

    fun scanPhone() = work("Scanning SMS") {
        val report = repository.ingestor.scanPhone(getApplication())
        "Scanned " + report.received + " SMS • " + report.inserted +
            " new posting candidates • " + report.suspectedDuplicates +
            " possible duplicates • " + report.ignored + " non-transactions excluded"
    }

    fun importSourceScoutFile(uri: Uri) = work("Importing Source Scout JSON") {
        val context = getApplication<Application>()
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Cannot read selected JSON")
        val report = input.bufferedReader().use(repository.ingestor::importJson)
        "Read " + report.received + " received SMS • " + report.inserted +
            " new transactions • " + report.ignored + " non-transactions excluded"
    }

    fun approveSource(sender: String, approved: Boolean) = work("Updating source") {
        repository.approveSource(sender, approved)
        if (approved) "Approved source for spending: " + sender
        else "Source excluded from spending: " + sender
    }

    fun editTransaction(id: Long, request: EditRequest) = work("Saving correction") {
        repository.edit(id, request)
        "Transaction saved; other unresolved review issues remain visible."
    }

    fun decideDuplicate(id: Long, duplicate: Boolean) = work("Reviewing possible duplicate") {
        repository.duplicateDecision(id, duplicate)
        if (duplicate) "Excluded duplicate from spending." else "Marked as a distinct transaction."
    }

    fun saveMerchantDefault(merchant: String, category: String) = work("Saving merchant rule") {
        repository.saveMerchantDefault(merchant, category)
        "Categorized unedited history and future postings for " + merchant
    }

    fun setRuleEnabled(key: String, enabled: Boolean) = work("Updating rule") {
        repository.setRuleEnabled(key, enabled)
        "Merchant rule updated."
    }

    fun saveBudget(month: String, category: String, currency: String, decimal: String) =
        work("Saving budget") {
            val minor = BigDecimal(decimal.trim()).multiply(BigDecimal(100))
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
            repository.saveBudget(month, category, currency, minor)
            "Saved " + month + " budget for " + category
        }

    fun exportCsv(uri: Uri) = work("Exporting transactions CSV") {
        val output = getApplication<Application>().contentResolver.openOutputStream(uri)
            ?: error("Cannot write selected CSV")
        val csv = repository.exportCsv()
        OutputStreamWriter(output, Charsets.UTF_8).use { it.write(csv) }
        "Transaction CSV exported."
    }

    fun exportBackup(uri: Uri) = work("Writing full backup") {
        val output = getApplication<Application>().contentResolver.openOutputStream(uri)
            ?: error("Cannot write selected backup")
        val backup = repository.exportBackup()
        OutputStreamWriter(output, Charsets.UTF_8).use { it.write(backup) }
        "Complete LedgerLens2 backup exported."
    }

    fun restoreBackup(uri: Uri) = work("Restoring full backup") {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: error("Cannot read selected backup")
        val count = InputStreamReader(input, Charsets.UTF_8).use(repository::restoreBackup)
        "Restored " + count + " transactions, sources, merchant rules, budgets and saved SMS."
    }

    fun clearAppData() = work("Deleting LedgerLens2 data") {
        repository.deleteEverything()
        "Only LedgerLens2 local data was cleared. The original LedgerLens was not changed."
    }
}
