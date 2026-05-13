package com.example.ledgerlens.platform

import android.content.ContentResolver
import android.provider.Telephony
import android.util.Log
import com.example.ledgerlens.data.dao.RawAlertDao
import com.example.ledgerlens.data.entity.RawAlertEntity
import java.util.Locale

class AndroidSmsImporter(
    private val contentResolver: ContentResolver,
    private val rawAlertDao: RawAlertDao
) {

    suspend fun importFinanceSmsMessages(daysBack: Int): Int {
        val now = System.currentTimeMillis()
        val startDate = now - (daysBack * 24L * 60L * 60L * 1000L)

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(startDate.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        var importedCount = 0
        var scannedCount = 0
        var financeLookingCount = 0
        var duplicateCount = 0

        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (cursor.moveToNext()) {
                scannedCount++

                val smsId = cursor.getLong(idIndex)
                val address = cursor.getString(addressIndex).orEmpty()
                val body = cursor.getString(bodyIndex).orEmpty()
                val date = cursor.getLong(dateIndex)
                val type = cursor.getInt(typeIndex)

                if (!looksLikeFinancialSms(body)) {
                    continue
                }

                financeLookingCount++

                val notificationKey = "sms:$smsId"
                if (rawAlertDao.countByNotificationKey(notificationKey) > 0) {
                    duplicateCount++
                    continue
                }

                rawAlertDao.insert(
                    RawAlertEntity(
                        notificationKey = notificationKey,
                        sourcePackage = "sms",
                        title = "SMS from $address",
                        text = body,
                        bigText = null,
                        subText = "sms_type=$type",
                        combinedText = body,
                        postTimeEpochMs = date,
                        capturedAtEpochMs = now,
                        processingStatus = "IMPORTED_SMS"
                    )
                )
                importedCount++
            }
        }

        Log.d(
            "LedgerLensSmsImport",
            "daysBack=$daysBack scanned=$scannedCount financeLooking=$financeLookingCount duplicates=$duplicateCount imported=$importedCount"
        )

        return importedCount
    }

    private fun looksLikeFinancialSms(body: String): Boolean {
        val lower = body.lowercase(Locale.US)
        val hasMoneyAmount = Regex(
            pattern = """(\$|usd\s*)?\d{1,3}(,\d{3})*(\.\d{2})"""
        ).containsMatchIn(lower)

        val financeKeywords = listOf(
            "spent",
            "purchase",
            "transaction",
            "charged",
            "charge",
            "debit",
            "debited",
            "credit",
            "credited",
            "deposit",
            "withdrawal",
            "payment",
            "paid",
            "balance",
            "card",
            "account",
            "atm",
            "pos",
            "zelle",
            "venmo",
            "cash app",
            "bank",
            "alert",
            "autopay",
            "refund",
            "authorized",
            "authorization",
            "available balance",
            "ending in"
        )

        return hasMoneyAmount && financeKeywords.any { lower.contains(it) }
    }
}
