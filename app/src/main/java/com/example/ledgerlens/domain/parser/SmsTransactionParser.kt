package com.example.ledgerlens.domain.parser

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import java.util.Locale
import kotlin.math.roundToLong
import com.example.ledgerlens.data.entity.FinancialSourceEntity

object SmsTransactionParser {

    private const val MONEY_AMOUNT_PATTERN =
        """(?:\$|usd\s*|rs\.?\s*|inr\s*)\s*(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?"""

    private data class ParserDiagnostic(
        val profile: String,
        val patternId: String,
        val treatmentReason: String,
        val merchantSpan: String? = null,
        val ignoreReason: String? = null
    )

    private sealed class ProfileOutcome {
        data class Parsed(
            val amountCents: Long,
            val currency: String = "USD",
            val accountingTreatment: String,
            val merchant: String?,
            val accountHint: String? = null,
            val institution: String,
            val categorySuggestion: String? = null,
            val reviewStatus: String? = null,
            val confidence: Double,
            val diagnostic: ParserDiagnostic
        ) : ProfileOutcome()

        data class Ignored(
            val diagnostic: ParserDiagnostic
        ) : ProfileOutcome()
    }

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

        when (val profileOutcome = parseWithSourceProfile(body, source)) {
            is ProfileOutcome.Ignored -> return null
            is ProfileOutcome.Parsed -> {
                return buildTransaction(
                    rawAlert = rawAlert,
                    source = source,
                    amountCents = profileOutcome.amountCents,
                    currency = profileOutcome.currency,
                    accountingTreatment = profileOutcome.accountingTreatment,
                    merchant = profileOutcome.merchant,
                    merchantExtraction = profileOutcome.merchant?.let {
                        MerchantExtraction(
                            merchant = it,
                            method = profileOutcome.diagnostic.patternId,
                            confidenceBonus = 0.04
                        )
                    },
                    accountHint = profileOutcome.accountHint
                        ?: extractAccountHint(body)
                        ?: source?.accountHint?.takeIf { !it.contains(",") },
                    institution = profileOutcome.institution,
                    categorySuggestion = profileOutcome.categorySuggestion
                        ?: suggestCategory(
                            merchant = profileOutcome.merchant,
                            lower = body.lowercase(Locale.US),
                            accountingTreatment = profileOutcome.accountingTreatment
                        ),
                    reviewStatusOverride = profileOutcome.reviewStatus,
                    confidenceOverride = profileOutcome.confidence,
                    diagnostic = profileOutcome.diagnostic
                )
            }

            null -> Unit
        }

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

        return buildTransaction(
            rawAlert = rawAlert,
            source = source,
            amountCents = amountCents,
            currency = inferCurrency(body),
            accountingTreatment = transactionType,
            merchant = merchant,
            merchantExtraction = merchantExtraction,
            accountHint = accountHint,
            institution = institution,
            categorySuggestion = categorySuggestion,
            diagnostic = ParserDiagnostic(
                profile = "Generic",
                patternId = "generic_fallback",
                treatmentReason = "Keyword-based fallback classification.",
                merchantSpan = merchantExtraction?.method
            )
        )
    }

    private fun extractAmountCents(text: String): Long? {
        val regex = Regex(
            pattern = """(?i)(?:\$|usd\s*|rs\.?\s*|inr\s*)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
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
                    lower.contains("paid") && lower.contains("credit card") ||
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

            lower.contains("external transfer") ||
                    lower.contains("transfer") ||
                    lower.contains("ach") -> {
                TransactionTreatments.TRANSFER
            }

            lower.contains("spent") ||
                    lower.contains("purchase") ||
                    lower.contains("charged") ||
                    lower.contains("chrge") ||
                    lower.contains("charge") ||
                    lower.contains("debit card purchase") ||
                    lower.contains("debit card transaction") ||
                    lower.contains("transaction") ||
                    lower.contains("pos") -> {
                TransactionTreatments.EXPENSE
            }

            lower.contains("withdrawal") ||
                    lower.contains("withdrawn") ||
                    lower.contains("atm") -> {
                TransactionTreatments.EXPENSE
            }

            else -> TransactionTreatments.UNKNOWN
        }
    }

    private fun looksLikeNonTransactionAlert(lower: String): Boolean {
        if (
            containsAny(
                lower,
                "otp",
                "one time password",
                "verification code",
                "security code",
                "transaction declined",
                "declined",
                "fraud alert",
                "did you attempt",
                "not done by you"
            )
        ) {
            return true
        }

        val hasTransactionSignal = listOf(
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
            "balance is",
            "bal is",
            "available bal"
        ).any { lower.contains(it) }
    }

    private fun parseWithSourceProfile(
        text: String,
        source: FinancialSourceEntity?
    ): ProfileOutcome? {
        val lower = text.lowercase(Locale.US)
        val sender = source?.sourceAddress?.trim().orEmpty()

        return when {
            sender == "227898" || lower.contains("capital one") -> parseCapitalOne(text)
            sender == "24273" || lower.contains("chase | zelle") || lower.contains("chase acct") -> parseChase(text)
            lower.contains("hdfc") || lower.contains("hdfc bank") -> parseHdfc(text)
            else -> null
        }
    }

    private fun parseCapitalOne(text: String): ProfileOutcome? {
        val lower = text.lowercase(Locale.US)

        if (
            Regex("""(?i)\b(?:bal|balance)\s+is\s+$MONEY_AMOUNT_PATTERN\b""").containsMatchIn(text) ||
            lower.contains("available credit")
        ) {
            return ProfileOutcome.Ignored(
                ParserDiagnostic(
                    profile = "Capital One",
                    patternId = "capital_one_balance_alert",
                    treatmentReason = "Informational balance alert.",
                    ignoreReason = "Balance/available credit alerts do not create transactions."
                )
            )
        }

        Regex("""(?i)\ba\s+(?:chrge|charge)\s+or\s+hold\s+for\s+($MONEY_AMOUNT_PATTERN)\b.*?\bat\s+(.+?)(?:\.\s*Std carrier|Std carrier|Msg\s*&\s*data|$)""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                val merchant = match.groupValues[2].cleanMerchantCandidate()
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    accountingTreatment = TransactionTreatments.EXPENSE,
                    merchant = merchant,
                    accountHint = extractAccountHint(text),
                    institution = "Capital One",
                    categorySuggestion = suggestCategory(
                        merchant = merchant,
                        lower = lower,
                        accountingTreatment = TransactionTreatments.EXPENSE
                    ),
                    confidence = 0.93,
                    diagnostic = ParserDiagnostic(
                        profile = "Capital One",
                        patternId = "capital_one_charge_or_hold",
                        treatmentReason = "Capital One charge-or-hold purchase alert.",
                        merchantSpan = "Merchant after 'at' and before carrier disclaimer."
                    )
                )
            }

        Regex("""(?i)\byou paid\s+($MONEY_AMOUNT_PATTERN)\s+to your\s+(.+?credit card).*?(?:\bon\b|\.|$)""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                val payee = match.groupValues[2].cleanMerchantCandidate()
                    .ifBlank { "Capital One Credit Card" }
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    accountingTreatment = TransactionTreatments.CREDIT_CARD_PAYMENT,
                    merchant = payee,
                    accountHint = extractAccountHint(text),
                    institution = "Capital One",
                    reviewStatus = "AUTO_PARSED",
                    confidence = 0.94,
                    diagnostic = ParserDiagnostic(
                        profile = "Capital One",
                        patternId = "capital_one_paid_card",
                        treatmentReason = "Payment to a Capital One credit card.",
                        merchantSpan = "Payee phrase after 'to your'."
                    )
                )
            }

        Regex("""(?i)\byour payment of\s+($MONEY_AMOUNT_PATTERN)\s+is scheduled\b""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    accountingTreatment = TransactionTreatments.CREDIT_CARD_PAYMENT,
                    merchant = "Capital One Credit Card",
                    accountHint = extractAccountHint(text),
                    institution = "Capital One",
                    reviewStatus = "AUTO_PARSED",
                    confidence = 0.92,
                    diagnostic = ParserDiagnostic(
                        profile = "Capital One",
                        patternId = "capital_one_scheduled_payment",
                        treatmentReason = "Scheduled credit-card payment confirmation.",
                        merchantSpan = "Fixed payee from Capital One scheduled payment alert."
                    )
                )
            }

        return null
    }

    private fun parseChase(text: String): ProfileOutcome? {
        val lower = text.lowercase(Locale.US)

        Regex("""(?i)^Chase\s*\|\s*Zelle\(R\):\s*-?\s*(.+?)\s+sent you\s+($MONEY_AMOUNT_PATTERN)\b""")
            .find(text)
            ?.let { match ->
                val payee = match.groupValues[1].cleanMerchantCandidate()
                val amount = extractAmountCents(match.groupValues[2]) ?: return null
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    accountingTreatment = TransactionTreatments.PERSON_TO_PERSON,
                    merchant = payee,
                    institution = "Chase",
                    reviewStatus = "NEEDS_REVIEW",
                    confidence = 0.84,
                    diagnostic = ParserDiagnostic(
                        profile = "Chase",
                        patternId = "chase_zelle_incoming",
                        treatmentReason = "Zelle person-to-person incoming payment.",
                        merchantSpan = "Full sender name before amount/status/signature tokens."
                    )
                )
            }

        Regex("""(?i)\byou sent\s+($MONEY_AMOUNT_PATTERN)\s+to\s+(.+?)(?:\s+(?:with|via)\s+Zelle|\.|$)""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                val payee = match.groupValues[2].cleanMerchantCandidate()
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    accountingTreatment = TransactionTreatments.PERSON_TO_PERSON,
                    merchant = payee,
                    institution = "Chase",
                    reviewStatus = "NEEDS_REVIEW",
                    confidence = 0.82,
                    diagnostic = ParserDiagnostic(
                        profile = "Chase",
                        patternId = "chase_zelle_outgoing",
                        treatmentReason = "Zelle person-to-person outgoing payment.",
                        merchantSpan = "Payee after 'to' and before Zelle token."
                    )
                )
            }

        Regex("""(?i)\byour\s+($MONEY_AMOUNT_PATTERN)\s+debit card transaction with\s+(.+?)\s+on\s+""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                val merchant = match.groupValues[2].cleanMerchantCandidate()
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    accountingTreatment = TransactionTreatments.EXPENSE,
                    merchant = merchant,
                    accountHint = extractAccountHint(text),
                    institution = "Chase",
                    categorySuggestion = suggestCategory(
                        merchant = merchant,
                        lower = lower,
                        accountingTreatment = TransactionTreatments.EXPENSE
                    ),
                    confidence = 0.93,
                    diagnostic = ParserDiagnostic(
                        profile = "Chase",
                        patternId = "chase_debit_card_transaction",
                        treatmentReason = "Chase debit-card purchase alert.",
                        merchantSpan = "Merchant after 'transaction with' and before alert date."
                    )
                )
            }

        Regex("""(?i)\byour\s+($MONEY_AMOUNT_PATTERN)\s+external transfer to\s+(.+?)\s+on\s+""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                val payee = match.groupValues[2].cleanMerchantCandidate()
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    accountingTreatment = TransactionTreatments.TRANSFER,
                    merchant = payee,
                    accountHint = extractAccountHint(text),
                    institution = "Chase",
                    reviewStatus = "AUTO_PARSED",
                    confidence = 0.91,
                    diagnostic = ParserDiagnostic(
                        profile = "Chase",
                        patternId = "chase_external_transfer",
                        treatmentReason = "Chase external transfer alert.",
                        merchantSpan = "Payee after 'transfer to' and before alert date."
                    )
                )
            }

        Regex("""(?i)\byour recent\s+($MONEY_AMOUNT_PATTERN)\s+Direct Deposit\b""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    accountingTreatment = TransactionTreatments.INCOME,
                    merchant = "Direct Deposit",
                    accountHint = extractAccountHint(text),
                    institution = "Chase",
                    reviewStatus = "AUTO_PARSED",
                    confidence = 0.91,
                    diagnostic = ParserDiagnostic(
                        profile = "Chase",
                        patternId = "chase_direct_deposit",
                        treatmentReason = "Direct deposit into Chase account.",
                        merchantSpan = "Fixed payee from Direct Deposit alert."
                    )
                )
            }

        return null
    }

    private fun parseHdfc(text: String): ProfileOutcome? {
        val lower = text.lowercase(Locale.US)

        if (
            containsAny(
                lower,
                "otp",
                "declined",
                "fraud",
                "unauthorised",
                "unauthorized",
                "not done by you"
            )
        ) {
            return ProfileOutcome.Ignored(
                ParserDiagnostic(
                    profile = "HDFC",
                    patternId = "hdfc_security_or_declined",
                    treatmentReason = "Security, OTP, declined, or fraud-control alert.",
                    ignoreReason = "No completed money movement."
                )
            )
        }

        Regex("""(?i)\bspent\s+($MONEY_AMOUNT_PATTERN)\b.*?\bAt\s+(.+?)\s+On\s+""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                val merchant = match.groupValues[2].cleanMerchantCandidate()
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    currency = "INR",
                    accountingTreatment = TransactionTreatments.EXPENSE,
                    merchant = merchant,
                    accountHint = extractAccountHint(text),
                    institution = "HDFC Bank",
                    categorySuggestion = suggestCategory(
                        merchant = merchant,
                        lower = lower,
                        accountingTreatment = TransactionTreatments.EXPENSE
                    ),
                    confidence = 0.90,
                    diagnostic = ParserDiagnostic(
                        profile = "HDFC",
                        patternId = "hdfc_spent_at",
                        treatmentReason = "HDFC debit-card spend alert.",
                        merchantSpan = "Merchant after 'At' and before HDFC date token."
                    )
                )
            }

        Regex("""(?i)\bMoney Received\s*-\s*($MONEY_AMOUNT_PATTERN)\b""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    currency = "INR",
                    accountingTreatment = TransactionTreatments.INCOME,
                    merchant = "Money Received",
                    accountHint = extractAccountHint(text),
                    institution = "HDFC Bank",
                    reviewStatus = "AUTO_PARSED",
                    confidence = 0.89,
                    diagnostic = ParserDiagnostic(
                        profile = "HDFC",
                        patternId = "hdfc_money_received",
                        treatmentReason = "Money received into HDFC account.",
                        merchantSpan = "Fixed payee from HDFC money-received alert."
                    )
                )
            }

        Regex("""(?is)\bDeducted\s+($MONEY_AMOUNT_PATTERN)\b.*?\bFunds transferred via\s+(.+?)(?:\s|$)""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                val payee = match.groupValues[2].cleanMerchantCandidate()
                    .ifBlank { "NetBanking Transfer" }
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    currency = "INR",
                    accountingTreatment = TransactionTreatments.TRANSFER,
                    merchant = payee,
                    accountHint = extractAccountHint(text),
                    institution = "HDFC Bank",
                    reviewStatus = "AUTO_PARSED",
                    confidence = 0.86,
                    diagnostic = ParserDiagnostic(
                        profile = "HDFC",
                        patternId = "hdfc_netbanking_transfer",
                        treatmentReason = "Funds transferred from HDFC account.",
                        merchantSpan = "Transfer channel after 'Funds transferred via'."
                    )
                )
            }

        Regex("""(?i)($MONEY_AMOUNT_PATTERN)\s+withdrawn\b.*?\bat\s+(.+?)\s+on\s+""")
            .find(text)
            ?.let { match ->
                val amount = extractAmountCents(match.groupValues[1]) ?: return null
                val merchant = match.groupValues[2].cleanMerchantCandidate()
                return ProfileOutcome.Parsed(
                    amountCents = amount,
                    currency = "INR",
                    accountingTreatment = TransactionTreatments.EXPENSE,
                    merchant = merchant.ifBlank { "ATM Withdrawal" },
                    accountHint = extractAccountHint(text),
                    institution = "HDFC Bank",
                    reviewStatus = "AUTO_PARSED",
                    confidence = 0.88,
                    diagnostic = ParserDiagnostic(
                        profile = "HDFC",
                        patternId = "hdfc_atm_withdrawal",
                        treatmentReason = "ATM withdrawal alert treated as cash spending.",
                        merchantSpan = "ATM/location after 'at' and before HDFC date token."
                    )
                )
            }

        return null
    }

    private fun buildTransaction(
        rawAlert: RawAlertEntity,
        source: FinancialSourceEntity?,
        amountCents: Long,
        currency: String,
        accountingTreatment: String,
        merchant: String?,
        merchantExtraction: MerchantExtraction?,
        accountHint: String?,
        institution: String?,
        categorySuggestion: String?,
        reviewStatusOverride: String? = null,
        confidenceOverride: Double? = null,
        diagnostic: ParserDiagnostic
    ): TransactionEntity {
        val lower = rawAlert.combinedText.lowercase(Locale.US)
        val isZelle = lower.contains("zelle")
        val excludedFromSpending = TransactionTreatments.defaultExcludedFromSpending(accountingTreatment)

        val reviewStatus = reviewStatusOverride ?: when {
            isZelle -> "NEEDS_REVIEW"
            accountingTreatment == TransactionTreatments.PERSON_TO_PERSON -> "NEEDS_REVIEW"
            accountingTreatment == TransactionTreatments.UNKNOWN -> "NEEDS_REVIEW"
            merchant.isNullOrBlank() && accountingTreatment == TransactionTreatments.EXPENSE -> "NEEDS_REVIEW"
            else -> "AUTO_PARSED"
        }

        val confidence = confidenceOverride ?: when {
            reviewStatus == "NEEDS_REVIEW" -> 0.55
            merchant != null && categorySuggestion != null && accountHint != null -> 0.88 + (merchantExtraction?.confidenceBonus ?: 0.0)
            merchant != null && accountHint != null -> 0.84 + (merchantExtraction?.confidenceBonus ?: 0.0)
            merchant != null && categorySuggestion != null -> 0.80 + (merchantExtraction?.confidenceBonus ?: 0.0)
            merchant != null -> 0.74 + (merchantExtraction?.confidenceBonus ?: 0.0)
            else -> 0.65
        }.coerceAtMost(0.95)

        val notes = buildList {
            add("Profile: ${diagnostic.profile}.")
            add("Pattern: ${diagnostic.patternId}.")
            add("Treatment reason: ${diagnostic.treatmentReason}")
            diagnostic.merchantSpan?.let { add("Merchant span: $it") }
            add("Accounting treatment: $accountingTreatment.")
            if (merchantExtraction != null) {
                add("Merchant extracted by ${merchantExtraction.method}.")
            }
            if (categorySuggestion != null) {
                add("Suggested category: $categorySuggestion.")
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
            transactionType = accountingTreatment,
            accountingTreatment = accountingTreatment,
            amountCents = amountCents,
            currency = currency,
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = institution,
            accountHint = accountHint,
            categoryName = categorySuggestion,
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

    private fun extractAccountHint(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)(?:ending in|ending|ends in|card ending|account ending|acct ending)\s*([0-9]{4})"""),
            Regex("""(?i)(?:x{2,}|X{2,}|\*{2,})\s*([0-9]{4})"""),
            Regex("""\(([0-9]{4})\)"""),
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

    private fun inferCurrency(text: String): String {
        return if (Regex("""(?i)\b(?:rs\.?|inr)\b""").containsMatchIn(text)) {
            "INR"
        } else {
            "USD"
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
                TransactionTreatments.REIMBURSEMENT,
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
            "spent merchant" to Regex("""(?i)\bspent\s+(?:$MONEY_AMOUNT_PATTERN\s+)?at\s+(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)"""),
            "amount at merchant" to Regex("""(?i)$MONEY_AMOUNT_PATTERN\s+(?:at|from|with)\s+(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)"""),
            "person transfer" to Regex("""(?i)\b(?:sent|paid|transferred)\s+(?:$MONEY_AMOUNT_PATTERN\s+)?to\s+(.+?)(?:\s+(?:with|via)\s+(?:zelle|venmo|cash app)|\s+on\s+|[.,]|$)"""),
            "incoming from" to Regex("""(?i)\b(?:received|deposit|credited)\s+(?:$MONEY_AMOUNT_PATTERN\s+)?(?:from|by)\s+(.+?)(?:\s+(?:with|via)\s+(?:zelle|venmo|cash app)|\s+on\s+|[.,]|$)"""),
            "paid to" to Regex("""(?i)\bpaid\s+(?:$MONEY_AMOUNT_PATTERN\s+)?(?:to\s+)?(.+?)(?:\s+on\s+(?:card|account)|\s+(?:ending|using|for)\b|[.,]|$)"""),
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
    ): String? {
        if (accountingTreatment != TransactionTreatments.EXPENSE) return null

        val haystack = listOfNotNull(merchant?.lowercase(Locale.US), lower)
            .joinToString(" ")

        return when {
            containsAny(haystack, "walmart", "target", "costco", "sam's club", "sams club", "aldi", "kroger", "whole foods", "trader joe") ->
                "Groceries"

            containsAny(haystack, "shell", "exxon", "chevron", "bp ", "mobil", "speedway", "circle k", "gas", "fuel") ->
                "Gas"

            containsAny(haystack, "netflix", "spotify", "hulu", "disney", "youtube", "apple.com/bill", "google", "subscription") ->
                "Subscriptions"

            containsAny(haystack, "mcdonald", "starbucks", "chipotle", "restaurant", "cafe", "doordash", "uber eats", "grubhub") ->
                "Restaurants"

            containsAny(haystack, "walgreens", "cvs", "pharmacy", "clinic", "doctor", "hospital") ->
                "Healthcare"

            containsAny(haystack, "uber", "lyft", "airline", "hotel", "airbnb", "parking") ->
                "Travel"

            containsAny(haystack, "masjid", "mosque", "islamic", "donation", "charity", "zakat", "sadaqah") ->
                "Charity"

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
            .replace(Regex("""(?i)(?:\$|usd\s*|rs\.?\s*|inr\s*)?\d+(?:,\d{3})*(?:\.\d{1,2})?"""), " ")
            .replace(Regex("""(?:x+|\*+)\d{4}"""), " ")
            .split(Regex("""[^a-z0-9']+"""))
            .filter { it.isNotBlank() }
    }

    private fun String.cleanMerchantCandidate(): String {
        return trim()
            .trim('-', ':', '.', ',', ';')
            .replace(Regex("""(?i)\s+(?:with|via)\s+(?:zelle|venmo|cash app).*$"""), "")
            .replace(Regex("""(?i)\s+sent you\s+$MONEY_AMOUNT_PATTERN.*$"""), "")
            .replace(Regex("""(?i)\s+Std carrier.*$"""), "")
            .replace(Regex("""(?i)\s+Msg\s*&\s*data.*$"""), "")
            .replace(Regex("""(?i)\s+Reply STOP.*$"""), "")
            .replace(Regex("""(?i)\s+was more than.*$"""), "")
            .replace(Regex("""(?i)\s+Avl bal.*$"""), "")
            .replace(Regex("""(?i)\s+on\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{2,4}.*$"""), "")
            .replace(Regex("""(?i)\s+on\s+\d{4}-\d{2}-\d{2}.*$"""), "")
            .replace(Regex("""(?i)\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}.*$"""), "")
            .replace(Regex("""(?i)\s+at\s+\d{1,2}:\d{2}.*$"""), "")
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
