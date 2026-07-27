package com.example.ledgerlens.domain.rules

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.automation.canonicalMerchantIdentity
import com.example.ledgerlens.domain.automation.isPaymentInstrumentLabel
import com.example.ledgerlens.domain.automation.transactionDirectionFromParserNotes

data class MerchantAliasRuleDraft(
    val sourceKey: String,
    val canonicalMerchantName: String,
    val aliases: List<String>,
    val applyCategory: Boolean = false,
    val categoryName: String? = null,
    val applyTreatment: Boolean = false,
    val transactionType: String? = null,
    val requiresReview: Boolean = false,
    val active: Boolean = true
) {
    val cleanedAliases: List<String>
        get() = aliases
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeAliasText(it) }
}

data class MerchantAliasPreviewItem(
    val transaction: TransactionEntity,
    val matchedAliases: List<String>,
    val willUpdateMerchant: Boolean,
    val willUpdateCategory: Boolean,
    val willUpdateTreatment: Boolean,
    val skippedMerchantUserEdited: Boolean
)

data class MerchantAliasApplyResult(
    val rulesSaved: Int,
    val matchedTransactions: Int,
    val merchantUpdated: Int,
    val categoryUpdated: Int,
    val treatmentUpdated: Int,
    val skippedMerchantUserEdited: Int
)

fun normalizeAliasText(value: String): String {
    return canonicalRuleText(value)
}

fun aliasMatchesText(alias: String, text: String?): Boolean {
    val cleanedAlias = alias.trim()
    if (cleanedAlias.isBlank() || text.isNullOrBlank()) return false
    if (text.contains(cleanedAlias, ignoreCase = true)) return true

    val normalizedAlias = normalizeAliasText(cleanedAlias)
    if (normalizedAlias.isBlank()) return false
    if (normalizeAliasText(text).contains(normalizedAlias)) return true

    val canonicalAlias = canonicalMerchantIdentity(cleanedAlias)
    val canonicalText = canonicalMerchantIdentity(text)
    return canonicalAlias.length >= 3 && canonicalAlias == canonicalText
}

fun ruleMatchesTransaction(
    rule: TransactionRuleEntity,
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity?
): Boolean {
    return aliasMatchesTransaction(rule.matchPhrase, transaction, rawAlert)
}

fun aliasMatchesTransaction(
    aliasValue: String,
    transaction: TransactionEntity,
    rawAlert: RawAlertEntity?
): Boolean {
    val alias = aliasValue.trim()
    if (alias.isBlank()) return false

    if (aliasMatchesText(alias, transaction.displayMerchantName) ||
        aliasMatchesText(alias, transaction.merchantRaw)
    ) {
        return true
    }

    if (isOverbroadRawAlias(alias)) {
        return false
    }

    return aliasMatchesText(alias, rawAlert?.combinedText)
}

private fun isOverbroadRawAlias(alias: String): Boolean {
    val normalized = normalizeAliasText(alias)
    if (normalized.length < 6) return true
    return normalized in setOf(
        "chase",
        "capitalone",
        "hdfc",
        "hdfcbank",
        "bank",
        "credit",
        "debit",
        "card",
        "visa",
        "mastercard",
        "transaction",
        "purchase",
        "payment",
        "creditcard",
        "debitcard"
    )
}

fun previewMerchantAliasRule(
    draft: MerchantAliasRuleDraft,
    transactions: List<TransactionEntity>,
    rawAlertsById: Map<Long, RawAlertEntity>
): List<MerchantAliasPreviewItem> {
    val aliases = draft.cleanedAliases
    if (aliases.isEmpty()) return emptyList()

    return transactions
        .filter { it.sourceKey == draft.sourceKey }
        .mapNotNull { transaction ->
            val rawAlert = rawAlertsById[transaction.rawAlertId]
            val matchedAliases = aliases.filter { alias ->
                aliasMatchesTransaction(alias, transaction, rawAlert)
            }

            if (matchedAliases.isEmpty()) {
                null
            } else {
                val requestedCanonicalName = draft.canonicalMerchantName.trim()
                val canonicalName = requestedCanonicalName.takeUnless(::isPaymentInstrumentLabel).orEmpty()
                val currentMerchant = transaction.displayMerchantName ?: transaction.merchantRaw
                val willUpdateMerchant = canonicalName.isNotBlank() &&
                    !transaction.merchantUserEdited &&
                    !currentMerchant.equals(canonicalName, ignoreCase = true)

                val categoryName = draft.categoryName?.trim()?.ifBlank { null }
                val willUpdateCategory = draft.applyCategory &&
                    !transaction.categoryUserEdited &&
                    transaction.categoryName != categoryName

                val treatment = draft.transactionType?.trim()?.ifBlank { null }
                val willUpdateTreatment = draft.applyTreatment &&
                    !transaction.treatmentUserEdited &&
                    treatment != null &&
                    transaction.accountingTreatment != treatment

                MerchantAliasPreviewItem(
                    transaction = transaction,
                    matchedAliases = matchedAliases,
                    willUpdateMerchant = willUpdateMerchant,
                    willUpdateCategory = willUpdateCategory,
                    willUpdateTreatment = willUpdateTreatment,
                    skippedMerchantUserEdited = canonicalName.isNotBlank() &&
                        transaction.merchantUserEdited &&
                        !currentMerchant.equals(canonicalName, ignoreCase = true)
                )
            }
        }
        .sortedByDescending { it.transaction.occurredAtEpochMs }
}

fun applyMerchantAliasRuleToTransaction(
    transaction: TransactionEntity,
    draft: MerchantAliasRuleDraft,
    now: Long = System.currentTimeMillis()
): TransactionEntity {
    val requestedCanonicalName = draft.canonicalMerchantName.trim().ifBlank { null }
    val canonicalName = requestedCanonicalName.takeUnless(::isPaymentInstrumentLabel)
    val categoryName = draft.categoryName?.trim()?.ifBlank { null }
    val treatment = draft.transactionType?.trim()?.ifBlank { null }

    val updatedMerchant = if (!transaction.merchantUserEdited) {
        canonicalName ?: transaction.displayMerchantName
    } else {
        transaction.displayMerchantName
    }

    val updatedCategory = if (draft.applyCategory && !transaction.categoryUserEdited) {
        categoryName
    } else {
        transaction.categoryName
    }

    val updatedTreatment = if (draft.applyTreatment && !transaction.treatmentUserEdited && treatment != null) {
        treatment
    } else {
        transaction.accountingTreatment
    }

    val treatmentWasApplied = draft.applyTreatment &&
        !transaction.treatmentUserEdited &&
        treatment != null
    val direction = transactionDirectionFromParserNotes(transaction.parserNotes)
    val updatedExcluded = if (treatmentWasApplied) {
        TransactionTreatments.defaultExcludedFromSpending(
            treatment = updatedTreatment,
            direction = direction
        )
    } else {
        transaction.excludedFromSpending
    }

    val ruleResolvedTransaction =
        (!canonicalName.isNullOrBlank() || !updatedMerchant.isNullOrBlank()) &&
            (!draft.applyCategory || !updatedCategory.isNullOrBlank()) &&
            (!draft.applyTreatment || treatmentWasApplied)

    return transaction.copy(
        merchantRaw = if (!transaction.merchantUserEdited) updatedMerchant else transaction.merchantRaw,
        displayMerchantName = updatedMerchant,
        categoryName = updatedCategory,
        transactionType = if (treatmentWasApplied) updatedTreatment else transaction.transactionType,
        accountingTreatment = updatedTreatment,
        excludedFromSpending = updatedExcluded,
        reviewStatus = when {
            draft.requiresReview -> ReviewStatus.NEEDS_REVIEW
            transaction.reviewStatus == ReviewStatus.NEEDS_REVIEW && ruleResolvedTransaction -> ReviewStatus.REVIEWED
            else -> transaction.reviewStatus
        },
        updatedAtEpochMs = now
    )
}

fun buildMerchantAliasRules(
    draft: MerchantAliasRuleDraft,
    now: Long = System.currentTimeMillis()
): List<TransactionRuleEntity> {
    val requestedCanonicalName = draft.canonicalMerchantName.trim().ifBlank { null }
    val canonicalName = requestedCanonicalName.takeUnless(::isPaymentInstrumentLabel)
    val treatment = if (draft.applyTreatment) {
        draft.transactionType?.trim()?.ifBlank { null }
    } else {
        null
    }

    return draft.cleanedAliases.map { alias ->
        TransactionRuleEntity(
            sourceKey = draft.sourceKey,
            matchPhrase = alias,
            normalizedMatchPhrase = normalizeRulePhrase(alias),
            ruleKind = RuleKind.SOURCE_ALIAS,
            merchantName = canonicalName,
            categoryName = if (draft.applyCategory) draft.categoryName?.trim()?.ifBlank { null } else null,
            transactionType = treatment,
            reviewStatus = when {
                draft.requiresReview -> ReviewStatus.NEEDS_REVIEW
                treatment != null -> ReviewStatus.REVIEWED
                else -> null
            },
            excludedFromSpending = treatment?.let { selectedTreatment ->
                TransactionTreatments.defaultExcludedFromSpending(selectedTreatment)
            },
            appliesToTreatment = null,
            applyCategoryAutomatically = draft.applyCategory,
            requiresReview = draft.requiresReview,
            active = draft.active,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
    }
}
