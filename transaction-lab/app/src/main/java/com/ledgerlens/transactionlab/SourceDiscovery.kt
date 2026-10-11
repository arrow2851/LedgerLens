package com.ledgerlens.transactionlab

import android.content.Context
import android.provider.Telephony

data class SourceProfile(
    val sender: String, val received: Int, val matches: Int, val institution: String?
) {
    val suggested get() = matches >= 2 || (matches == 1 && received <= 3)
    val label get() = if (institution != null) "$institution · $sender" else sender
}
fun discoverSources(context: Context): List<SourceProfile> {
    class Counts(var received: Int=0,var matches: Int=0,
        val banks:MutableMap<String,Int> = mutableMapOf())
    val sources=linkedMapOf<String,Counts>()
    context.contentResolver.query(Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.TYPE),
        null,null,null)?.use { c ->
        val senderIdx=c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyIdx=c.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val typeIdx=c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
        while(c.moveToNext()){
            if(c.getInt(typeIdx)!=Telephony.Sms.MESSAGE_TYPE_INBOX)continue
            val sender=c.getString(senderIdx).orEmpty()
            val body=c.getString(bodyIdx).orEmpty()
            val s=sources.getOrPut(sender){Counts()}
            s.received++
            val bank=DetailsExtractor.institutionFor(body)
            if(bank!=null)s.banks[bank]=(s.banks[bank]?:0)+1
            if(SmsParser.parse(body,sender).outcome!="IGNORED")s.matches++
        }
    }?:throw IllegalStateException("SMS provider unavailable")
    return sources.map{(id,s) ->
        SourceProfile(id,s.received,s.matches,s.banks.maxByOrNull{it.value}?.key)
    }.sortedWith(compareByDescending<SourceProfile>{it.matches}.thenByDescending{it.received})
}
class SourceStore(context: Context) {
    private val prefs=context.getSharedPreferences("lab_source_selection_v1",Context.MODE_PRIVATE)
    fun load():Set<String> = prefs.getStringSet("chosen",emptySet())?.toSet() ?: emptySet()
    fun save(senders:Set<String>){prefs.edit().putStringSet("chosen",senders.toSet()).apply()}
}
