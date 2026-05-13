package com.example.ledgerlens

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.merchants.CategoryOption
import com.example.ledgerlens.domain.merchants.applyMerchantCategoryToTransaction
import com.example.ledgerlens.domain.merchants.buildCategoryCatalog
import com.example.ledgerlens.domain.merchants.buildMerchantDefaultRule
import com.example.ledgerlens.domain.merchants.buildMerchantReviewItems
import com.example.ledgerlens.domain.rules.MERCHANT_DEFAULT_RULE_SOURCE_KEY
import com.example.ledgerlens.domain.rules.normalizeRulePhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantReviewDomainTest {

    @Test
    fun categoryCatalogIncludesPresetsExistingRulesAndCustomCategories() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "Local Clinic",
                categoryName = "Healthcare"
            )
        )
        val rules = listOf(
            rule(
                merchant = "Masjid",
                category = "Charity"
            )
        )
        val custom = CategoryOption("Education")

        val catalog = buildCategoryCatalog(
            transactions = transactions,
            rules = rules,
            customOptions = listOf(custom)
        )

        assertTrue(catalog.any { it.categoryName == "Groceries" && it.source == "Preset" })
        assertTrue(catalog.any { it.categoryName == "Healthcare" })
        assertTrue(catalog.any { it.categoryName == "Charity" })
        assertTrue(catalog.any { it.categoryName == "Education" })
    }

    @Test
    fun categoryCatalogExcludesVirtualUncategorizedNames() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "Missing",
                categoryName = "Uncategorized"
            ),
            transaction(
                id = 2,
                merchant = "Also Missing",
                categoryName = "Unassigned"
            )
        )
        val catalog = buildCategoryCatalog(
            transactions = transactions,
            rules = listOf(
                rule(merchant = "Legacy", category = "Uncategorized")
            ),
            customOptions = listOf(CategoryOption("Unassigned"))
        )

        assertTrue(catalog.none { it.categoryName.equals("Uncategorized", ignoreCase = true) })
        assertTrue(catalog.none { it.categoryName.equals("Unassigned", ignoreCase = true) })
    }

    @Test
    fun bulkCategoryApplyPreservesTransactionSpecificCategoryOverride() {
        val option = CategoryOption("Groceries")
        val edited = transaction(
            id = 1,
            merchant = "Target",
            categoryName = "Healthcare",
            categoryUserEdited = true
        )
        val unedited = transaction(
            id = 2,
            merchant = "Target"
        )

        val editedResult = applyMerchantCategoryToTransaction(
            transaction = edited,
            merchantNames = setOf("Target"),
            option = option,
            now = 2_000
        )
        val uneditedResult = applyMerchantCategoryToTransaction(
            transaction = unedited,
            merchantNames = setOf("Target"),
            option = option,
            now = 2_000
        )

        assertEquals("Healthcare", editedResult.categoryName)
        assertEquals("Groceries", uneditedResult.categoryName)
    }

    @Test
    fun personToPersonMerchantDefaultRequiresReview() {
        val rule = buildMerchantDefaultRule(
            merchantName = "Omar",
            option = CategoryOption(
                categoryName = "Person to Person",
                accountingTreatment = TransactionTreatments.PERSON_TO_PERSON
            ),
            now = 2_000
        )
        val updated = applyMerchantCategoryToTransaction(
            transaction = transaction(
                id = 1,
                merchant = "Omar",
                transactionType = TransactionTreatments.PERSON_TO_PERSON,
                excludedFromSpending = true
            ),
            merchantNames = setOf("Omar"),
            option = CategoryOption(
                categoryName = "Person to Person",
                accountingTreatment = TransactionTreatments.PERSON_TO_PERSON
            ),
            now = 2_000
        )

        assertTrue(rule.requiresReview)
        assertEquals("NEEDS_REVIEW", updated.reviewStatus)
        assertTrue(updated.excludedFromSpending)
    }

    @Test
    fun suggestionsAreGeneratedWithoutCreatingRules() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "ChatGPT",
                parserNotes = "subscription ai tools"
            )
        )

        val items = buildMerchantReviewItems(
            transactions = transactions,
            rules = emptyList()
        )

        assertEquals("Subscriptions", items.single().suggestion.categoryName)
    }

    private fun transaction(
        id: Long,
        merchant: String,
        transactionType: String = TransactionTreatments.EXPENSE,
        excludedFromSpending: Boolean = false,
        categoryName: String? = null,
        categoryUserEdited: Boolean = false,
        parserNotes: String? = null
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            rawAlertId = id,
            sourceKey = "sender:24273",
            transactionType = transactionType,
            accountingTreatment = transactionType,
            amountCents = 1200,
            currency = "USD",
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = "Test Bank",
            accountHint = "1234",
            occurredAtEpochMs = 1_500,
            receivedAtEpochMs = 1_500,
            parseConfidence = 0.90,
            reviewStatus = "NEEDS_REVIEW",
            excludedFromSpending = excludedFromSpending,
            parserNotes = parserNotes,
            categoryUserEdited = categoryUserEdited,
            createdAtEpochMs = 1_500,
            updatedAtEpochMs = 1_500,
            categoryName = categoryName
        )
    }

    private fun rule(
        merchant: String,
        category: String
    ): TransactionRuleEntity {
        return TransactionRuleEntity(
            sourceKey = MERCHANT_DEFAULT_RULE_SOURCE_KEY,
            matchPhrase = merchant,
            normalizedMatchPhrase = normalizeRulePhrase(merchant),
            merchantName = merchant,
            categoryName = category,
            transactionType = TransactionTreatments.EXPENSE,
            appliesToTreatment = TransactionTreatments.EXPENSE,
            active = true,
            createdAtEpochMs = 1_500,
            updatedAtEpochMs = 1_500
        )
    }
}
