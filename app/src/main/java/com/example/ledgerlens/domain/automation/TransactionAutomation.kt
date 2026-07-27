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

    val normalized = cleaned
        .split(' ')
        .filter { it.isNotBlank() }
        .filterNot { it in setOf("LLC", "INC", "CORP", "CO") }
        .joinToString(" ")

    return when {
        normalized.startsWith("WAL MART") ||
            normalized.startsWith("WALMART") ||
            normalized.startsWith("WM SUPERCENTER") -> "WALMART"

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
