package com.example.ledgerlens.domain.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialSmsClassifierTest {
    @Test
    fun alertThresholdMessages_areNeverTransactions() {
        val text = "$3,200 at 10:03 PM ET is above the limit in your alert settings"

        assertTrue(FinancialSmsClassifier.isLikelyNonTransactionFinancialAlert(text))
        assertFalse(FinancialSmsClassifier.looksTransactional(text))
    }

    @Test
    fun ordinaryPurchaseAlert_remainsTransactional() {
        val text = "A charge for $25.40 at EXAMPLE CAFE was approved"

        assertFalse(FinancialSmsClassifier.isLikelyNonTransactionFinancialAlert(text))
        assertTrue(FinancialSmsClassifier.looksTransactional(text))
    }
}
