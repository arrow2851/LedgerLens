package com.ledgerlens.transactionlab

import android.content.Context
import android.provider.Telephony
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

data class SmsRow(val id: Long, val sender: String, val body: String, val timestamp: Long, val parsed: Parsed, val details: TransactionDetails)
data class ScanResult(val total: Int, val ignored: Int, val rows: List<SmsRow>)

fun scanTransactions(context: Context, selectedSources:Set<String>): ScanResult {
    val rows = ArrayList<SmsRow>()
    var count = 0
    var ignored = 0
    val cols = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
    val cursor = context.contentResolver.query(Telephony.Sms.CONTENT_URI, cols, null, null, Telephony.Sms.DATE + " DESC")
        ?: throw IllegalStateException("SMS provider returned no cursor")
    cursor.use { c ->
        val id = c.getColumnIndexOrThrow(Telephony.Sms._ID)
        val sender = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val type = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
        while (c.moveToNext()) {
            count++
            val s = c.getString(sender).orEmpty()
            if (s !in selectedSources) { ignored++; continue }
            val b = c.getString(body).orEmpty()
            val p = SmsParser.parse(b,s,c.getInt(type) == Telephony.Sms.MESSAGE_TYPE_INBOX)
            if (p.outcome == "IGNORED") { ignored++; continue }
            rows.add(SmsRow(c.getLong(id), s, b, c.getLong(date), p, DetailsExtractor.extract(b,p)))
        }
    }
    return ScanResult(count,ignored,rows)
}

data class ManualEdit(
    val merchant: String,
    val amount: String,
    val kind: String,
    val direction: String,
    val lifecycle: String,
    val category: String,
    val reviewed: Boolean,
    val ignored: Boolean,
    val counterparty: String = "",
    val paymentMethod: String = "",
    val cardholder: String = "",
    val cardLast4: String = ""
) {
    fun json(id: Long) = JSONObject().apply {
        put("sms_id",id)
        put("merchant",merchant)
        put("amount",amount)
        put("kind",kind)
        put("direction",direction)
        put("lifecycle",lifecycle)
        put("category",category)
        put("reviewed",reviewed)
        put("ignored",ignored)
        put("counterparty",counterparty)
        put("payment_method",paymentMethod)
        put("cardholder",cardholder)
        put("card_last4",cardLast4)
    }
    companion object {
        fun from(obj: JSONObject) = ManualEdit(
            obj.optString("merchant"),obj.optString("amount"),obj.optString("kind","UNKNOWN"),
            obj.optString("direction","UNKNOWN"),obj.optString("lifecycle","UNKNOWN"),
            obj.optString("category"),obj.optBoolean("reviewed"),obj.optBoolean("ignored"),
            obj.optString("counterparty"),obj.optString("payment_method"),
            obj.optString("cardholder"),obj.optString("card_last4")
        )
    }
}
fun moneyString(minor:Long?): String = if(minor == null) "" else BigDecimal.valueOf(minor,2).setScale(2).toPlainString()
fun amountValid(s:String): Boolean = try {
    if (s.isBlank()) true
    else {
        val n=BigDecimal(s.replace(",","")).setScale(2, RoundingMode.UNNECESSARY)
        n.signum() >= 0 && n < BigDecimal("1000000000000")
    }
} catch (_: Exception) { false }

class EditStore(context: Context) {
    private val prefs = context.getSharedPreferences("transaction_lab_edits_v1",Context.MODE_PRIVATE)
    fun load(): Map<Long,ManualEdit> = prefs.all.mapNotNull { (k,v) ->
        if (!k.startsWith("sms_") || v !is String) null else try {
            k.removePrefix("sms_").toLong() to ManualEdit.from(JSONObject(v))
        } catch (_: Exception) { null }
    }.toMap()
    fun save(id:Long, edit: ManualEdit) {
        prefs.edit().putString("sms_" + id,edit.json(id).toString()).apply()
    }
}
