package com.example.ledgerlens.domain

object TransactionTreatments {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val REFUND = "REFUND"
    const val REIMBURSEMENT = "REIMBURSEMENT"
    const val CREDIT_CARD_PAYMENT = "CREDIT_CARD_PAYMENT"
    const val TRANSFER = "TRANSFER"
    const val PERSON_TO_PERSON = "PERSON_TO_PERSON"
    const val UNKNOWN = "UNKNOWN"

    val movementTreatments = setOf(
        CREDIT_CARD_PAYMENT,
        TRANSFER,
        PERSON_TO_PERSON
    )

    fun countsAsSpending(treatment: String, excludedFromSpending: Boolean): Boolean {
        return isInSpendingView(treatment, excludedFromSpending)
    }

    fun isInSpendingView(treatment: String, excludedFromSpending: Boolean): Boolean {
        return spendingImpactCents(
            treatment = treatment,
            excludedFromSpending = excludedFromSpending,
            amountCents = 1
        ) != 0L
    }

    fun isRefundLike(treatment: String): Boolean {
        return treatment == REFUND || treatment == REIMBURSEMENT
    }

    fun spendingImpactCents(
        treatment: String,
        excludedFromSpending: Boolean,
        amountCents: Long
    ): Long {
        if (excludedFromSpending) return 0

        return when (treatment) {
            EXPENSE -> amountCents
            REFUND,
            REIMBURSEMENT -> -amountCents
            else -> 0
        }
    }

    fun defaultExcludedFromSpending(treatment: String): Boolean {
        return treatment !in setOf(EXPENSE, REFUND, REIMBURSEMENT)
    }
}
