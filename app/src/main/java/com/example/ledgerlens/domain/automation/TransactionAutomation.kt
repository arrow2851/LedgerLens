package com.example.ledgerlens.domain.automation

import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import java.text.Normalizer
import java.util.Locale

data class AuthorizedCardholderLabel(
    val authorizedUserName: String,
    val accountHint: String
)

private val authorizedCardholderRegex = Regex(
    pattern = """(?ix)^\s*(.+?)[’']s\s+(?:(?:capital\s+one|chase|discover|amex|american\s+express)\s+)?(?:(?:venture|quicksilver|savor|freedom|slate|card)\s+)*credit\s+card\s*\((\d{4})\)\s*$"""
)

private val processorPrefixRegex = Regex(
    pattern = """(?i)^(?:TST\*|SQ\s*\*|SP\s+|CKE\*|PAYPAL\s*\*|PP\*|TOAST\*|CL\*|APL\*)"""
)

private val locationTokens = setOf(
    "TX",
    "TEXAS",
    "IRVING",
    "PLANO",
    "DALLAS",
    "MURPHY",
    "FRISCO",
    "RICHARDSON",
    "GARLAND",
    "CARROLLTON",
    "MCKINNEY"
)

fun parseAuthorizedCardholderLabel(value: String?): AuthorizedCardholderLabel? {
    val cleaned = value
        ?.replace('’', '\'')
        ?.trim()
        .orEmpty()
    val match = authorizedCardholderRegex.matchEntire(cleaned) ?: return null
    return AuthorizedCardholderLabel(
        authorizedUserName = match.groupValues[1].trim(),
        accountHint = match.groupValues[2]
    )
}

fun isPaymentInstrumentLabel(value: String?): Boolean {
    return parseAuthorizedCardholderLabel(value) != null
}

fun canonicalMerchantIdentity(value: String?): String {
    var cleaned = value.orEmpty()
        .replace('’', '\'')
        .trim()
        .uppercase(Locale.US)

    cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFKD)
    cleaned = processorPrefixRegex.replace(cleaned, "").trim()
    cleaned = cleaned
        .replace(Regex("""\b(?:STORE|SHOP|LOCATION)\s*#?\s*\d+\b"""), " ")
        .replace(Regex("""#\s*\d+\b"""), " ")
        .replace(Regex("""[^A-Z0-9]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    val tokens = cleaned
        .split(' ')
        .filter { token -> token.isNotBlank() && token !in locationTokens }
        .filterNot { token -> token in setOf("LLC", "INC", "CORP", "CO") }

    val normalized = tokens.joinToString(" ")
    return when {
        normalized.startsWith("WAL MART") ||
            normalized.startsWith("WALMART") ||
            normalized.startsWith("WM SUPERCENTER") -> "WALMART"

        normalized.startsWith("DECCAN MORSELS") -> "DECCAN MORSELS"
        normalized.startsWith("DESI DISTRICT") -> "DESI DISTRICT"
        normalized.startsWith("CHICHAS") -> "CHICHAS"
        normalized.startsWith("DESI CHOWRASTHA") -> "DESI CHOWRASTHA"
        normalized.startsWith("SPICE WOK") -> "SPICE WOK"
        normalized.startsWith("PLANO INDOPAK SUPERMAR") ||
            normalized.startsWith("INDOPAK SUPERMARKET") -> "INDOPAK SUPERMARKET"

        normalized.startsWith("4TE IACC") || normalized.startsWith("IACC") -> "IACC"
        else -> normalized
    }
}

fun transactionDirectionFromParserNotes(parserNotes: String?): TransactionTreatments.Direction {
    val lower = parserNotes.orEmpty().lowercase(Locale.US)
    return when {
        "direction: outgoing" in lower -> TransactionTreatments.Direction.OUTGOING
        "direction: incoming" in lower -> TransactionTreatments.Direction.INCOMING
        else -> TransactionTreatments.Direction.UNKNOWN
    }
}

fun resolvedReviewStatusAfterUserDecision(
    previousStatus: String,
    merchantChanged: Boolean,
    categoryChanged: Boolean,
    treatmentChanged: Boolean
): String {
    val userResolvedSomething = merchantChanged || categoryChanged || treatmentChanged
    return if (previousStatus == ReviewStatus.NEEDS_REVIEW && userResolvedSomething) {
        ReviewStatus.REVIEWED
    } else {
        previousStatus
    }
}
