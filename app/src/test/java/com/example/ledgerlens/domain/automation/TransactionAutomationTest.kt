package com.example.ledgerlens.domain.automation

import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionAutomationTest {
    @Test
    fun authorizedCardholderLabel_isSeparatedFromMerchant() {
        val parsed = parseAuthorizedCardholderLabel(
            "Example User's Venture Credit Card (1234)"
        )

        assertEquals("Example User", parsed?.authorizedUserName)
        assertEquals("1234", parsed?.accountHint)
        assertTrue(isPaymentInstrumentLabel("Example User's Venture Credit Card (1234)"))
    }

    @Test
    fun processorAndStoreVariants_shareCanonicalMerchant() {
        assertEquals("CHICHAS", canonicalMerchantIdentity("TST*CHICHAS"))
        assertEquals("CHICHAS", canonicalMerchantIdentity("CHICHAS"))
        assertEquals("WALMART", canonicalMerchantIdentity("WAL-MART #0202"))
        assertEquals("WALMART", canonicalMerchantIdentity("WM SUPERCENTER #2995"))
    }

    @Test
    fun editingAReviewItem_resolvesReviewWithoutExtraStep() {
        val status = resolvedReviewStatusAfterUserDecision(
            previousStatus = ReviewStatus.NEEDS_REVIEW,
            merchantChanged = false,
            categoryChanged = false,
            treatmentChanged = true
        )

        assertEquals(ReviewStatus.REVIEWED, status)
    }

    @Test
    fun parserDirection_isRecoveredForRuleApplication() {
        assertEquals(
            TransactionTreatments.Direction.OUTGOING,
            transactionDirectionFromParserNotes("Accounting treatment: EXPENSE. Direction: outgoing.")
        )
        assertEquals(
            TransactionTreatments.Direction.INCOMING,
            transactionDirectionFromParserNotes("Direction: incoming.")
        )
        assertFalse(
            transactionDirectionFromParserNotes(null) == TransactionTreatments.Direction.OUTGOING
        )
    }
}
