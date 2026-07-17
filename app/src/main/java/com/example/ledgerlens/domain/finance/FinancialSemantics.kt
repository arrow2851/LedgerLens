package com.example.ledgerlens.domain.finance

import java.util.Locale

/**
 * A non-negative monetary magnitude in the currency's smallest unit.
 *
 * Transaction amounts are stored as magnitudes. Account-flow direction and
 * spending effect are modeled separately so refunds, transfers, and payments
 * do not depend on overloaded signed amounts.
 */
@JvmInline
value class MinorAmount(val value: Long) {
    init {
        require(value >= 0L) { "Minor amount must be non-negative." }
    }
}

/** ISO-4217-style uppercase, three-letter currency code. */
@JvmInline
value class CurrencyCode(val value: String) {
    init {
        require(value.length == 3 && value.all { it in 'A'..'Z' }) {
            "Currency code must contain exactly three uppercase ASCII letters."
        }
    }

    companion object {
        val USD: CurrencyCode = CurrencyCode("USD")

        fun of(raw: String): CurrencyCode = CurrencyCode(
            raw.trim().uppercase(Locale.US)
        )
    }
}

data class Money(
    val amount: MinorAmount,
    val currency: CurrencyCode = CurrencyCode.USD,
)

enum class TransactionTreatment {
    PURCHASE,
    FEE,
    INCOME,
    TRANSFER,
    CREDIT_CARD_PAYMENT,
    REFUND,
    REVERSAL,
    REIMBURSEMENT,
    CASH_WITHDRAWAL,
    INFORMATIONAL,
    UNKNOWN,
}

enum class MoneyDirection {
    DEBIT,
    CREDIT,
    NONE,
    UNKNOWN,
}

enum class SpendingEffect {
    INCREASE,
    DECREASE,
    NONE,
    UNRESOLVED,
}

enum class ReviewReason {
    TREATMENT_UNKNOWN,
    DIRECTION_UNKNOWN,
    SPENDING_EFFECT_UNRESOLVED,
}

/**
 * Minimal domain event used to establish financial semantics independently of
 * Room, Android SMS APIs, and UI state.
 */
data class FinancialEvent(
    val id: String,
    val money: Money,
    val treatment: TransactionTreatment,
    val direction: MoneyDirection,
    val spendingEffectOverride: SpendingEffect? = null,
) {
    init {
        require(id.isNotBlank()) { "Financial event id must not be blank." }
    }

    val effectiveSpendingEffect: SpendingEffect
        get() = spendingEffectOverride ?: SpendingPolicy.defaultEffect(treatment)

    fun spendingContributionMinor(): Long = SpendingPolicy.contributionMinor(
        amount = money.amount,
        effect = effectiveSpendingEffect,
    )

    fun reviewReasons(): Set<ReviewReason> = buildSet {
        if (treatment == TransactionTreatment.UNKNOWN) {
            add(ReviewReason.TREATMENT_UNKNOWN)
        }

        if (
            direction == MoneyDirection.UNKNOWN &&
            treatment != TransactionTreatment.INFORMATIONAL
        ) {
            add(ReviewReason.DIRECTION_UNKNOWN)
        }

        if (effectiveSpendingEffect == SpendingEffect.UNRESOLVED) {
            add(ReviewReason.SPENDING_EFFECT_UNRESOLVED)
        }
    }
}

data class SpendingTotal(
    /** Signed amount: positive increases spending; negative reduces spending. */
    val amountMinor: Long,
    val currency: CurrencyCode,
)

object SpendingPolicy {
    fun defaultEffect(treatment: TransactionTreatment): SpendingEffect = when (treatment) {
        TransactionTreatment.PURCHASE,
        TransactionTreatment.FEE,
        -> SpendingEffect.INCREASE

        TransactionTreatment.REFUND,
        TransactionTreatment.REVERSAL,
        TransactionTreatment.REIMBURSEMENT,
        -> SpendingEffect.DECREASE

        TransactionTreatment.INCOME,
        TransactionTreatment.TRANSFER,
        TransactionTreatment.CREDIT_CARD_PAYMENT,
        TransactionTreatment.INFORMATIONAL,
        -> SpendingEffect.NONE

        TransactionTreatment.CASH_WITHDRAWAL,
        TransactionTreatment.UNKNOWN,
        -> SpendingEffect.UNRESOLVED
    }

    fun contributionMinor(
        amount: MinorAmount,
        effect: SpendingEffect,
    ): Long = when (effect) {
        SpendingEffect.INCREASE -> amount.value
        SpendingEffect.DECREASE -> -amount.value
        SpendingEffect.NONE,
        SpendingEffect.UNRESOLVED,
        -> 0L
    }

    fun total(
        events: Iterable<FinancialEvent>,
        currency: CurrencyCode = CurrencyCode.USD,
    ): SpendingTotal {
        var totalMinor = 0L

        events.forEach { event ->
            require(event.money.currency == currency) {
                "Cannot total ${event.money.currency.value} in ${currency.value} spending."
            }

            totalMinor = Math.addExact(
                totalMinor,
                event.spendingContributionMinor(),
            )
        }

        return SpendingTotal(
            amountMinor = totalMinor,
            currency = currency,
        )
    }
}
