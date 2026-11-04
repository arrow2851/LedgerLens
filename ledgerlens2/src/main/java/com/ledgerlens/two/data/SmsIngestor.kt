package com.ledgerlens.two.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.ledgerlens.two.domain.FinanceEngine
import com.ledgerlens.two.domain.ParsedTransaction
import com.ledgerlens.two.domain.SmsInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Reader

data class ImportReport(
    val received: Int,
    val postingCandidates: Int,
    val inserted: Int,
    val previouslySeen: Int,
    val suspectedDuplicates: Int,
    val ignored: Int
)

/** Read using Android's SMS provider. No network, default SMS role or SMS deletion. */
internal fun readPhoneSms(context: Context): List<SmsInput> {
    check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
        == PackageManager.PERMISSION_GRANTED) { "SMS permission has not been granted." }
    val fields = arrayOf(
        Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
        Telephony.Sms.DATE, Telephony.Sms.DATE_SENT
    )
    val list = mutableListOf<SmsInput>()
    val cursor = context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI, fields, null, null, Telephony.Sms.DATE + " ASC"
    ) ?: error("Android SMS provider is not available")
    cursor.use { c ->
        val id = c.getColumnIndexOrThrow(Telephony.Sms._ID)
        val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val dateSent = c.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
        while (c.moveToNext()) {
            if (c.isNull(id) || c.isNull(date)) continue
            list += SmsInput(
                smsId = c.getLong(id),
                sender = c.getString(address).orEmpty().trim().ifBlank { "Unknown sender" },
                body = c.getString(body).orEmpty(),
                receivedAtMs = c.getLong(date),
                sentAtMs = if (c.isNull(dateSent) || c.getLong(dateSent) <= 0L)
                    null else c.getLong(dateSent)
            )
        }
    }
    return list
}

/** Source Scout v0.2 FULL_FIDELITY_SMS JSON. No file content is ever sent to GitHub. */
internal fun parseSourceScoutJson(reader: Reader): List<SmsInput> {
    val root = JSONObject(reader.readText())
    require(root.optInt("schema_version") == 2 &&
        root.optString("export_kind") == "FULL_FIDELITY_SMS") {
        "Select the LedgerLens Source Scout full original SMS JSON file."
    }
    val messages = root.getJSONArray("messages")
    val result = ArrayList<SmsInput>(messages.length())
    for (i in 0 until messages.length()) {
        val obj = messages.getJSONObject(i)
        if (obj.optString("direction") != "RECEIVED") continue
        if (!obj.has("sms_id") || !obj.has("received_timestamp_ms")) continue
        result += SmsInput(
            smsId = obj.getLong("sms_id"),
            sender = obj.optString("sender").ifBlank { "Unknown sender" },
            body = obj.optString("body"),
            receivedAtMs = obj.getLong("received_timestamp_ms"),
            sentAtMs = if (obj.isNull("sent_timestamp_ms")) null
                else obj.optLong("sent_timestamp_ms").takeIf { it > 0L }
        )
    }
    return result
}

private fun institutionFor(sms: SmsInput): String? {
    val text = (sms.sender + " " + sms.body).lowercase()
    return when {
        "capital one" in text || "capitalone" in text -> "Capital One"
        "chase" in text -> "Chase"
        "hdfc" in text -> "HDFC Bank"
        "sbi" in text -> "State Bank of India"
        "bank of america" in text || "bofa" in text -> "Bank of America"
        "citi" in text -> "Citi"
        "wells fargo" in text -> "Wells Fargo"
        "american express" in text || "amex" in text -> "American Express"
        "discover" in text -> "Discover"
        "zelle" in text -> "Zelle"
        "paypal" in text -> "PayPal"
        "venmo" in text -> "Venmo"
        else -> null
    }
}

class SmsIngestor(private val database: LedgerDatabase) {
    /**
     * The SQL uniqueness on smsId provides idempotent rescans. Economic duplicates are only
     * candidates and are never removed without an explicit user decision.
     */
    suspend fun ingest(messages: List<SmsInput>): ImportReport = withContext(Dispatchers.IO) {
        val sorted = messages.sortedWith(compareBy<SmsInput> { it.receivedAtMs }.thenBy { it.smsId })
        val parsed = sorted.mapNotNull { sms -> FinanceEngine.parse(sms)?.let { sms to it } }
        val dao = database.dao()
        var inserted = 0
        var previouslySeen = 0
        var possibleDuplicates = 0
        val old = dao.allTransactions().toMutableList()
        val oldBySmsId = old.map { it.smsId }.toHashSet()
        val oldRules = dao.allRules().filter { it.active }.associateBy { it.merchantKey }
        database.withTransaction {
            for ((sms, decision) in parsed) {
                if (!oldBySmsId.add(sms.smsId)) {
                    previouslySeen++
                    continue
                }
                val key = decision.merchant?.let(FinanceEngine::canonicalMerchant).orEmpty()
                val savedCategory = oldRules[key]?.category
                val matching = old.asReversed().firstOrNull { other ->
                    other.sourceKey == sms.sender && other.currency == decision.currency &&
                        other.kind == decision.kind && other.amountMinor == decision.amountMinor &&
                        kotlin.math.abs(other.occurredAtMs - sms.receivedAtMs) <= 300_000L &&
                        (key.isBlank() || other.merchantKey.isBlank() || key == other.merchantKey)
                }
                val now = System.currentTimeMillis()
                dao.insertSource(
                    FinancialSource(sms.sender, approved = false,
                        institution = institutionFor(sms), updatedAtMs = now)
                )
                dao.insertSms(StoredSms(
                    smsId = sms.smsId, sender = sms.sender, body = sms.body,
                    receivedAtMs = sms.receivedAtMs, sentAtMs = sms.sentAtMs, insertedAtMs = now
                ))
                val t = LedgerTransaction(
                    smsId = sms.smsId, sourceKey = sms.sender, sender = sms.sender,
                    accountHint = decision.accountHint, amountMinor = decision.amountMinor, currency = decision.currency,
                    kind = decision.kind, merchant = decision.merchant,
                    merchantKey = key, category = savedCategory ?: decision.category,
                    confidence = decision.confidence,
                    reason = decision.explanation + if (savedCategory != null) " Merchant rule applied." else "",
                    occurredAtMs = decision.occurredAtMs, receivedAtMs = decision.receivedAtMs,
                    duplicateOfId = matching?.id, updatedAtMs = now
                )
                val newId = dao.insertTransaction(t)
                if (newId > 0L) {
                    inserted++
                    if (matching != null) possibleDuplicates++
                    old += t.copy(id = newId)
                } else {
                    previouslySeen++
                }
            }
        }
        ImportReport(
            received = messages.size, postingCandidates = parsed.size, inserted = inserted,
            previouslySeen = previouslySeen, suspectedDuplicates = possibleDuplicates,
            ignored = messages.size - parsed.size
        )
    }

    suspend fun scanPhone(context: Context): ImportReport = withContext(Dispatchers.IO) {
        ingest(readPhoneSms(context))
    }

    suspend fun importJson(reader: Reader): ImportReport = withContext(Dispatchers.IO) {
        ingest(parseSourceScoutJson(reader))
    }
}
