package com.example.ledgerlens.domain.parser

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import java.util.Locale
import com.example.ledgerlens.data.entity.FinancialSourceEntity

object SmsTransactionParser {

    private val MONEY_AMOUNT_PATTERN = MoneyExtractor.MONEY_AMOUNT_PATTERN

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
            val direction: TransactionTreatments.Direction = TransactionTreatments.Direction.UNKNOWN,
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

    private data class GenericClassification(
        val treatment: String,
        val direction: TransactionTreatments.Direction,
        val shouldIgnore: Boolean = false,
        val ignoreReason: String? = null,
        val reviewStatus: String? = null
    )

    fun isNonTransactionAlert(rawAlert: RawAlertEntity): Boolean {
        return FinancialSmsClassifier.isLikelyNonTransactionFinancialAlert(rawAlert.combinedText)
    }

    fun ignoreReason(
        rawAlert: RawAlertEntity,
        source: FinancialSourceEntity? = null
    ): String? {
        val body = rawAlert.combinedText.trim()
        val profileOutcome = parseWithSourceProfile(body, source)
        if (profileOutcome is ProfileOutcome.Ignored) {
            return profileOutcome.diagnostic.ignoreReason
                ?: profileOutcome.diagnostic.treatmentReason
        }
        if (isIssuerPaymentConfirmation(body, source)) {
            return "Issuer-side credit card payment confirmation."
        }
        return if (FinancialSmsClassifier.isLikelyNonTransactionFinancialAlert(body)) {
            "Informational financial alert, not a transaction."
        } else {
            null
        }
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
                    direction = profileOutcome.direction,
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
        if (isNonTransactionAlert(rawAlert) && looksClearlyInformational(lower)) return null

        val amountCents = extractAmountCents(body) ?: return null

        val classification = inferGenericClassification(
            rawAlert = rawAlert,
            source = source,
            lower = lower
        )
        if (classification.shouldIgnore) {
            return null
        }
        val accountingTreatment = classification.treatment
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
            direction = classification.direction,
            merchant = merchant,
            merchantExtraction = merchantExtraction,
            accountHint = accountHint,
            institution = institution,
            categorySuggestion = categorySuggestion,
            reviewStatusOverride = classification.reviewStatus,
            diagnostic = ParserDiagnostic(
                profile = "Generic",
                patternId = "generic_fallback",
                treatmentReason = "Keyword-based fallback classification.",
                merchantSpan = merchantExtraction?.method
            )
        )
    }

    private fun extractAmountCents(text: String): Long? {
        return MoneyExtractor.parseAmountToMinorUnits(text)
    }

    private fun inferGenericClassification(
        rawAlert: RawAlertEntity,
        source: FinancialSourceEntity?,
        lower: String
    ): GenericClassification {
        if (isIssuerPaymentConfirmation(rawAlert.combinedText, source)) {
            return GenericClassification(
                treatment = TransactionTreatments.CREDIT_CARD_PAYMENT,
                direction = TransactionTreatments.Direction.UNKNOWN,
                shouldIgnore = true,
                ignoreReason = "Issuer-side credit card payment confirmation."
            )
        }

        val direction = inferDirection(lower)
        return when {
            lower.contains("zelle") ||
                lower.contains("venmo") ||
                lower.contains("cash app") -> {
                GenericClassification(
                    treatment = if (direction == TransactionTreatments.Direction.INCOMING) {
                        TransactionTreatments.INCOME
                    } else {
                        TransactionTreatments.PERSON_TO_PERSON
                    },
                    direction = direction,
                    reviewStatus = ReviewStatus.NEEDS_REVIEW
                )
            }

            lower.contains("credit card payment") ||
                lower.contains("payment to your credit card") ||
                lower.contains("paid") && lower.contains("credit card") ||
                lower.contains("payment was made") ||
                (lower.contains("payment received") && lower.contains("card")) -> {
                GenericClassification(
                    treatment = TransactionTreatments.CREDIT_CARD_PAYMENT,
                    direction = direction
                )
            }

            lower.contains("refund") ||
                lower.contains("credited back") -> {
                GenericClassification(
                    treatment = TransactionTreatments.REFUND,
                    direction = TransactionTreatments.Direction.INCOMING,
                    reviewStatus = ReviewStatus.NEEDS_REVIEW
                )
            }

            lower.contains("direct deposit") ||
                lower.contains("payroll") && lower.contains("deposit") ||
                lower.contains("deposit") ||
                lower.contains("credited to your account") ||
                lower.contains("was credited") -> {
                GenericClassification(
                    treatment = TransactionTreatments.INCOME,
                    direction = TransactionTreatments.Direction.INCOMING
                )
            }

            lower.contains("external transfer") ||
                lower.contains("bill pay") ||
                lower.contains("electronic payment") ||
                lower.contains("payment to ") ||
                lower.contains("ach payment") ||
                lower.contains("ach transfer") ||
                lower.contains("ach") -> {
                val confidentInternal = looksLikeInternalTransfer(lower)
                GenericClassification(
                    treatment = if (confidentInternal) {
                        TransactionTreatments.TRANSFER
                    } else {
                        TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER
                    },
                    direction = direction,
                    reviewStatus = if (confidentInternal) null else ReviewStatus.NEEDS_REVIEW
                )
            }

            lower.contains("transfer") -> {
                val confidentInternal = looksLikeInternalTransfer(lower)
                GenericClassification(
                    treatment = if (confidentInternal) {
                        TransactionTreatments.TRANSFER
                    } else {
                        TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER
                    },
                    direction = direction,
                    reviewStatus = if (confidentInternal) null else ReviewStatus.NEEDS_REVIEW
                )
            }

            lower.contains("spent") ||
                lower.contains("purchase") ||
                lower.contains("charged") ||
                lower.contains("chrge") ||
                lower.contains("charge") ||
                lower.contains("debit card purchase") ||
                lower.contains("debit card transaction") ||
                lower.contains("transaction") ||
                lower.contains("pos") ||
                lower.contains("withdrawal") ||
                lower.contains("withdrawn") ||
                lower.contains("atm") -> {
                GenericClassification(
                    treatment = TransactionTreatments.EXPENSE,
                    direction = if (direction == TransactionTreatments.Direction.UNKNOWN) {
                        TransactionTreatments.Direction.OUTGOING
                    } else {
                        direction
                    }
                )
            }

            else -> GenericClassification(
                treatment = TransactionTreatments.UNKNOWN,
                direction = direction,
                reviewStatus = ReviewStatus.NEEDS_REVIEW
            )
        }
    }

    private fun parseWithSourceProfile(
        text: String,
        source: FinancialSourceEntity?
    ): ProfileOutcome? {
        val lower = text.lowercase(Locale.US)
        val sender = source?.sourceAddress?.trim()?.lowercase(Locale.US).orEmpty()

        return when {
            sender == "227898" ||
                source?.institutionName.equals("Capital One", ignoreCase = true) -> parseCapitalOne(text)
            sender == "24273" ||
                source?.institutionName.equals("Chase", ignoreCase = true) -> parseChase(text)
            sender == "hdfcbk" ||
                source?.institutionName.equals("HDFC Bank", ignoreCase = true) -> parseHdfc(text)
            source == null && lower.contains("capital one") -> parseCapitalOne(text)
            source == null && (lower.contains("chase | zelle") || lower.contains("chase acct")) -> parseChase(text)
            source == null && (lower.contains("hdfc") || lower.contains("hdfc bank")) -> parseHdfc(text)
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
                    direction = TransactionTreatments.Direction.OUTGOING,
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
                return ProfileOutcome.Ignored(
                    ParserDiagnostic(
                        profile = "Capital One",
                        patternId = "capital_one_paid_card",
                        treatmentReason = "Issuer-side credit card payment confirmation.",
                        ignoreReason = "Capital One payment confirmations are ignored to avoid duplicate ledger entries."
                    )
                )
            }

        Regex("""(?i)\byour payment of\s+($MONEY_AMOUNT_PATTERN)\s+is scheduled\b""")
            .find(text)
            ?.let { match ->
                return ProfileOutcome.Ignored(
                    ParserDiagnostic(
                        profile = "Capital One",
                        patternId = "capital_one_scheduled_payment",
                        treatmentReason = "Issuer-side scheduled credit-card payment confirmation.",
                        ignoreReason = "Capital One scheduled payment confirmations are ignored to avoid duplicate ledger entries."
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
                    accountingTreatment = TransactionTreatments.INCOME,
                    direction = TransactionTreatments.Direction.INCOMING,
                    merchant = payee,
                    institution = "Chase",
                    reviewStatus = ReviewStatus.NEEDS_REVIEW,
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
                    direction = TransactionTreatments.Direction.OUTGOING,
                    merchant = payee,
                    institution = "Chase",
                    reviewStatus = ReviewStatus.NEEDS_REVIEW,
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
                    direction = TransactionTreatments.Direction.OUTGOING,
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
                    accountingTreatment = if (looksLikeInternalTransfer(payee.lowercase(Locale.US))) {
                        TransactionTreatments.TRANSFER
                    } else {
                        TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER
                    },
                    direction = TransactionTreatments.Direction.OUTGOING,
                    merchant = payee,
                    accountHint = extractAccountHint(text),
                    institution = "Chase",
                    reviewStatus = if (looksLikeInternalTransfer(payee.lowercase(Locale.US))) {
                        ReviewStatus.AUTO_PARSED
                    } else {
                        ReviewStatus.NEEDS_REVIEW
                    },
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
                    direction = TransactionTreatments.Direction.INCOMING,
                    merchant = "Direct Deposit",
                    accountHint = extractAccountHint(text),
                    institution = "Chase",
                    reviewStatus = ReviewStatus.AUTO_PARSED,
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
                    direction = TransactionTreatments.Direction.OUTGOING,
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
                    direction = TransactionTreatments.Direction.INCOMING,
                    merchant = "Money Received",
                    accountHint = extractAccountHint(text),
                    institution = "HDFC Bank",
                    reviewStatus = ReviewStatus.AUTO_PARSED,
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
                    accountingTreatment = TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER,
                    direction = TransactionTreatments.Direction.OUTGOING,
                    merchant = payee,
                    accountHint = extractAccountHint(text),
                    institution = "HDFC Bank",
                    reviewStatus = ReviewStatus.NEEDS_REVIEW,
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
                    direction = TransactionTreatments.Direction.OUTGOING,
                    merchant = merchant.ifBlank { "ATM Withdrawal" },
                    accountHint = extractAccountHint(text),
                    institution = "HDFC Bank",
                    reviewStatus = ReviewStatus.AUTO_PARSED,
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
        direction: TransactionTreatments.Direction,
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
        val excludedFromSpending = TransactionTreatments.defaultExcludedFromSpending(
            treatment = accountingTreatment,
            direction = direction
        )

        val reviewStatus = reviewStatusOverride ?: when {
            isZelle -> ReviewStatus.NEEDS_REVIEW
            accountingTreatment == TransactionTreatments.PERSON_TO_PERSON -> ReviewStatus.NEEDS_REVIEW
            accountingTreatment == TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER -> ReviewStatus.NEEDS_REVIEW
            accountingTreatment == TransactionTreatments.UNKNOWN -> ReviewStatus.NEEDS_REVIEW
            merchant.isNullOrBlank() && accountingTreatment == TransactionTreatments.EXPENSE -> ReviewStatus.NEEDS_REVIEW
            else -> ReviewStatus.AUTO_PARSED
        }

        val confidence = confidenceOverride ?: when {
            reviewStatus == ReviewStatus.NEEDS_REVIEW -> 0.55
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
            add("Direction: ${direction.name.lowercase(Locale.US)}.")
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
            occurredAtEpochMs = DateExtractor.extractTransactionDateEpochMs(
                text = rawAlert.combinedText,
                receivedAtEpochMs = rawAlert.postTimeEpochMs
            ),
            receivedAtEpochMs = rawAlert.capturedAtEpochMs,
            parseConfidence = confidence,
            reviewStatus = reviewStatus,
            excludedFromSpending = excludedFromSpending,
            parserNotes = notes,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
    }

    private fun inferDirection(lower: String): TransactionTreatments.Direction {
        return when {
            containsAny(
                lower,
                "sent you",
                "from payroll",
                "deposit",
                "received from",
                "received ",
                "credited",
                "refund from",
                "money received"
            ) -> TransactionTreatments.Direction.INCOMING
            containsAny(
                lower,
                "you sent",
                "sent ",
                "payment to",
                "paid ",
                "transfer to",
                "withdraw",
                "spent",
                "purchase",
                "charge",
                "debit",
                "bill pay"
            ) -> TransactionTreatments.Direction.OUTGOING
            else -> TransactionTreatments.Direction.UNKNOWN
        }
    }

    private fun looksLikeInternalTransfer(lower: String): Boolean {
        return containsAny(
            lower,
            "your savings",
            "your checking",
            "to savings",
            "to checking",
            "between accounts",
            "own account",
            "linked account",
            "brokerage",
            "money market"
        )
    }

    private fun looksClearlyInformational(lower: String): Boolean {
        return containsAny(
            lower,
            "available balance",
            "your available balance",
            "available credit",
            "statement balance",
            "minimum payment due",
            "payment due",
            "otp",
            "fraud",
            "declined",
            "unauthorized",
            "unauthorised"
        )
    }

    private fun isIssuerPaymentConfirmation(
        text: String,
        source: FinancialSourceEntity?
    ): Boolean {
        val lower = text.lowercase(Locale.US)
        val sourceLooksLikeCardIssuer =
            source?.confirmedAccountType.equals("CREDIT_CARD", ignoreCase = true) ||
                source?.suggestedAccountType.equals("CREDIT_CARD", ignoreCase = true) ||
                containsAny(lower, "credit card", "card account", "venture card", "visa card")

        if (!sourceLooksLikeCardIssuer) return false

        val confirmation = containsAny(
            lower,
            "payment was received",
            "payment received",
            "payment posted",
            "payment successful",
            "thank you for your payment",
            "your payment of",
            "you paid ",
            "payment is scheduled"
        ) || Regex("""(?i)credit card payment of\s+$MONEY_AMOUNT_PATTERN\s+was made""").containsMatchIn(text)

        val bankDebitStyleAlert = containsAny(
            lower,
            "debit card transaction",
            "checking acct",
            "checking account",
            "external transfer to",
            "bill pay to",
            "payment to "
        )

        return confirmation && !bankDebitStyleAlert
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
        return MoneyExtractor.inferCurrency(text)
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
                TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER,
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
            "payment to amount" to Regex("""(?i)\bpayment to\s+(.+?)\s+$MONEY_AMOUNT_PATTERN(?:\s+|[.,]|$)"""),
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
                "Gas & Transport"

            containsAny(haystack, "netflix", "spotify", "hulu", "disney", "youtube", "apple.com/bill", "google", "subscription") ->
                "Subscriptions"

            containsAny(haystack, "mcdonald", "starbucks", "chipotle", "restaurant", "cafe", "doordash", "uber eats", "grubhub") ->
                "Dining & Restaurants"

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
