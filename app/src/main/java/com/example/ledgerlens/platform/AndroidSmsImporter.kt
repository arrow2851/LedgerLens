package com.example.ledgerlens.platform

import android.content.ContentResolver
import android.provider.Telephony
import android.util.Log
import com.example.ledgerlens.data.dao.RawAlertDao
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.domain.parser.FinancialSmsClassifier
import com.example.ledgerlens.domain.parser.RawAlertStatus
import com.example.ledgerlens.domain.sync.SmsImportResult
import com.example.ledgerlens.domain.sync.SmsMessageImporter

class AndroidSmsImporter(
    private val contentResolver: ContentResolver,
    private val rawAlertDao: RawAlertDao
) : SmsMessageImporter {

    override suspend fun importFinanceSmsMessages(sinceEpochMs: Long): SmsImportResult {
        val now = System.currentTimeMillis()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(sinceEpochMs.toString())
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

                if (!AndroidSmsImportFilters.isInboxSmsType(type)) {
                    continue
                }

                if (!FinancialSmsClassifier.looksFinancial(body)) {
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
                        processingStatus = RawAlertStatus.IMPORTED_SMS
                    )
                )
                importedCount++
            }
        }

        Log.d(
            "LedgerLensSmsImport",
            "sinceEpochMs=$sinceEpochMs scanned=$scannedCount financeLooking=$financeLookingCount duplicates=$duplicateCount imported=$importedCount"
        )

        return SmsImportResult(
            scannedCount = scannedCount,
            financeLookingCount = financeLookingCount,
            importedCount = importedCount,
            duplicateCount = duplicateCount
        )
    }
}

object AndroidSmsImportFilters {
    fun isInboxSmsType(type: Int): Boolean {
        return type == Telephony.Sms.MESSAGE_TYPE_INBOX
    }
}
