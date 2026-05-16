package com.example.ledgerlens

import com.example.ledgerlens.domain.sync.SMS_SYNC_DAY_MS
import com.example.ledgerlens.domain.sync.SmsSyncMode
import com.example.ledgerlens.domain.sync.resolveSmsSyncStartEpochMs
import com.example.ledgerlens.platform.AndroidSmsImportFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSyncPolicyTest {

    @Test
    fun importFilterAcceptsInboxOnly() {
        assertTrue(AndroidSmsImportFilters.isInboxSmsType(1))
        assertFalse(AndroidSmsImportFilters.isInboxSmsType(2))
        assertFalse(AndroidSmsImportFilters.isInboxSmsType(3))
        assertFalse(AndroidSmsImportFilters.isInboxSmsType(4))
        assertFalse(AndroidSmsImportFilters.isInboxSmsType(5))
        assertFalse(AndroidSmsImportFilters.isInboxSmsType(6))
    }

    @Test
    fun firstRefreshUsesInitialWindow() {
        val now = 200L * SMS_SYNC_DAY_MS

        val since = resolveSmsSyncStartEpochMs(
            mode = SmsSyncMode.REFRESH_LATEST,
            nowEpochMs = now,
            lastSuccessfulRefreshEpochMs = null
        )

        assertEquals(now - 90L * SMS_SYNC_DAY_MS, since)
    }

    @Test
    fun laterRefreshUsesCursorWithOverlap() {
        val lastSuccess = 200L * SMS_SYNC_DAY_MS

        val since = resolveSmsSyncStartEpochMs(
            mode = SmsSyncMode.REFRESH_LATEST,
            nowEpochMs = 250L * SMS_SYNC_DAY_MS,
            lastSuccessfulRefreshEpochMs = lastSuccess
        )

        assertEquals(lastSuccess - 7L * SMS_SYNC_DAY_MS, since)
    }

    @Test
    fun backfillIgnoresRefreshCursor() {
        val now = 2_000L * SMS_SYNC_DAY_MS

        val since = resolveSmsSyncStartEpochMs(
            mode = SmsSyncMode.BACKFILL_HISTORY,
            nowEpochMs = now,
            lastSuccessfulRefreshEpochMs = now - SMS_SYNC_DAY_MS
        )

        assertEquals(now - 365L * 5L * SMS_SYNC_DAY_MS, since)
    }
}
