package com.example.ledgerlens.domain.parser

import java.util.Locale

object FinancialSmsClassifier {
    private val financeKeywords = listOf(
        "spent",
        "purchase",
        "transaction",
        "charged",
        "charge",
        "debit",
        "debited",
        "credit",
        "credited",
        "deposit",
        "withdrawal",
        "withdrawn",
        "payment",
        "paid",
        "balance",
        "card",
        "account",
        "acct",
        "atm",
        "pos",
        "zelle",
        "venmo",
        "cash app",
        "bank",
        "alert",
        "autopay",
        "refund",
        "authorized",
        "authorization",
        "available balance",
        "ending in",
        "hdfc",
        "chase",
        "capital one"
    )

    private val transactionSignals = listOf(
        "spent",
        "purchase",
        "charged",
        "charge at",
        "chrge or hold",
        "debit card purchase",
        "debit card transaction",
        "withdrawal",
        "withdrawn",
        "atm",
        "zelle",
        "venmo",
        "cash app",
        "deposit",
        "direct deposit",
        "refund",
        "credited back",
        "transfer",
        "deducted",
        "money received",
        "sent you",
        "you sent"
    )

    private val alwaysNonTransactionSignals = listOf(
        "is above the limit in your alert settings",
        "is below the limit in your alert settings",
        "was above the limit in your alert settings",
        "was below the limit in your alert settings",
        "change your alert settings",
        "alert threshold"
    )

    private val nonTransactionSignals = listOf(
        "otp",
        "one time password",
        "verification code",
        "security code",
        "transaction declined",
        "declined",
        "fraud alert",
        "did you attempt",
        "not done by you",
        "available balance",
        "statement balance",
        "minimum payment",
        "payment due",
        "due date",
        "available credit",
        "credit limit",
        "low balance",
        "balance is",
        "bal is",
        "available bal"
    )

    fun looksFinancial(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return MoneyExtractor.findAmounts(text).isNotEmpty() &&
            financeKeywords.any { lower.contains(it) }
    }

    fun looksTransactional(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return looksFinancial(text) &&
            !isLikelyNonTransactionFinancialAlert(text) &&
            transactionSignals.any { lower.contains(it) }
    }

    fun isLikelyNonTransactionFinancialAlert(text: String): Boolean {
        val lower = text.lowercase(Locale.US)

        if (alwaysNonTransactionSignals.any { lower.contains(it) }) {
            return true
        }

        if (nonTransactionSignals.take(9).any { lower.contains(it) }) {
            return true
        }

        if (transactionSignals.any { lower.contains(it) }) {
            return false
        }

        return nonTransactionSignals.drop(9).any { lower.contains(it) }
    }
}
