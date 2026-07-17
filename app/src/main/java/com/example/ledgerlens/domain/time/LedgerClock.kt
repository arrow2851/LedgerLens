package com.example.ledgerlens.domain.time

fun interface LedgerClock {
    fun nowEpochMs(): Long
}

object SystemLedgerClock : LedgerClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}

class FixedLedgerClock(
    private val fixedEpochMs: Long,
) : LedgerClock {
    override fun nowEpochMs(): Long = fixedEpochMs
}
