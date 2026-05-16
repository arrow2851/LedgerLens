package com.example.ledgerlens

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.parser.mergeReparsedTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class ReparseMergeTest {

    @Test
    fun reparsePreservesUserEditedFields() {
        val existing = transaction(
            merchant = "User Merchant",
            category = "User Category",
            treatment = TransactionTreatments.REIMBURSEMENT,
            excluded = false,
            reviewStatus = ReviewStatus.REVIEWED,
            merchantEdited = true,
            categoryEdited = true,
            treatmentEdited = true
        )
        val reparsed = transaction(
            merchant = "Parser Merchant",
            category = "Parser Category",
            treatment = TransactionTreatments.EXPENSE,
            excluded = false,
            reviewStatus = ReviewStatus.AUTO_PARSED
        )

        val merged = mergeReparsedTransaction(existing, reparsed)

        assertEquals("User Merchant", merged.displayMerchantName)
        assertEquals("User Category", merged.categoryName)
        assertEquals(TransactionTreatments.REIMBURSEMENT, merged.accountingTreatment)
        assertEquals(false, merged.excludedFromSpending)
        assertEquals(ReviewStatus.REVIEWED, merged.reviewStatus)
    }

    @Test
    fun reparseMarksAmountChangeForReview() {
        val existing = transaction(amountCents = 1200, reviewStatus = ReviewStatus.AUTO_PARSED)
        val reparsed = transaction(amountCents = 1300, reviewStatus = ReviewStatus.AUTO_PARSED)

        val merged = mergeReparsedTransaction(existing, reparsed)

        assertEquals(1300L, merged.amountCents)
        assertEquals(ReviewStatus.NEEDS_REVIEW, merged.reviewStatus)
    }

    private fun transaction(
        amountCents: Long = 1200,
        merchant: String = "Merchant",
        category: String? = "Category",
        treatment: String = TransactionTreatments.EXPENSE,
        excluded: Boolean = false,
        reviewStatus: String = ReviewStatus.AUTO_PARSED,
        merchantEdited: Boolean = false,
        categoryEdited: Boolean = false,
        treatmentEdited: Boolean = false
    ): TransactionEntity {
        return TransactionEntity(
            id = 1,
            rawAlertId = 1,
            sourceKey = "sender:bank",
            transactionType = treatment,
            accountingTreatment = treatment,
            amountCents = amountCents,
            currency = "USD",
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = "Bank",
            accountHint = "1234",
            occurredAtEpochMs = 1_700_000_000_000,
            receivedAtEpochMs = 1_700_000_000_000,
            parseConfidence = 0.9,
            reviewStatus = reviewStatus,
            excludedFromSpending = excluded,
            parserNotes = "notes",
            merchantUserEdited = merchantEdited,
            categoryUserEdited = categoryEdited,
            treatmentUserEdited = treatmentEdited,
            createdAtEpochMs = 1_700_000_000_000,
            updatedAtEpochMs = 1_700_000_000_000,
            categoryName = category
        )
    }
}
