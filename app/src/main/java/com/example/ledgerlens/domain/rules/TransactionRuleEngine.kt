package com.example.ledgerlens.domain.rules

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.TransactionTreatments
import java.util.Locale

const val MERCHANT_DEFAULT_RULE_SOURCE_KEY = "__merchant_defaults__"

fun normalizeRulePhrase(phrase: String): String {
    return phrase.trim().lowercase(Locale.US)
}

fun applyRulesToTransaction(
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity,
    sourceRules: List<TransactionRuleEntity>,
    merchantDefaultRules: List<TransactionRuleEntity> = emptyList()
): TransactionEntity {
    var updated = transaction
    val appliedRulePhrases = mutableListOf<String>()

    sourceRules.forEach { rule ->
        if (!rule.active) return@forEach
        if (!rule.matchesTreatment(updated.accountingTreatment)) return@forEach

        val phrase = rule.matchPhrase.trim()
        if (phrase.isBlank()) return@forEach

        if (!ruleMatchesTransaction(rule, updated, rawAlert)) return@forEach

        val ruleTreatment = rule.transactionType ?: updated.accountingTreatment
        val shouldApplyCategory = rule.shouldApplyCategoryTo(updated.accountingTreatment)

        updated = updated.copy(
            merchantRaw = if (!updated.merchantUserEdited) rule.merchantName ?: updated.merchantRaw else updated.merchantRaw,
            displayMerchantName = if (!updated.merchantUserEdited) rule.merchantName ?: updated.displayMerchantName else updated.displayMerchantName,
            categoryName = if (!updated.categoryUserEdited && shouldApplyCategory) rule.categoryName ?: updated.categoryName else updated.categoryName,
            transactionType = if (!updated.treatmentUserEdited) ruleTreatment else updated.transactionType,
            accountingTreatment = if (!updated.treatmentUserEdited) ruleTreatment else updated.accountingTreatment,
            reviewStatus = when {
                rule.requiresReview -> "NEEDS_REVIEW"
                rule.reviewStatus != null -> rule.reviewStatus
                else -> updated.reviewStatus
            },
            excludedFromSpending = if (!updated.treatmentUserEdited) {
                rule.excludedFromSpending ?: TransactionTreatments.defaultExcludedFromSpending(ruleTreatment)
            } else {
                updated.excludedFromSpending
            },
            updatedAtEpochMs = System.currentTimeMillis()
        )

        appliedRulePhrases.add(rule.matchPhrase)
    }

    val merchantName = (updated.displayMerchantName ?: updated.merchantRaw)
        ?.trim()

    if (!merchantName.isNullOrBlank()) {
        val normalizedMerchant = normalizeRulePhrase(merchantName)
        val merchantRule = merchantDefaultRules.firstOrNull { rule ->
            rule.active &&
                    rule.matchesTreatment(updated.accountingTreatment) &&
                    (
                            rule.normalizedMatchPhrase == normalizedMerchant ||
                                    normalizeRulePhrase(rule.merchantName ?: rule.matchPhrase) == normalizedMerchant
                            )
        }

        if (merchantRule != null) {
            val merchantTreatment = merchantRule.transactionType ?: updated.accountingTreatment
            val shouldApplyCategory = merchantRule.shouldApplyCategoryTo(updated.accountingTreatment)
            val categoryName = if (!updated.categoryUserEdited && shouldApplyCategory) {
                merchantRule.categoryName ?: updated.categoryName
            } else {
                updated.categoryName
            }

            updated = updated.copy(
                merchantRaw = if (!updated.merchantUserEdited) merchantRule.merchantName ?: updated.merchantRaw else updated.merchantRaw,
                displayMerchantName = if (!updated.merchantUserEdited) merchantRule.merchantName ?: updated.displayMerchantName else updated.displayMerchantName,
                categoryName = categoryName,
                transactionType = if (!updated.treatmentUserEdited) merchantTreatment else updated.transactionType,
                accountingTreatment = if (!updated.treatmentUserEdited) merchantTreatment else updated.accountingTreatment,
                excludedFromSpending = if (!updated.treatmentUserEdited) {
                    merchantRule.excludedFromSpending
                        ?: TransactionTreatments.defaultExcludedFromSpending(merchantTreatment)
                } else {
                    updated.excludedFromSpending
                },
                reviewStatus = when {
                    merchantRule.requiresReview -> "NEEDS_REVIEW"
                    !categoryName.isNullOrBlank() && updated.reviewStatus == "NEEDS_REVIEW" -> "AUTO_PARSED"
                    else -> updated.reviewStatus
                },
                updatedAtEpochMs = System.currentTimeMillis()
            )

            appliedRulePhrases.add("merchant default: ${merchantRule.matchPhrase}")
        }
    }

    if (appliedRulePhrases.isEmpty()) {
        return updated
    }

    val existingNotes = updated.parserNotes.orEmpty()
    val ruleNote = "Applied saved rule(s): ${appliedRulePhrases.joinToString(", ")}."

    return updated.copy(
        parserNotes = listOf(existingNotes, ruleNote)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    )
}

private fun TransactionRuleEntity.matchesTreatment(treatment: String): Boolean {
    return appliesToTreatment.isNullOrBlank() ||
            appliesToTreatment == treatment ||
            transactionType != null
}

private fun TransactionRuleEntity.shouldApplyCategoryTo(treatment: String): Boolean {
    if (!applyCategoryAutomatically || requiresReview) return false
    if (categoryName.isNullOrBlank()) return false
    return !appliesToTreatment.isNullOrBlank() ||
            treatment == TransactionTreatments.EXPENSE ||
            transactionType == treatment
}
