package com.example.ledgerlens

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.ui.ReviewQueueFilter
import com.example.ledgerlens.ui.defaultReviewQueueFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewQueueSelectionTest {

    @Test
    fun defaultsToMissingCategoryWhenAvailable() {
        assertEquals(
            ReviewQueueFilter.MISSING_CATEGORY,
            defaultReviewQueueFilter(listOf(transaction(categoryName = null)))
        )
    }

    @Test
    fun defaultsToNeedsReviewWhenNoMissingCategory() {
        assertEquals(
            ReviewQueueFilter.NEEDS_REVIEW,
            defaultReviewQueueFilter(
                listOf(transaction(categoryName = "Dining", reviewStatus = "NEEDS_REVIEW"))
            )
        )
    }

    @Test
    fun returnsNullWhenNothingNeedsReview() {
        assertNull(
            defaultReviewQueueFilter(
                listOf(transaction(categoryName = "Dining", reviewStatus = "AUTO_PARSED"))
            )
        )
    }

    private fun transaction(
        categoryName: String?,
        reviewStatus: String = "AUTO_PARSED"
    ): TransactionEntity {
        return TransactionEntity(
            id = 1,
            rawAlertId = 1,
            sourceKey = "sender:bank",
            transactionType = TransactionTreatments.EXPENSE,
            accountingTreatment = TransactionTreatments.EXPENSE,
            amountCents = 1000,
            currency = "USD",
            merchantRaw = "Cafe",
            displayMerchantName = "Cafe",
            sourceInstitution = "Bank",
            accountHint = null,
            occurredAtEpochMs = 1,
            receivedAtEpochMs = 1,
            parseConfidence = 0.9,
            reviewStatus = reviewStatus,
            excludedFromSpending = false,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
            categoryName = categoryName
        )
    }
}
