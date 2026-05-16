package com.example.ledgerlens.ui

import java.util.Locale

fun formatMoney(
    minorUnits: Long,
    currency: String,
    signed: Boolean = false
): String {
    val normalizedCurrency = currency.trim().uppercase(Locale.US).ifBlank { "USD" }
    val sign = when {
        !signed -> ""
        minorUnits < 0 -> "-"
        minorUnits > 0 -> "+"
        else -> ""
    }
    val amount = "%.2f".format(Locale.US, kotlin.math.abs(minorUnits) / 100.0)

    return when (normalizedCurrency) {
        "USD" -> "$sign${'$'}$amount"
        "INR" -> "${sign}INR $amount"
        else -> "$sign$normalizedCurrency $amount"
    }
}

fun formatSignedMoney(cents: Long, currency: String = "USD"): String {
    return formatMoney(cents, currency, signed = true)
}

fun formatCurrencyTotals(amountsByCurrency: Map<String, Long>): String {
    if (amountsByCurrency.isEmpty()) return formatMoney(0, "USD")
    return amountsByCurrency
        .toSortedMap()
        .map { (currency, amount) -> formatMoney(amount, currency, signed = true) }
        .joinToString(" / ")
}
