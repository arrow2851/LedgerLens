package com.example.ledgerlens

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.rules.applyRulesToTransaction
import com.example.ledgerlens.domain.rules.normalizeRulePhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRuleApplicationTest {

    @Test
    fun merchantDefaultAppliesCategoryToExpense() {
        val rawAlert = rawAlert("Card purchase $42.10 at Walmart.")
        val transaction = transaction(merchant = "Walmart")
        val rule = merchantRule(
            merchant = "Walmart",
            category = "Groceries"
        )

        val updated = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = emptyList(),
            merchantDefaultRules = listOf(rule)
        )

        assertEquals("Groceries", updated.categoryName)
        assertTrue(updated.parserNotes.orEmpty().contains("merchant default"))
    }

    @Test
    fun merchantDefaultDoesNotApplyExpenseCategoryToPersonToPersonByDefault() {
        val rawAlert = rawAlert("Zelle transfer $42.10 to Walmart.")
        val transaction = transaction(
            merchant = "Walmart",
            transactionType = TransactionTreatments.PERSON_TO_PERSON,
            excludedFromSpending = true
        )
        val rule = merchantRule(
            merchant = "Walmart",
            category = "Groceries"
        )

        val updated = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = emptyList(),
            merchantDefaultRules = listOf(rule)
        )

        assertEquals(null, updated.categoryName)
    }

    @Test
    fun transactionSpecificCategoryOverrideSurvivesMerchantDefault() {
        val rawAlert = rawAlert("Card purchase $42.10 at Walmart.")
        val transaction = transaction(
            merchant = "Walmart",
            categoryName = "Healthcare",
            categoryUserEdited = true
        )
        val rule = merchantRule(
            merchant = "Walmart",
            category = "Groceries"
        )

        val updated = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = emptyList(),
            merchantDefaultRules = listOf(rule)
        )

        assertEquals("Healthcare", updated.categoryName)
    }

    @Test
    fun merchantDefaultCanSetIncomeTreatment() {
        val rawAlert = rawAlert("Deposit $125.00 from Payroll.")
        val transaction = transaction(merchant = "Payroll")
        val rule = merchantRule(
            merchant = "Payroll",
            category = "Income",
            transactionType = TransactionTreatments.INCOME
        )

        val updated = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = emptyList(),
            merchantDefaultRules = listOf(rule)
        )

        assertEquals(TransactionTreatments.INCOME, updated.accountingTreatment)
        assertTrue(updated.excludedFromSpending)
        assertEquals("Income", updated.categoryName)
    }

    @Test
    fun requiresReviewMerchantRuleDoesNotAutoApplyCategory() {
        val rawAlert = rawAlert("You sent $25.00 to Omar with Zelle.")
        val transaction = transaction(
            merchant = "Omar",
            transactionType = TransactionTreatments.PERSON_TO_PERSON,
            excludedFromSpending = true
        )
        val rule = merchantRule(
            merchant = "Omar",
            category = "Groceries",
            transactionType = TransactionTreatments.PERSON_TO_PERSON,
            requiresReview = true
        )

        val updated = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = emptyList(),
            merchantDefaultRules = listOf(rule)
        )

        assertEquals("NEEDS_REVIEW", updated.reviewStatus)
        assertEquals(null, updated.categoryName)
    }

    @Test
    fun sourcePhraseRuleUsesNormalizedAliasMatching() {
        val rawAlert = rawAlert("Card purchase $42.10 at SAMS Club #8839.")
        val transaction = transaction(merchant = "Unknown merchant")
        val rule = TransactionRuleEntity(
            sourceKey = "sender:24273",
            matchPhrase = "SAMSCLUB",
            normalizedMatchPhrase = normalizeRulePhrase("SAMSCLUB"),
            merchantName = "Sam's Club",
            createdAtEpochMs = 1_700_000_000_000,
            updatedAtEpochMs = 1_700_000_000_000
        )

        val updated = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = listOf(rule),
            merchantDefaultRules = emptyList()
        )

        assertEquals("Sam's Club", updated.displayMerchantName)
    }

    private fun rawAlert(body: String): RawAlertEntity {
        return RawAlertEntity(
            id = 7,
            notificationKey = "sms:7",
            sourcePackage = "sms",
            title = "SMS from 24273",
            text = body,
            bigText = null,
            subText = null,
            combinedText = body,
            postTimeEpochMs = 1_700_000_000_000,
            capturedAtEpochMs = 1_700_000_000_100,
            processingStatus = "IMPORTED_SMS"
        )
    }

    private fun transaction(
        merchant: String,
        transactionType: String = TransactionTreatments.EXPENSE,
        excludedFromSpending: Boolean = false,
        categoryName: String? = null,
        categoryUserEdited: Boolean = false
    ): TransactionEntity {
        return TransactionEntity(
            id = 10,
            rawAlertId = 7,
            sourceKey = "sender:24273",
            transactionType = transactionType,
            accountingTreatment = transactionType,
            amountCents = 4210,
            currency = "USD",
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = "Chase",
            accountHint = "1234",
            occurredAtEpochMs = 1_700_000_000_000,
            receivedAtEpochMs = 1_700_000_000_100,
            parseConfidence = 0.84,
            reviewStatus = "AUTO_PARSED",
            excludedFromSpending = excludedFromSpending,
            parserNotes = null,
            categoryUserEdited = categoryUserEdited,
            createdAtEpochMs = 1_700_000_000_000,
            updatedAtEpochMs = 1_700_000_000_000,
            categoryName = categoryName
        )
    }

    private fun merchantRule(
        merchant: String,
        category: String,
        transactionType: String? = null,
        requiresReview: Boolean = false
    ): TransactionRuleEntity {
        return TransactionRuleEntity(
            sourceKey = "__merchant_defaults__",
            matchPhrase = merchant,
            normalizedMatchPhrase = normalizeRulePhrase(merchant),
            merchantName = merchant,
            categoryName = category,
            transactionType = transactionType,
            appliesToTreatment = transactionType,
            excludedFromSpending = transactionType?.let {
                TransactionTreatments.defaultExcludedFromSpending(it)
            },
            requiresReview = requiresReview,
            createdAtEpochMs = 1_700_000_000_000,
            updatedAtEpochMs = 1_700_000_000_000
        )
    }
}
