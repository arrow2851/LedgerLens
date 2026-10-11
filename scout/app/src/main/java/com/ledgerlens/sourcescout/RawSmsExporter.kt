package com.ledgerlens.sourcescout

import android.content.Context
import android.provider.Telephony
import android.util.JsonWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant

/**
 * Export is deliberately local-only. Android's Storage Access Framework asks the user where
 * to save. No network, cloud sync, GitHub integration, logging or temporary local copies.
 *
 * WARNING: Raw exports can contain financial details, personal conversations and OTPs.
 */
internal enum class RawSmsExportScope {
    ALL_SMS,
    FINANCIAL_RECEIVED
}

internal object RawSmsExporter {
    /**
     * Writes one SMS at a time: RAM usage stays bounded even for a large inbox.
     * All timestamps remain the exact provider millisecond values.
     */
    fun write(context: Context, output: OutputStream, scope: RawSmsExportScope): Int {
        val columns = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.READ
        )
        var rows = 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, columns, null, null, Telephony.Sms.DATE + " ASC"
        ) ?: throw IllegalStateException("SMS provider returned no data")

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val senderIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val sentIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
            val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val threadIdx = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val readIdx = c.getColumnIndexOrThrow(Telephony.Sms.READ)

            JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { j ->
                j.setIndent("  ")
                j.beginObject()
                j.name("schema_version").value(2)
                j.name("export_kind").value("FULL_FIDELITY_SMS")
                j.name("created_utc").value(Instant.now().toString())
                j.name("scope").value(scope.name)
                j.name("notes").value(
                    "Original SMS data, intentionally unredacted. Android SMS provider only; excludes RCS and MMS."
                )
                j.name("messages").beginArray()
                while (c.moveToNext()) {
                    val body = c.getString(bodyIdx)
                    val type = c.getInt(typeIdx)
                    val classification = MessageShape.classify(body.orEmpty())
                    if (scope == RawSmsExportScope.FINANCIAL_RECEIVED &&
                        (type != Telephony.Sms.MESSAGE_TYPE_INBOX || classification != 2)
                    ) continue
                    j.beginObject()
                    j.name("sms_id").value(c.getLong(idIdx))
                    j.name("sender").value(c.getString(senderIdx))
                    j.name("body").value(body)
                    j.name("received_timestamp_ms")
                    if (c.isNull(dateIdx)) j.nullValue() else j.value(c.getLong(dateIdx))
                    j.name("sent_timestamp_ms")
                    if (c.isNull(sentIdx)) j.nullValue() else j.value(c.getLong(sentIdx))
                    j.name("sms_type").value(type.toLong())
                    j.name("direction").value(when (type) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> "RECEIVED"
                        Telephony.Sms.MESSAGE_TYPE_SENT -> "SENT"
                        Telephony.Sms.MESSAGE_TYPE_DRAFT -> "DRAFT"
                        Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "OUTBOX"
                        Telephony.Sms.MESSAGE_TYPE_FAILED -> "FAILED"
                        Telephony.Sms.MESSAGE_TYPE_QUEUED -> "QUEUED"
                        else -> "OTHER"
                    })
                    j.name("thread_id")
                    if (c.isNull(threadIdx)) j.nullValue() else j.value(c.getLong(threadIdx))
                    j.name("read").value(c.getInt(readIdx) != 0)
                    j.name("local_message_class").value(when (classification) {
                        1 -> "AUTH_OR_OTP"
                        2 -> "FINANCIAL_CANDIDATE"
                        else -> "OTHER"
                    })
                    j.endObject()
                    rows++
                }
                j.endArray()
                j.name("exported_message_count").value(rows.toLong())
                j.endObject()
            }
        }
        return rows
    }
}
