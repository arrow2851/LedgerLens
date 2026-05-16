package com.example.ledgerlens.domain.parser

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class MoneyAmountCandidate(
    val rawText: String,
    val minorUnits: Long,
    val currency: String,
    val startIndex: Int,
    val endIndex: Int
)

object MoneyExtractor {
    private const val RUPEE_SYMBOL = "\u20B9"

    val MONEY_AMOUNT_PATTERN: String =
        "(?:(?:\\$|$RUPEE_SYMBOL|usd|rs\\.?|inr)\\s*)?" +
            "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)" +
            "(?:\\.[0-9]{1,2})?" +
            "(?:\\s*(?:usd|inr))?" +
            "(?![0-9.])"

    private val amountRegex = Regex(
        pattern =
            "(?i)(\\$|$RUPEE_SYMBOL|usd|rs\\.?|inr)?\\s*" +
                "((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?)" +
                "(?:\\s*(usd|inr))?(?![0-9.])"
    )

    fun findAmounts(text: String): List<MoneyAmountCandidate> {
        return amountRegex.findAll(text)
            .mapNotNull { match ->
                val prefix = match.groupValues.getOrNull(1).orEmpty()
                val amountText = match.groupValues.getOrNull(2).orEmpty()
                val suffix = match.groupValues.getOrNull(3).orEmpty()
                val hasCurrencyMarker = prefix.isNotBlank() || suffix.isNotBlank()
                val hasDecimal = amountText.contains(".")

                if (!hasCurrencyMarker && !hasDecimal) {
                    return@mapNotNull null
                }

                val minorUnits = parseMinorUnits(amountText) ?: return@mapNotNull null
                MoneyAmountCandidate(
                    rawText = match.value.trim(),
                    minorUnits = minorUnits,
                    currency = inferCurrency(prefix = prefix, suffix = suffix),
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1
                )
            }
            .toList()
    }

    fun parseAmountToMinorUnits(text: String): Long? {
        return findAmounts(text).firstOrNull()?.minorUnits
    }

    fun inferCurrency(text: String): String {
        return findAmounts(text).firstOrNull()?.currency ?: "USD"
    }

    private fun parseMinorUnits(amountText: String): Long? {
        return runCatching {
            BigDecimal(amountText.replace(",", ""))
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }

    private fun inferCurrency(prefix: String, suffix: String): String {
        val token = (prefix.ifBlank { suffix }).lowercase(Locale.US)
        return when {
            token.startsWith("rs") || token == "inr" || token == RUPEE_SYMBOL.lowercase(Locale.US) -> "INR"
            else -> "USD"
        }
    }
}
