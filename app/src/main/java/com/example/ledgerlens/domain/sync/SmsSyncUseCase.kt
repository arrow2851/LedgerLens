package com.example.ledgerlens.domain.sync

import androidx.room.withTransaction
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.domain.parser.ParseMode
import com.example.ledgerlens.domain.parser.ParseRunResult
import com.example.ledgerlens.domain.parser.detectAndSaveSources
import com.example.ledgerlens.domain.parser.parseIdentifiedSourceTransactions
import com.example.ledgerlens.domain.privacy.PrivacySettingsStore
import com.example.ledgerlens.domain.privacy.shouldRedactAfterSuccessfulParse

const val SMS_SYNC_DAY_MS = 24L * 60L * 60L * 1000L

enum class SmsSyncMode(
    val initialWindowDays: Int,
    val usesCursor: Boolean
) {
    REFRESH_LATEST(initialWindowDays = 90, usesCursor = true),
    BACKFILL_HISTORY(initialWindowDays = 365 * 5, usesCursor = false)
}

data class SmsImportResult(
    val scannedCount: Int,
    val financeLookingCount: Int,
    val importedCount: Int,
    val duplicateCount: Int
)

interface SmsMessageImporter {
    suspend fun importFinanceSmsMessages(sinceEpochMs: Long): SmsImportResult
}

interface SmsSyncCursorStore {
    fun getLastSuccessfulRefreshEpochMs(): Long?
    fun setLastSuccessfulRefreshEpochMs(epochMs: Long)
}

data class SmsSyncResult(
    val mode: SmsSyncMode,
    val scannedCount: Int,
    val financeLookingCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val detectedSourceCount: Int,
    val newlyDetectedSourceCount: Int,
    val approvedSourceCount: Int,
    val parsedCount: Int,
    val skippedExistingCount: Int,
    val ignoredNonTransactionCount: Int,
    val failedParseCount: Int,
    val pendingSourceReviewCount: Int
) {
    fun userMessage(): String {
        val countLine = "Scanned $scannedCount messages. Imported $importedCount new alerts."
        if (scannedCount == 0) {
            return "No SMS messages were found for this sync range."
        }
        if (financeLookingCount == 0) {
            return "$countLine No financial-looking SMS alerts were found."
        }

        val parts = mutableListOf<String>()
        parts += "scanned $scannedCount messages"
        parts += "imported $importedCount new alerts"
        if (duplicateCount > 0) parts += "skipped $duplicateCount already imported"
        if (parsedCount > 0) parts += "parsed $parsedCount transactions"
        if (pendingSourceReviewCount > 0) parts += "found $pendingSourceReviewCount banks/cards to review"
        if (ignoredNonTransactionCount > 0) parts += "ignored $ignoredNonTransactionCount balance/info alerts"
        if (failedParseCount > 0) parts += "could not parse $failedParseCount alerts"
        if (parsedCount == 0 && approvedSourceCount == 0 && pendingSourceReviewCount > 0) {
            parts += "review banks/cards before transactions can be created"
        }

        return "Synced SMS alerts: ${parts.joinToString(", ")}."
    }
}

class SmsSyncUseCase(
    private val database: AppDatabase,
    private val smsImporter: SmsMessageImporter,
    private val cursorStore: SmsSyncCursorStore? = null,
    private val privacySettingsStore: PrivacySettingsStore? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val overlapWindowMs: Long = 7L * SMS_SYNC_DAY_MS

    suspend fun sync(mode: SmsSyncMode): SmsSyncResult {
        val syncStartedAt = clock()
        val sinceEpochMs = resolveSmsSyncStartEpochMs(
            mode = mode,
            nowEpochMs = syncStartedAt,
            lastSuccessfulRefreshEpochMs = cursorStore?.getLastSuccessfulRefreshEpochMs(),
            overlapWindowMs = overlapWindowMs
        )

        val importResult = smsImporter.importFinanceSmsMessages(sinceEpochMs = sinceEpochMs)
        var parseResult = ParseRunResult()
        var detectedSourceCount = 0
        var newlyDetectedSourceCount = 0
        var approvedSourceCount = 0
        var pendingSourceReviewCount = 0

        database.withTransaction {
            val existingSourceKeys = database.financialSourceDao()
                .getAllOnce()
                .map { it.sourceKey }
                .toSet()

            detectedSourceCount = detectAndSaveSources(
                database = database,
                rawAlerts = database.rawAlertDao().getAllOnce()
            )

            val sources = database.financialSourceDao().getAllOnce()
            newlyDetectedSourceCount = sources.count { it.sourceKey !in existingSourceKeys }
            approvedSourceCount = sources.count { it.userConfirmed && !it.ignored }
            pendingSourceReviewCount = sources.count { !it.userConfirmed && !it.ignored }

            parseResult = parseIdentifiedSourceTransactions(
                database = database,
                mode = ParseMode.NEW_PENDING_ONLY,
                redactRawSmsAfterParse = privacySettingsStore
                    ?.getRawSmsRetention()
                    ?.shouldRedactAfterSuccessfulParse()
                    ?: false
            )
        }

        if (mode.usesCursor) {
            cursorStore?.setLastSuccessfulRefreshEpochMs(syncStartedAt)
        }

        return SmsSyncResult(
            mode = mode,
            scannedCount = importResult.scannedCount,
            financeLookingCount = importResult.financeLookingCount,
            importedCount = importResult.importedCount,
            duplicateCount = importResult.duplicateCount,
            detectedSourceCount = detectedSourceCount,
            newlyDetectedSourceCount = newlyDetectedSourceCount,
            approvedSourceCount = approvedSourceCount,
            parsedCount = parseResult.parsedCount,
            skippedExistingCount = parseResult.skippedCount,
            ignoredNonTransactionCount = parseResult.ignoredNonTransactionCount,
            failedParseCount = parseResult.failedCount,
            pendingSourceReviewCount = pendingSourceReviewCount
        )
    }
}

fun resolveSmsSyncStartEpochMs(
    mode: SmsSyncMode,
    nowEpochMs: Long,
    lastSuccessfulRefreshEpochMs: Long?,
    overlapWindowMs: Long = 7L * SMS_SYNC_DAY_MS
): Long {
    return when {
        mode.usesCursor && lastSuccessfulRefreshEpochMs != null ->
            (lastSuccessfulRefreshEpochMs - overlapWindowMs).coerceAtLeast(0L)
        else -> nowEpochMs - mode.initialWindowDays * SMS_SYNC_DAY_MS
    }
}
