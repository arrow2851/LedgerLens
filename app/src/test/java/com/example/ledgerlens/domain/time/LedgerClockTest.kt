package com.example.ledgerlens.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerClockTest {

    @Test
    fun `fixed clock returns deterministic epoch milliseconds`() {
        val clock: LedgerClock = FixedLedgerClock(1_720_000_000_000L)

        assertEquals(1_720_000_000_000L, clock.nowEpochMs())
    }
}
