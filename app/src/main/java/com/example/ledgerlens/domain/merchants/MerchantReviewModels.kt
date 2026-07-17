package com.example.ledgerlens.domain.merchants

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.CategoryPresets
import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.rules.MERCHANT_DEFAULT_RULE_SOURCE_KEY
import com.example.ledgerlens.domain.rules.RuleKind
import com.example.ledgerlens.domain.rules.normalizeRulePhrase
import com.example.ledgerlens.domain.summary.MerchantSummary
import com.example.ledgerlens.domain.summary.isVirtualUncategorizedCategory
import com.example.ledgerlens.domain.summary.hasLowConfidence
import com.example.ledgerlens.domain.summary.hasMissingCategory
import com.example.ledgerlens.domain.summary.hasMissingMerchant
import com.example.ledgerlens.domain.summary.merchantSummaryName
import com.example.ledgerlens.domain.summary.merchantSummaries
import java.util.Locale

data class CategoryOption(
    val categoryName: String,
    val accountingTreatment: String = TransactionTreatments.EXPENSE,
    val source: String = "Preset"
) {
    val label: String
        get() = categoryName
}

data class MerchantCategorySuggestion(
    val categoryName: String?,
    val confidence: Double,
    val reason: String
) {
    val label: String
        get() = categoryName?.takeIf { it.isNotBlank() } ?: "No suggestion"
}

data class MerchantReviewItem(
    val summary: MerchantSummary,
    val suggestion: MerchantCategorySuggestion,
    val needsCategory: Boolean,
    val hasMissingMerchant: Boolean,
    val hasLowConfidence: Boolean
)

data class MerchantBulkSelectionState(
    val selectedMerchantNames: Set<String> = emptySet(),
    val selectedCategory: CategoryOption? = null
)

data class MerchantReviewStats(
    val needsReviewCount: Int,
    val missingMerchantCount: Int,
    val missingCategoryCount: Int,
    val lowConfidenceCount: Int
)

data class MerchantBulkApplyResult(
    val merchantCount: Int,
    val transactionCount: Int,
    val ruleCount: Int
)

val defaultCategoryOptions = CategoryPresets.defaults.map(::CategoryOption)

fun buildCategoryCatalog(
    transactions: List<TransactionEntity>,
    rules: List<TransactionRuleEntity>,
    customOptions: List<CategoryOption> = emptyList()
): List<CategoryOption> {
    val optionsByKey = linkedMapOf<String, CategoryOption>()

    fun add(option: CategoryOption) {
        if (option.categoryName.isBlank()) return
        if (isVirtualUncategorizedCategory(option.categoryName)) return
        if (option.categoryName.equals("General", ignoreCase = true)) return
        val key = categoryKey(option.categoryName)
        optionsByKey.putIfAbsent(key, option)
    }

    defaultCategoryOptions.forEach(::add)
    customOptions.forEach { add(it.copy(source = "Custom")) }

    transactions
        .filter { !it.categoryName.isNullOrBlank() }
        .forEach { transaction ->
            add(
                CategoryOption(
                    categoryName = transaction.categoryName.orEmpty(),
                    source = "Existing"
                )
            )
        }

    rules
        .filter { it.active && !it.categoryName.isNullOrBlank() }
        .forEach { rule ->
            add(
                CategoryOption(
                    categoryName = rule.categoryName.orEmpty(),
                    source = "Rule"
                )
            )
        }

    return optionsByKey.values
        .sortedWith(
            compareBy<CategoryOption> {
                when (it.source) {
                    "Preset" -> 0
                    "Custom" -> 1
                    "Existing" -> 2
                    else -> 3
                }
            }.thenBy { it.categoryName.lowercase(Locale.US) }
        )
}

fun buildMerchantReviewItems(
    transactions: List<TransactionEntity>,
    rules: List<TransactionRuleEntity>
): List<MerchantReviewItem> {
    val rulesByMerchant = rules
        .filter { it.active && it.sourceKey == MERCHANT_DEFAULT_RULE_SOURCE_KEY }
        .associateBy { normalizeRulePhrase(it.merchantName ?: it.matchPhrase) }

    val transactionsByMerchant = transactions.groupBy {
        merchantSummaryName(it).lowercase(Locale.US)
    }

    return merchantSummaries(transactions)
        .map { summary ->
            val merchantTransactions = transactionsByMerchant[summary.merchantName.lowercase(Locale.US)].orEmpty()
            MerchantReviewItem(
                summary = summary,
                suggestion = suggestCategoryForMerchant(
                    merchant = summary,
                    merchantTransactions = merchantTransactions,
                    merchantRule = rulesByMerchant[normalizeRulePhrase(summary.merchantName)]
                ),
                needsCategory = summary.uncategorizedCount > 0,
                hasMissingMerchant = merchantTransactions.any { hasMissingMerchant(it) },
                hasLowConfidence = merchantTransactions.any { hasLowConfidence(it) }
            )
        }
}

fun merchantReviewStats(transactions: List<TransactionEntity>): MerchantReviewStats {
    return MerchantReviewStats(
        needsReviewCount = transactions.count { it.reviewStatus == ReviewStatus.NEEDS_REVIEW },
        missingMerchantCount = transactions.count { hasMissingMerchant(it) },
        missingCategoryCount = transactions.count { hasMissingCategory(it) },
        lowConfidenceCount = transactions.count { hasLowConfidence(it) }
    )
}

fun optionMatchesSuggestion(
    option: CategoryOption,
    suggestion: MerchantCategorySuggestion
): Boolean {
    return suggestion.categoryName.equals(option.categoryName, ignoreCase = true)
}

fun buildMerchantDefaultRule(
    merchantName: String,
    option: CategoryOption,
    now: Long = System.currentTimeMillis()
): TransactionRuleEntity {
    return TransactionRuleEntity(
        sourceKey = MERCHANT_DEFAULT_RULE_SOURCE_KEY,
        matchPhrase = merchantName,
        normalizedMatchPhrase = normalizeRulePhrase(merchantName),
        ruleKind = RuleKind.MERCHANT_DEFAULT,
        merchantName = merchantName,
        categoryName = option.categoryName,
        transactionType = null,
        excludedFromSpending = null,
        appliesToTreatment = null,
        applyCategoryAutomatically = true,
        requiresReview = false,
        active = true,
        createdAtEpochMs = now,
        updatedAtEpochMs = now
    )
}

fun applyMerchantCategoryToTransaction(
    transaction: TransactionEntity,
    merchantNames: Set<String>,
    option: CategoryOption,
    now: Long = System.currentTimeMillis()
): TransactionEntity {
    val merchant = merchantSummaryName(transaction)
    if (merchantNames.none { it.equals(merchant, ignoreCase = true) }) {
        return transaction
    }

    val categoryName = if (!transaction.categoryUserEdited) option.categoryName else transaction.categoryName
    val reviewStatus = when {
        transaction.reviewStatus == ReviewStatus.NEEDS_REVIEW &&
            !categoryName.isNullOrBlank() &&
            merchant.isNotBlank() -> ReviewStatus.AUTO_PARSED
        else -> transaction.reviewStatus
    }

    return transaction.copy(
        categoryName = categoryName,
        reviewStatus = reviewStatus,
        updatedAtEpochMs = now
    )
}

private fun suggestCategoryForMerchant(
    merchant: MerchantSummary,
    merchantTransactions: List<TransactionEntity>,
    merchantRule: TransactionRuleEntity?
): MerchantCategorySuggestion {
    if (!merchant.categoryName.isNullOrBlank()) {
        return MerchantCategorySuggestion(
            categoryName = merchant.categoryName,
            confidence = 1.0,
            reason = "Already categorized"
        )
    }

    if (merchantRule != null && !merchantRule.categoryName.isNullOrBlank()) {
        return MerchantCategorySuggestion(
            categoryName = merchantRule.categoryName,
            confidence = 0.96,
            reason = "Saved merchant rule"
        )
    }

    val haystack = (merchant.merchantName + " " + merchantTransactions.joinToString(" ") {
        listOfNotNull(it.parserNotes, it.sourceInstitution, it.categoryName).joinToString(" ")
    }).lowercase(Locale.US)

    return when {
        containsAny(haystack, "walmart", "target", "costco", "sam's", "sams", "aldi", "kroger", "whole foods", "trader joe", "market") ->
            MerchantCategorySuggestion("Groceries", 0.78, "Merchant keyword")

        containsAny(haystack, "restaurant", "cafe", "coffee", "starbucks", "mcdonald", "chipotle", "doordash", "uber eats", "grubhub") ->
            MerchantCategorySuggestion("Dining & Restaurants", 0.78, "Merchant keyword")

        containsAny(haystack, "shell", "exxon", "chevron", "bp ", "mobil", "speedway", "circle k", "gas", "fuel") ->
            MerchantCategorySuggestion("Gas & Transport", 0.78, "Merchant keyword")

        containsAny(haystack, "netflix", "spotify", "hulu", "disney", "youtube", "openai", "chatgpt", "google", "apple.com/bill") ->
            MerchantCategorySuggestion("Subscriptions", 0.74, "Merchant keyword")

        containsAny(haystack, "cvs", "walgreens", "pharmacy", "clinic", "doctor", "hospital") ->
            MerchantCategorySuggestion("Healthcare", 0.74, "Merchant keyword")

        containsAny(haystack, "electric", "utility", "water", "internet", "phone", "insurance") ->
            MerchantCategorySuggestion("Utilities", 0.72, "Merchant keyword")

        containsAny(haystack, "masjid", "mosque", "islamic", "donation", "charity", "zakat", "sadaqah") ->
            MerchantCategorySuggestion("Charity", 0.78, "Merchant keyword")

        else -> MerchantCategorySuggestion(null, 0.0, "No confident suggestion")
    }
}

private fun categoryKey(categoryName: String): String {
    return categoryName.trim().lowercase(Locale.US)
}

private fun containsAny(text: String, vararg needles: String): Boolean {
    return needles.any { text.contains(it) }
}
