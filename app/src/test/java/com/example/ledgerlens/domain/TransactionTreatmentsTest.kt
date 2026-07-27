package com.example.ledgerlens.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTreatmentsTest {
    @Test
    fun refundsAndReimbursements_areIncludedAsNegativeOffsets() {
        assertFalse(
            TransactionTreatments.defaultExcludedFromSpending(
                TransactionTreatments.REFUND,
                TransactionTreatments.Direction.INCOMING
            )
        )
        assertFalse(
            TransactionTreatments.defaultExcludedFromSpending(
                TransactionTreatments.REIMBURSEMENT,
                TransactionTreatments.Direction.INCOMING
            )
        )
        assertEquals(
            -6_000L,
            TransactionTreatments.spendingImpactCents(
                treatment = TransactionTreatments.REIMBURSEMENT,
                excludedFromSpending = false,
                amountCents = 6_000L
            )
        )
    }

    @Test
    fun incomeTransfersAndCardPayments_areExcludedByDefault() {
        assertTrue(TransactionTreatments.defaultExcludedFromSpending(TransactionTreatments.INCOME))
        assertTrue(TransactionTreatments.defaultExcludedFromSpending(TransactionTreatments.TRANSFER))
        assertTrue(TransactionTreatments.defaultExcludedFromSpending(TransactionTreatments.CREDIT_CARD_PAYMENT))
    }
}
