package com.example.ledgerlens

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.ui.transactionCategoryOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryOptionsTest {

    @Test
    fun transactionCategoryOptionsExcludeAccountingTreatments() {
        val options = transactionCategoryOptions(
            listOf(
                transaction(categoryName = "Transfer"),
                transaction(categoryName = "Income"),
                transaction(categoryName = "Groceries")
            )
        )

        assertTrue(options.contains("Groceries"))
        assertFalse(options.contains("Transfer"))
        assertFalse(options.contains("Income"))
        assertFalse(options.contains("Credit Card Payment"))
        assertFalse(options.contains("Person to Person"))
    }

    private fun transaction(categoryName: String?): TransactionEntity {
        return TransactionEntity(
            id = 1,
            rawAlertId = 1,
            sourceKey = "sender:test",
            transactionType = TransactionTreatments.EXPENSE,
            accountingTreatment = TransactionTreatments.EXPENSE,
            amountCents = 1000,
            currency = "USD",
            merchantRaw = "Merchant",
            displayMerchantName = "Merchant",
            sourceInstitution = "Bank",
            accountHint = "1234",
            occurredAtEpochMs = 1,
            receivedAtEpochMs = 1,
            parseConfidence = 0.9,
            reviewStatus = "AUTO_PARSED",
            excludedFromSpending = false,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
            categoryName = categoryName
        )
    }
}
