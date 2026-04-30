package com.example.ledgerlens.domain.source

import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import java.util.Locale

object SourceDetector {

    fun detect(rawAlerts: List<RawAlertEntity>): List<FinancialSourceEntity> {
        val now = System.currentTimeMillis()

        val smsAlerts = rawAlerts
            .filter { it.sourcePackage == "sms" }
            .filter { it.combinedText.isNotBlank() }

        // IMPORTANT:
        // Top-level source is now ONLY the SMS sender/address.
        // Institutions, account/card hints, and message types are summaries under that sender.
        val groupedBySender = smsAlerts.groupBy { alert ->
            extractSmsAddress(alert)
        }

        return groupedBySender.map { (sourceAddress, alerts) ->
            val firstSeen = alerts.minOf { it.postTimeEpochMs }
            val lastSeen = alerts.maxOf { it.postTimeEpochMs }

            val messages = alerts.map { it.combinedText }

            val institutions = messages
                .mapNotNull { inferInstitution(it) }
                .distinct()
                .sorted()

            val accountHints = messages
                .mapNotNull { extractAccountHint(it) }
                .distinct()
                .sorted()

            val likelyType = inferGroupAccountType(messages)

            val institutionSummary = buildInstitutionSummary(institutions)
            val accountHintSummary = buildAccountHintSummary(accountHints)

            val confidence = calculateConfidence(
                institutions = institutions,
                accountHints = accountHints,
                suggestedAccountType = likelyType
            )

            val sample = alerts
                .maxByOrNull { it.postTimeEpochMs }
                ?.combinedText
                ?.take(300)

            FinancialSourceEntity(
                sourceKey = buildSenderSourceKey(sourceAddress),
                sourceAddress = sourceAddress,
                institutionName = institutionSummary,
                accountHint = accountHintSummary,
                suggestedAccountType = likelyType,
                confirmedAccountType = null,
                displayName = buildDisplayName(
                    sourceAddress = sourceAddress,
                    institutionSummary = institutionSummary,
                    accountHintSummary = accountHintSummary,
                    suggestedAccountType = likelyType
                ),
                detectionConfidence = confidence,
                userConfirmed = false,
                ignored = false,
                messageCount = alerts.size,
                firstSeenEpochMs = firstSeen,
                lastSeenEpochMs = lastSeen,
                sampleMessage = sample,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        }.sortedWith(
            compareByDescending<FinancialSourceEntity> { it.detectionConfidence }
                .thenByDescending { it.messageCount }
                .thenByDescending { it.lastSeenEpochMs }
                .thenBy { it.sourceAddress }
        )
    }

    fun matchesSource(
        rawAlert: RawAlertEntity,
        source: FinancialSourceEntity
    ): Boolean {
        if (rawAlert.sourcePackage != "sms") return false

        val alertSenderKey = buildSenderSourceKey(
            extractSmsAddress(rawAlert)
        )

        return alertSenderKey == source.sourceKey
    }

    private fun extractSmsAddress(alert: RawAlertEntity): String {
        val title = alert.title.orEmpty()

        return title
            .removePrefix("SMS from ")
            .trim()
            .ifBlank { "UNKNOWN_SENDER" }
    }

    private fun buildSenderSourceKey(sourceAddress: String): String {
        return "sender:${sourceAddress.trim().lowercase(Locale.US)}"
    }

    private fun buildInstitutionSummary(institutions: List<String>): String? {
        return when {
            institutions.isEmpty() -> null
            institutions.size == 1 -> institutions.first()
            else -> "Multiple: ${institutions.joinToString(", ")}"
        }
    }

    private fun buildAccountHintSummary(accountHints: List<String>): String? {
        return when {
            accountHints.isEmpty() -> null
            else -> accountHints.joinToString(", ")
        }
    }

    private fun buildDisplayName(
        sourceAddress: String,
        institutionSummary: String?,
        accountHintSummary: String?,
        suggestedAccountType: String
    ): String {
        val base = institutionSummary ?: "Sender $sourceAddress"

        val typeLabel = suggestedAccountType
            .lowercase(Locale.US)
            .replace("_", " ")

        val hintPart = if (accountHintSummary.isNullOrBlank()) {
            ""
        } else {
            " • hints $accountHintSummary"
        }

        return "$base • $typeLabel$hintPart"
    }

    private fun calculateConfidence(
        institutions: List<String>,
        accountHints: List<String>,
        suggestedAccountType: String
    ): Double {
        return when {
            institutions.isNotEmpty() && accountHints.isNotEmpty() && suggestedAccountType !in setOf("UNKNOWN", "MIXED") -> 0.90
            institutions.isNotEmpty() && accountHints.isNotEmpty() -> 0.82
            institutions.isNotEmpty() && suggestedAccountType !in setOf("UNKNOWN", "MIXED") -> 0.76
            accountHints.isNotEmpty() && suggestedAccountType !in setOf("UNKNOWN", "MIXED") -> 0.72
            institutions.isNotEmpty() -> 0.64
            accountHints.isNotEmpty() -> 0.55
            else -> 0.40
        }
    }

    private fun inferGroupAccountType(messages: List<String>): String {
        val counts = messages
            .map { inferLikelyAccountType(it) }
            .groupingBy { it }
            .eachCount()

        val nonUnknownCounts = counts.filterKeys { it != "UNKNOWN" }

        if (nonUnknownCounts.isEmpty()) {
            return "UNKNOWN"
        }

        if (nonUnknownCounts.size == 1) {
            return nonUnknownCounts.keys.first()
        }

        val totalKnown = nonUnknownCounts.values.sum()
        val best = nonUnknownCounts.maxByOrNull { it.value }

        // If one type clearly dominates, use it.
        // Otherwise mark the sender as mixed.
        if (best != null && best.value.toDouble() / totalKnown.toDouble() >= 0.70) {
            return best.key
        }

        return "MIXED"
    }

    private fun inferLikelyAccountType(text: String): String {
        val lower = text.lowercase(Locale.US)

        return when {
            lower.contains("savings") -> "SAVINGS"

            lower.contains("credit card") ||
                    lower.contains("available credit") ||
                    lower.contains("statement balance") ||
                    lower.contains("minimum payment") ||
                    lower.contains("payment due") -> {
                "CREDIT_CARD"
            }

            lower.contains("debit card") -> "DEBIT_CARD"

            lower.contains("checking") ||
                    lower.contains("available balance") ||
                    lower.contains("direct deposit") ||
                    lower.contains("atm withdrawal") ||
                    lower.contains("withdrawal") -> {
                "CHECKING"
            }

            else -> "UNKNOWN"
        }
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
            lower.contains("td bank") -> "TD Bank"
            lower.contains("pnc") -> "PNC"
            else -> null
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
}