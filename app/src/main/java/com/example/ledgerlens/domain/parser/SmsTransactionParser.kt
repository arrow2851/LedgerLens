package com.example.ledgerlens.domain.parser

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import java.util.Locale
import kotlin.math.roundToLong
import com.example.ledgerlens.data.entity.FinancialSourceEntity

object SmsTransactionParser {

    private data class MerchantExtraction(
        val merchant: String,
        val method: String,
        val confidenceBonus: Double = 0.0
    )

    fun isNonTransactionAlert(rawAlert: RawAlertEntity): Boolean {
        return looksLikeNonTransactionAlert(
            rawAlert.combinedText.trim().lowercase(Locale.US)
        )
    }

    fun parse(
        rawAlert: RawAlertEntity,
        source: FinancialSourceEntity? = null,
        sourceMessages: List<String> = emptyList()
    ): TransactionEntity? {
        val body = rawAlert.combinedText.trim()
        if (body.isBlank()) return null

        val lower = body.lowercase(Locale.US)
        if (isNonTransactionAlert(rawAlert)) return null

        val amountCents = extractAmountCents(body) ?: return null

        val accountingTreatment = inferAccountingTreatment(lower)
        val transactionType = accountingTreatment
        val accountHint = extractAccountHint(body)
            ?: source?.accountHint?.takeIf { !it.contains(",") }

        val institution = source?.institutionName
            ?: inferInstitution(body)
        val merchantExtraction = extractMerchant(
            text = body,
            accountingTreatment = accountingTreatment,
            sourceMessages = sourceMessages
        )
        val merchant = merchantExtraction?.merchant
        val categorySuggestion = suggestCategory(
            merchant = merchant,
            lower = lower,
            accountingTreatment = accountingTreatment
        )

        val isZelle = lower.contains("zelle")
        val excludedFromSpending = TransactionTreatments.defaultExcludedFromSpending(accountingTreatment)

        val reviewStatus = when {
            isZelle -> "NEEDS_REVIEW"
            accountingTreatment == TransactionTreatments.PERSON_TO_PERSON -> "NEEDS_REVIEW"
            accountingTreatment == TransactionTreatments.UNKNOWN -> "NEEDS_REVIEW"
            merchant.isNullOrBlank() && accountingTreatment == TransactionTreatments.EXPENSE -> "NEEDS_REVIEW"
            else -> "AUTO_PARSED"
        }

        val confidence = when {
            reviewStatus == "NEEDS_REVIEW" -> 0.55
            merchant != null && categorySuggestion != null && accountHint != null -> 0.88 + (merchantExtraction?.confidenceBonus ?: 0.0)
            merchant != null && accountHint != null -> 0.84 + (merchantExtraction?.confidenceBonus ?: 0.0)
            merchant != null && categorySuggestion != null -> 0.80 + (merchantExtraction?.confidenceBonus ?: 0.0)
            merchant != null -> 0.74 + (merchantExtraction?.confidenceBonus ?: 0.0)
            else -> 0.65
        }.coerceAtMost(0.95)

        val notes = buildList {
            add("Accounting treatment: $accountingTreatment.")
            if (merchantExtraction != null) {
                add("Merchant extracted by ${merchantExtraction.method}.")
            }
            if (categorySuggestion != null) {
                add("Suggested category: ${categorySuggestion.first}/${categorySuggestion.second}.")
            }
            if (isZelle) add("Zelle detected; user review recommended.")
            if (excludedFromSpending) add("Retained in activity; not counted in Spending Summary.")
            if (merchant == null) add("Merchant/payee not confidently extracted.")
            if (accountingTreatment == TransactionTreatments.UNKNOWN) add("Could not confidently classify accounting treatment.")
        }.joinToString(" ").ifBlank { null }

        val now = System.currentTimeMillis()

        return TransactionEntity(
            rawAlertId = rawAlert.id,
            sourceKey = source?.sourceKey ?: rawAlert.notificationKey,
            transactionType = transactionType,
            accountingTreatment = accountingTreatment,
            amountCents = amountCents,
            currency = "USD",
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = institution,
            accountHint = accountHint,
            categoryName = categorySuggestion?.first,
            subcategoryName = categorySuggestion?.second,
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

    private fun inferAccountingTreatment(lower: String): String {
        return when {
            lower.contains("zelle") ||
                    lower.contains("venmo") ||
                    lower.contains("cash app") -> TransactionTreatments.PERSON_TO_PERSON

            lower.contains("credit card payment") ||
                    lower.contains("payment to your credit card") ||
                    lower.contains("payment was made") ||
                    (lower.contains("payment received") && lower.contains("card")) -> {
                TransactionTreatments.CREDIT_CARD_PAYMENT
            }

            lower.contains("refund") ||
                    lower.contains("credited back") -> {
                TransactionTreatments.REFUND
            }

            lower.contains("direct deposit") ||
                    lower.contains("deposit") ||
                    lower.contains("credited to your account") ||
                    lower.contains("was credited") -> {
                TransactionTreatments.INCOME
            }

            lower.contains("spent") ||
                    lower.contains("purchase") ||
                    lower.contains("charged") ||
                    lower.contains("charge") ||
                    lower.contains("debit card purchase") ||
                    lower.contains("transaction") ||
                    lower.contains("pos") -> {
                TransactionTreatments.EXPENSE
            }

            lower.contains("withdrawal") ||
                    lower.contains("atm") -> {
                TransactionTreatments.EXPENSE
            }

            lower.contains("transfer") ||
                    lower.contains("ach") -> {
                TransactionTreatments.TRANSFER
            }

            else -> TransactionTreatments.UNKNOWN
        }
    }

    private fun looksLikeNonTransactionAlert(lower: String): Boolean {
        val hasTransactionSignal = listOf(
            "spent",
            "purchase",
            "charged",
            "charge at",
            "debit card purchase",
            "withdrawal",
            "atm",
            "zelle",
            "venmo",
            "cash app",
            "deposit",
            "direct deposit",
            "refund",
            "credited back",
            "transfer"
        ).any { lower.contains(it) }

        if (hasTransactionSignal) return false

        return listOf(
            "available balance",
            "statement balance",
            "minimum payment",
            "payment due",
            "due date",
            "available credit",
            "credit limit",
            "security code",
            "verification code",
            "fraud alert",
            "did you attempt",
            "low balance",
            "balance is"
        ).any { lower.contains(it) }
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

    private fun extractMerchant(
        text: String,
        accountingTreatment: String,
        sourceMessages: List<String>
    ): MerchantExtraction? {
        if (accountingTreatment !in setOf(
                TransactionTreatments.EXPENSE,
                TransactionTreatments.REFUND,
                TransactionTreatments.TRANSFER,
                TransactionTreatments.PERSON_TO_PERSON,
                TransactionTreatments.CREDIT_CARD_PAYMENT,
                TransactionTreatments.INCOME
            )
        ) {
            return null
        }

        val patterns = listOf(
            "action merchant" to Regex("""(?i)\b(?:purchase|transaction|charge)\s+(?:at|from|with)\s+(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)"""),
            "charged merchant" to Regex("""(?i)\bcharged\s+(?:by|at|to)\s+(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)"""),
            "spent merchant" to Regex("""(?i)\bspent\s+(?:\$?[0-9,]+(?:\.[0-9]{2})?\s+)?at\s+(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)"""),
            "amount at merchant" to Regex("""(?i)(?:\$|usd\s*)\s*[0-9,]+(?:\.[0-9]{2})?\s+(?:at|from|with)\s+(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)"""),
            "person transfer" to Regex("""(?i)\b(?:sent|paid|transferred)\s+(?:\$?[0-9,]+(?:\.[0-9]{2})?\s+)?to\s+(.+?)(?:\s+(?:with|via)\s+(?:zelle|venmo|cash app)|\s+on\s+|[.,]|$)"""),
            "incoming from" to Regex("""(?i)\b(?:received|deposit|credited)\s+(?:\$?[0-9,]+(?:\.[0-9]{2})?\s+)?(?:from|by)\s+(.+?)(?:\s+(?:with|via)\s+(?:zelle|venmo|cash app)|\s+on\s+|[.,]|$)"""),
            "paid to" to Regex("""(?i)\bpaid\s+(?:\$?[0-9,]+(?:\.[0-9]{2})?\s+)?(?:to\s+)?(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)"""),
            "generic at" to Regex("""(?i)\bat\s+(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)""")
        )

        for ((method, pattern) in patterns) {
            val match = pattern.find(text)
            val candidate = match?.groupValues?.getOrNull(1)
                ?.trim()
                ?.cleanMerchantCandidate()

            if (!candidate.isNullOrBlank() && candidate.length >= 2) {
                return MerchantExtraction(
                    merchant = candidate.take(80),
                    method = method,
                    confidenceBonus = 0.02
                )
            }
        }

        val templateCandidate = extractTemplateOutlierMerchant(
            text = text,
            sourceMessages = sourceMessages
        )
        if (!templateCandidate.isNullOrBlank()) {
            return MerchantExtraction(
                merchant = templateCandidate.take(80),
                method = "source template outlier",
                confidenceBonus = 0.04
            )
        }

        return null
    }

    private fun suggestCategory(
        merchant: String?,
        lower: String,
        accountingTreatment: String
    ): Pair<String, String>? {
        if (accountingTreatment != TransactionTreatments.EXPENSE) return null

        val haystack = listOfNotNull(merchant?.lowercase(Locale.US), lower)
            .joinToString(" ")

        return when {
            containsAny(haystack, "walmart", "target", "costco", "sam's club", "sams club", "aldi", "kroger", "whole foods", "trader joe") ->
                "Groceries" to "General"

            containsAny(haystack, "shell", "exxon", "chevron", "bp ", "mobil", "speedway", "circle k", "gas", "fuel") ->
                "Gas" to "Fuel"

            containsAny(haystack, "netflix", "spotify", "hulu", "disney", "youtube", "apple.com/bill", "google", "subscription") ->
                "Subscriptions" to "General"

            containsAny(haystack, "mcdonald", "starbucks", "chipotle", "restaurant", "cafe", "doordash", "uber eats", "grubhub") ->
                "Restaurants" to "Dining Out"

            containsAny(haystack, "walgreens", "cvs", "pharmacy", "clinic", "doctor", "hospital") ->
                "Healthcare" to "General"

            containsAny(haystack, "uber", "lyft", "airline", "hotel", "airbnb", "parking") ->
                "Travel" to "General"

            containsAny(haystack, "masjid", "mosque", "islamic", "donation", "charity", "zakat", "sadaqah") ->
                "Charity" to "Donation"

            else -> null
        }
    }

    private fun containsAny(text: String, vararg needles: String): Boolean {
        return needles.any { text.contains(it) }
    }

    private fun extractTemplateOutlierMerchant(
        text: String,
        sourceMessages: List<String>
    ): String? {
        if (sourceMessages.size < 3) return null

        val tokenCounts = sourceMessages
            .flatMap { normalizeTemplateTokens(it).toSet() }
            .groupingBy { it }
            .eachCount()

        val variableTokens = normalizeTemplateTokens(text)
            .filter { token ->
                token.length >= 3 &&
                        token !in merchantStopWords &&
                        (tokenCounts[token] ?: 0) <= 1
            }

        val bestRun = variableTokens
            .fold(mutableListOf<MutableList<String>>()) { runs, token ->
                val last = runs.lastOrNull()
                if (last == null || token in merchantStopWords) {
                    runs.add(mutableListOf(token))
                } else {
                    last.add(token)
                }
                runs
            }
            .filter { it.isNotEmpty() }
            .maxByOrNull { run -> run.sumOf { it.length } }

        return bestRun
            ?.joinToString(" ")
            ?.cleanMerchantCandidate()
            ?.takeIf { it.length >= 3 }
            ?.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
    }

    private fun normalizeTemplateTokens(text: String): List<String> {
        return text
            .lowercase(Locale.US)
            .replace(Regex("""\$?\d+(?:,\d{3})*(?:\.\d{2})?"""), " ")
            .replace(Regex("""(?:x+|\*+)\d{4}"""), " ")
            .split(Regex("""[^a-z0-9']+"""))
            .filter { it.isNotBlank() }
    }

    private fun String.cleanMerchantCandidate(): String {
        return trim()
            .trim('-', ':', '.', ',', ';')
            .replace(Regex("""(?i)\s+(?:with|via)\s+(?:zelle|venmo|cash app).*$"""), "")
            .replace(Regex("""(?i)\s+on\s+(?:card|account).*$"""), "")
            .replace(Regex("""(?i)\s+(?:card|account|acct)\s+(?:ending|x+|\*+).*$"""), "")
            .replace(Regex("""(?i)\s+ending\s+(?:in\s+)?\d{4}.*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('-', ':', '.', ',', ';')
    }

    private val merchantStopWords = setOf(
        "alert",
        "available",
        "balance",
        "card",
        "account",
        "acct",
        "ending",
        "your",
        "you",
        "was",
        "were",
        "has",
        "have",
        "the",
        "and",
        "for",
        "from",
        "with",
        "via",
        "transaction",
        "purchase",
        "charged",
        "spent",
        "payment",
        "deposit",
        "transfer",
        "zelle",
        "venmo",
        "cash",
        "app",
        "usd"
    )
}
