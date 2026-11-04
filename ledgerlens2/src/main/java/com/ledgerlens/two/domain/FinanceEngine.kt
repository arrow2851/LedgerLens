package com.ledgerlens.two.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class SmsInput(
    val smsId: Long,
    val sender: String,
    val body: String,
    val receivedAtMs: Long,
    val sentAtMs: Long? = null
)

object Movement {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val REFUND = "REFUND"
    const val REIMBURSEMENT = "REIMBURSEMENT"
    const val CARD_PAYMENT = "CARD_PAYMENT"
    const val TRANSFER = "TRANSFER"
    const val P2P_REVIEW = "P2P_REVIEW"
    const val HOLD = "HOLD"
    const val SCHEDULED = "SCHEDULED"
}

data class CategorySuggestion(val name: String, val confidence: Double, val reason: String)
data class ParsedTransaction(
    val amountMinor: Long,
    val currency: String,
    val kind: String,
    val merchant: String?,
    val category: String?,
    val confidence: Double,
    val explanation: String,
    val occurredAtMs: Long,
    val receivedAtMs: Long
)
data class Recurrence(val merchantKey: String, val months: Int, val medianMinor: Long)

/** Pure parsing and accounting functions, with no Android dependency. */
object FinanceEngine {
    private val money = Regex(
        """(?i)(?<![A-Za-z0-9])(?:US\s*\$|USD|INR|Rs\.?|₹|\$|SAR|AED)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )
    private val auth = Regex(
        """(?i)\b(otp|one[\s-]?time (?:password|code)|verification code|security code|sign[\s-]?in code|passcode|code to (?:approve|authorize)|authorization code)\b"""
    )
    private val advertising = Regex(
        """(?i)\b(discount|offer|cashback offer|save up to|shop now|apply now|pre-qualify|limited time|promotion|eligible for|special deal|refer a friend)\b"""
    )
    private val notPosted = Regex(
        """(?i)\b(declined|failed|unsuccessful|was rejected|could not be processed|pending approval|low balance|fraud alert|possible unauthori[sz]ed|was blocked|attempted)\b"""
    )
    private val posted = Regex(
        """(?i)\b(debited|deducted|credited|deposited|spent|charged|withdrawn|withdrawal of|purchase of|payment (?:of|received|processed|successful|made)|paid|transferred|transfer (?:of|completed|successful)|external transfer|debit card transaction|direct deposit posted|received (?:a )?(?:payment|transfer)|sent you|has been sent|txn (?:of|for)|transaction (?:of|for|at|with)|refunded|refund (?:of|processed))\b"""
    )
    private val holds = Regex("""(?i)\b(?:chrge or hold|charge or hold|authorization hold|preauthorization)\b""")
    private val scheduled = Regex("""(?i)\b(?:is scheduled|scheduled payment|will be paid|scheduled for)\b""")
    private val refunds = Regex("""(?i)\b(refunded|refund processed|refund of|reversal|reversed|chargeback|credited back)\b""")
    private val incoming = Regex("""(?i)\b(credited|deposited|direct deposit|received a payment|payment received|sent you|was received|has been received)\b""")
    private val cards = Regex(
        """(?i)\b(credit card payment|card payment received|payment to (?:your )?(?:credit )?card|payment (?:received|posted|made) (?:on|to|for) (?:your )?(?:credit )?card|autopay (?:payment|posted)|you paid.{0,70}to your.{0,40}credit card)\b"""
    )
    private val transfers = Regex(
        """(?i)\b(transfer(?:red)? (?:to|from|between|of)|external transfer|funds transferred|zelle|venmo|cash app|upi|imps|neft|rtgs|person to person|p2p)\b"""
    )
    private val outgoing = Regex(
        """(?i)\b(debited|deducted|spent|charged|withdrawn|purchase|debit card transaction|paid|payment of|transferred from|sent to)\b"""
    )
    private val merchantPattern = Regex(
        """(?i)\b(?:at|to|towards|with|merchant:|payee:)\s+([A-Za-z0-9][\p{L}\p{N} .&'*/#_-]{2,75})"""
    )
    private val badMerchant = Regex("""(?i)\b(?:a/c|account|card|xx\d+|x\d{3,}|ending|ref#|ref no|txn|balance|credit card)\b""")
    private val keywords = listOf(
        "Groceries" to listOf("h-e-b", "heb", "walmart", "wal-mart", "costco", "aldi", "kroger", "whole foods", "trader joe", "patel brothers", "grocery", "supermarket"),
        "Dining & Restaurants" to listOf("starbucks", "chipotle", "pizza", "restaurant", "cafe", "coffee", "doordash", "uber eats", "grubhub", "mcdonald"),
        "Gas & Transport" to listOf("shell", "chevron", "exxon", "gas station", "fuel", "quiktrip", "racetrac"),
        "Subscriptions" to listOf("netflix", "spotify", "youtube premium", "hulu", "disney+", "chatgpt", "openai", "prime video"),
        "Healthcare" to listOf("walgreens", "cvs pharmacy", "hospital", "clinic", "pharmacy", "urgent care"),
        "Utilities" to listOf("electricity", "electric bill", "gexa", "water utility", "internet bill", "spectrum internet"),
        "Charity" to listOf("masjid", "mosque", "zakat", "sadaqah", "charity donation"),
        "Travel" to listOf("airlines", "airways", "airbnb", "hotel", "expedia"),
        "Insurance" to listOf("insurance premium", "geico", "progressive insurance"),
        "Personal Care" to listOf("barber", "salon", "great clips")
    )
    val categories = listOf(
        "Groceries", "Dining & Restaurants", "Shopping", "Subscriptions", "Utilities",
        "Gas & Transport", "Healthcare", "Travel", "Entertainment", "Home",
        "Personal Care", "Charity", "Insurance", "Education", "Other"
    )

    /**
     * Financial-message presence is not equivalent to a posted transaction.
     * Return null unless there is a monetary amount and strong posted-event wording.
     * Sources are approved separately and never trusted solely by their message body.
     */
    fun parse(sms: SmsInput): ParsedTransaction? {
        val body = sms.body.trim()
        if (body.isBlank() || body == "nullnull" || body.length > 4000) return null
        val matched = money.find(body) ?: return null
        if (auth.containsMatchIn(body) || notPosted.containsMatchIn(body)) return null
        val pendingHold = holds.containsMatchIn(body)
        val scheduledPayment = scheduled.containsMatchIn(body)
        if (!posted.containsMatchIn(body) && !pendingHold && !scheduledPayment) return null
        if (advertising.containsMatchIn(body) &&
            !Regex("""(?i)\b(?:was|has been|successfully|completed|posted)\b""").containsMatchIn(body)
        ) return null

        val number = matched.groupValues[1].replace(",", "")
        val minor = runCatching {
            BigDecimal(number).multiply(BigDecimal(100))
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        }.getOrNull() ?: return null
        if (minor <= 0L || minor > 100_000_000_000L) return null

        val currency = when {
            Regex("""(?i)^(₹|Rs\.?|INR)""").containsMatchIn(matched.value) -> "INR"
            matched.value.startsWith("SAR", true) -> "SAR"
            matched.value.startsWith("AED", true) -> "AED"
            else -> "USD"
        }
        val treatment = when {
            pendingHold -> Movement.HOLD
            scheduledPayment -> Movement.SCHEDULED
            cards.containsMatchIn(body) -> Movement.CARD_PAYMENT
            refunds.containsMatchIn(body) -> Movement.REFUND
            transfers.containsMatchIn(body) -> {
                if (incoming.containsMatchIn(body) && !outgoing.containsMatchIn(body)) Movement.TRANSFER
                else if (Regex("""(?i)\bexternal transfer\b""").containsMatchIn(body)) Movement.TRANSFER
                else Movement.P2P_REVIEW
            }
            incoming.containsMatchIn(body) && !outgoing.containsMatchIn(body) -> Movement.INCOME
            outgoing.containsMatchIn(body) -> Movement.EXPENSE
            else -> Movement.P2P_REVIEW
        }
        val merchant = extractMerchant(body)
        val suggested = if (treatment == Movement.EXPENSE || treatment == Movement.REFUND)
            merchant?.let(::suggestCategory) else null
        val confidence = when {
            treatment == Movement.P2P_REVIEW -> .54
            treatment == Movement.HOLD || treatment == Movement.SCHEDULED -> .85
            merchant == null && treatment == Movement.EXPENSE -> .68
            suggested != null -> .90
            treatment == Movement.EXPENSE -> .78
            else -> .82
        }
        return ParsedTransaction(
            amountMinor = minor, currency = currency, kind = treatment, merchant = merchant,
            category = suggested?.name, confidence = confidence,
            explanation = "Posted financial event. " +
                (if (merchant == null) "Merchant unknown. " else "Merchant extracted. ") +
                "Android SMS timestamp retained.", occurredAtMs = sms.receivedAtMs,
            receivedAtMs = sms.receivedAtMs
        )
    }

    fun extractMerchant(body: String): String? {
        for (match in merchantPattern.findAll(body)) {
            val cleaned = match.groupValues[1]
                .split(Regex("""(?i)\s+(?:on|dated|ref(?:erence)?|available|via|for\s+card|with\s+card|bal)\b"""))[0]
                .trim(' ', '.', ',', ':', '-', '*').take(58)
            if (cleaned.length < 3 || badMerchant.containsMatchIn(cleaned)) continue
            if (Regex("""^\d[\d .-]*$""").matches(cleaned)) continue
            return cleaned
        }
        return null
    }

    fun suggestCategory(merchant: String): CategorySuggestion? {
        val source = merchant.lowercase(Locale.US)
        val hit = keywords.firstOrNull { (_, patterns) -> patterns.any(source::contains) }
            ?: return null
        return CategorySuggestion(hit.first, .83, "Merchant keyword")
    }

    fun canonicalMerchant(merchant: String): String {
        val clean = Normalizer.normalize(merchant.uppercase(Locale.US), Normalizer.Form.NFKD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""^(?:TST|SQ|SP|PAYPAL|PP|POS)\s*[*-]\s*"""), "")
            .replace(Regex("""[^A-Z0-9 ]"""), " ")
            .replace(Regex("""\b(?:STORE|LOCATION|NUMBER|NO)\s*#?\d+\b"""), " ")
            .replace(Regex("""\s+#?\d{3,}\b"""), "")
            .replace(Regex("""\s+"""), " ").trim()
        return if (clean.startsWith("WM SUPERCENTER") || clean.startsWith("WAL MART"))
            "WALMART" else clean
    }

    fun impact(kind: String, minor: Long, excluded: Boolean, sourceApproved: Boolean): Long {
        if (!sourceApproved || excluded) return 0L
        return when (kind) {
            Movement.EXPENSE -> minor
            Movement.REFUND, Movement.REIMBURSEMENT -> -minor
            else -> 0L
        }
    }

    fun duplicateCandidate(a: ParsedTransaction, b: ParsedTransaction, sameSource: Boolean): Boolean =
        sameSource && a.currency == b.currency && a.amountMinor == b.amountMinor &&
            a.kind == b.kind && abs(a.occurredAtMs - b.occurredAtMs) <= 300_000L &&
            (a.merchant == null || b.merchant == null ||
                canonicalMerchant(a.merchant) == canonicalMerchant(b.merchant))

    fun monthKey(epoch: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = epoch }
        return "%04d-%02d".format(Locale.US, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
    }

    fun formatMoney(minor: Long, currency: String, signed: Boolean = false): String {
        val prefix = if (!signed) "" else if (minor < 0) "−" else if (minor > 0) "+" else ""
        val amount = "%.2f".format(Locale.US, abs(minor) / 100.0)
        return when (currency) {
            "USD" -> prefix + "$" + amount
            "INR" -> prefix + "₹" + amount
            else -> prefix + currency + " " + amount
        }
    }

    fun recurring(transactions: List<Triple<String, Long, Long>>): List<Recurrence> =
        transactions.groupBy { canonicalMerchant(it.first) }.mapNotNull { (merchant, rows) ->
            val months = rows.map { monthKey(it.third) }.distinct()
            val amounts = rows.map { it.second }.sorted()
            val median = amounts.getOrNull(amounts.size / 2) ?: 0L
            if (merchant.isBlank() || months.size < 3 || median <= 0L) null
            else if (rows.count { abs(it.second - median) <= (median / 10).coerceAtLeast(100L) } < 3) null
            else Recurrence(merchant, months.size, median)
        }.sortedByDescending { it.months }
}
