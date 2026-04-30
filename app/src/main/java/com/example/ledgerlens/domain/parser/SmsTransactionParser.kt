package com.example.ledgerlens.domain.parser

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import java.util.Locale
import kotlin.math.roundToLong
import com.example.ledgerlens.data.entity.FinancialSourceEntity

object SmsTransactionParser {

    fun parse(
        rawAlert: RawAlertEntity,
        source: FinancialSourceEntity? = null
    ): TransactionEntity? {
        val body = rawAlert.combinedText.trim()
        if (body.isBlank()) return null

        val amountCents = extractAmountCents(body) ?: return null
        val lower = body.lowercase(Locale.US)

        val transactionType = inferTransactionType(lower)
        val accountHint = extractAccountHint(body)
            ?: source?.accountHint?.takeIf { !it.contains(",") }

        val institution = source?.institutionName
            ?: inferInstitution(body)
        val merchant = extractMerchant(body, transactionType)

        val isZelle = lower.contains("zelle")
        val excludedFromSpending = transactionType in setOf(
            "TRANSFER",
            "CREDIT_CARD_PAYMENT"
        )

        val reviewStatus = when {
            isZelle -> "NEEDS_REVIEW"
            transactionType == "UNKNOWN" -> "NEEDS_REVIEW"
            merchant.isNullOrBlank() && transactionType == "EXPENSE" -> "NEEDS_REVIEW"
            else -> "AUTO_PARSED"
        }

        val confidence = when {
            reviewStatus == "NEEDS_REVIEW" -> 0.55
            merchant != null && accountHint != null -> 0.85
            merchant != null -> 0.75
            else -> 0.65
        }

        val notes = buildList {
            if (isZelle) add("Zelle detected; user review recommended.")
            if (excludedFromSpending) add("Excluded from spending totals by default.")
            if (merchant == null) add("Merchant/payee not confidently extracted.")
        }.joinToString(" ").ifBlank { null }

        val now = System.currentTimeMillis()

        return TransactionEntity(
            rawAlertId = rawAlert.id,
            sourceKey = source?.sourceKey ?: rawAlert.notificationKey,
            transactionType = transactionType,
            amountCents = amountCents,
            currency = "USD",
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = institution,
            accountHint = accountHint,
            categoryName = null,
            subcategoryName = null,
            occurredAtEpochMs = rawAlert.postTimeEpochMs,
            receivedAtEpochMs = rawAlert.capturedAtEpochMs,
            parseConfidence = confidence,
            reviewStatus = reviewStatus,
            excludedFromSpending = excludedFromSpending,
            parserNotes = notes,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
    }

    private fun extractAmountCents(text: String): Long? {
        val regex = Regex(
            pattern = """(?i)(?:\$|usd\s*)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{2})|[0-9]+(?:\.[0-9]{2}))"""
        )

        val match = regex.find(text) ?: return null
        val amountText = match.groupValues[1].replace(",", "")

        return amountText
            .toDoubleOrNull()
            ?.let { (it * 100).roundToLong() }
    }

    private fun inferTransactionType(lower: String): String {
        return when {
            lower.contains("zelle") -> "TRANSFER"

            lower.contains("credit card payment") ||
                    lower.contains("payment to your credit card") ||
                    lower.contains("payment was made") ||
                    lower.contains("payment received") && lower.contains("card") -> {
                "CREDIT_CARD_PAYMENT"
            }

            lower.contains("refund") ||
                    lower.contains("credited back") -> {
                "REFUND"
            }

            lower.contains("direct deposit") ||
                    lower.contains("deposit") ||
                    lower.contains("credited to your account") ||
                    lower.contains("was credited") -> {
                "INCOME"
            }

            lower.contains("spent") ||
                    lower.contains("purchase") ||
                    lower.contains("charged") ||
                    lower.contains("charge") ||
                    lower.contains("debit card purchase") ||
                    lower.contains("transaction") ||
                    lower.contains("pos") -> {
                "EXPENSE"
            }

            lower.contains("withdrawal") ||
                    lower.contains("atm") -> {
                "EXPENSE"
            }

            lower.contains("transfer") ||
                    lower.contains("ach") -> {
                "TRANSFER"
            }

            else -> "UNKNOWN"
        }
    }

    private fun extractAccountHint(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)(?:ending in|ending|ends in|card ending|account ending|acct ending)\s*([0-9]{4})"""),
            Regex("""(?i)(?:x{2,}|X{2,}|\*{2,})\s*([0-9]{4})"""),
            Regex("""(?i)(?:card|account|acct)\s*(?:\*+|x+)?\s*([0-9]{4})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    private fun inferInstitution(text: String): String? {
        val lower = text.lowercase(Locale.US)

        return when {
            lower.contains("chase") -> "Chase"
            lower.contains("capital one") || lower.contains("capitalone") -> "Capital One"
            lower.contains("discover") -> "Discover"
            lower.contains("american express") || lower.contains("amex") -> "American Express"
            lower.contains("bank of america") || lower.contains("bofa") -> "Bank of America"
            lower.contains("wells fargo") -> "Wells Fargo"
            lower.contains("citi") || lower.contains("citibank") -> "Citi"
            lower.contains("us bank") || lower.contains("u.s. bank") -> "US Bank"
            lower.contains("apple card") -> "Apple Card"
            else -> null
        }
    }

    private fun extractMerchant(text: String, transactionType: String): String? {
        if (transactionType !in setOf("EXPENSE", "REFUND", "TRANSFER")) {
            return null
        }

        val patterns = listOf(
            Regex("""(?i)\bat\s+(.+?)(?:\.|,| on | for |\$|$)"""),
            Regex("""(?i)\bfrom\s+(.+?)(?:\.|,| on | for |\$|$)"""),
            Regex("""(?i)\bto\s+(.+?)(?:\.|,| on | for |\$|$)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            val candidate = match?.groupValues?.getOrNull(1)
                ?.trim()
                ?.trim('-', ':', '.', ',')

            if (!candidate.isNullOrBlank() && candidate.length >= 2) {
                return candidate.take(80)
            }
        }

        return null
    }
}