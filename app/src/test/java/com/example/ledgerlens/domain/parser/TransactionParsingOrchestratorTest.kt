package com.example.ledgerlens.domain.parser

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionParsingOrchestratorTest {
    @Test
    fun authorizedCardholderLabel_isClearedBeforePersistence() {
        val transaction = TransactionEntity(
            rawAlertId = 1L,
            sourceKey = "example-source",
            transactionType = TransactionTreatments.EXPENSE,
            accountingTreatment = TransactionTreatments.EXPENSE,
            amountCents = 1_083L,
            merchantRaw = "Example User's Venture Credit Card (1234)",
            displayMerchantName = "Example User's Venture Credit Card (1234)",
            sourceInstitution = "Capital One",
            accountHint = null,
            occurredAtEpochMs = 1L,
            receivedAtEpochMs = 1L,
            parseConfidence = 0.91,
            reviewStatus = ReviewStatus.AUTO_PARSED,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )

        val repaired = sanitizeAuthorizedCardholderMerchant(transaction, now = 2L)

        assertNull(repaired.merchantRaw)
        assertNull(repaired.displayMerchantName)
        assertEquals("1234", repaired.accountHint)
        assertEquals(ReviewStatus.NEEDS_REVIEW, repaired.reviewStatus)
        assertTrue(repaired.parserNotes.orEmpty().contains("Authorized user: Example User"))
    }

    @Test
    fun ordinaryMerchant_isNotChanged() {
        val transaction = TransactionEntity(
            rawAlertId = 2L,
            sourceKey = "example-source",
            transactionType = TransactionTreatments.EXPENSE,
            accountingTreatment = TransactionTreatments.EXPENSE,
            amountCents = 2_540L,
            merchantRaw = "Example Cafe",
            displayMerchantName = "Example Cafe",
            sourceInstitution = "Example Bank",
            accountHint = "9876",
            occurredAtEpochMs = 1L,
            receivedAtEpochMs = 1L,
            parseConfidence = 0.91,
            reviewStatus = ReviewStatus.AUTO_PARSED,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )

        assertEquals(transaction, sanitizeAuthorizedCardholderMerchant(transaction, now = 2L))
    }
}
