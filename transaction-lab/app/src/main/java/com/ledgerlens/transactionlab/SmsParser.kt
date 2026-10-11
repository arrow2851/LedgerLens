package com.ledgerlens.transactionlab

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern

/** Pure JVM deterministic parser. No Android/permissions/data dependencies. */
data class Parsed(
    val rule: String,
    val outcome: String = "IGNORED",
    val kind: String = "UNKNOWN",
    val direction: String = "UNKNOWN",
    val lifecycle: String = "NOT_APPLICABLE",
    val currency: String? = null,
    val amountMinor: Long? = null,
    val amountStart: Int = -1,
    val amountEnd: Int = -1,
    val merchant: String? = null,
    val merchantStart: Int = -1,
    val merchantEnd: Int = -1,
    val accountSuffix: String? = null,
    val note: String = ""
)

object SmsParser {
    private const val MONEY = """(?<amount>\d[\d,]*(?:\.\d{1,2})?)(?![\d.,])"""
    private const val MONTH = """(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"""
    private fun p(s: String) = Pattern.compile(s, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val auth = p("""\b(?:otp|one[ -]?time (?:password|passcode)|verification code|temporary code|temporary verification code|authentication code|authorization code|sign[ -]?in code|security code|passcode|your [\w ]{0,28}code is|is your code|code to (?:verify|sign in|log ?in)|do not share this code)\b""")
    private val promotional = p("""\b(?:cashback offer|offer valid|shop now|get \$\d+ off|you get \$\d+ off|qualify for|click here to|refer a friend|sign up|get upto|sale ends|promo code)\b""")
    private val scheduled = p("""\b(?:is scheduled|will be paid|payment due|payment reminder|minimum payment due|past due|your statement|balance is|available balance|credit limit)\b""")
    private val negative = p("""\b(?:declined|failed|unsuccessful|could not be processed|was not approved)\b""")
    private val currency = p("""(?:USD|INR|RS\.?|\$|₹)\s*\d[\d,]*(?:\.\d{1,2})?""")
    private val executed = p("""\b(?:debited|credited|spent|charged|paid|received\s+payment)\b""")
    private val triage = p("""\b(?:debited|credited|transaction|transfer|purchase|refund|withdrew|deposit|paid|spent)\b""")
    private val c1Hold = p("""\b(?:a\s+)?(?:chrge|charge)\s+or\s+hold\s+for\s*\$\s*""" + MONEY +
        """\s+on\s+.+?\s+was\s+placed\s+on\s+.+?credit\s+card\s*\((?<suffix>\d{2,6})\)\s+at\s+(?<party>.+?)(?=\.\s*(?:std\s+carrier|msg\s*&|message\s*&|text\s+STOP)|$)""")
    private val c1Purchase = p("""\ba\s+purchase\s+for\s*\$\s*""" + MONEY +
        """\s+at\s+(?<party>.+?)\s+on\s+""" + MONTH +
        """\.?\s+\d{1,2}[^.]{0,60}?\s+was\s+charged\s+to\s+.+?credit\s+card""")
    private val c1International = p("""\bCapital\s+One\s+Alert\s*:\s*International\s+chrg\s*\$\s*""" + MONEY +
        """\s+on\s+.+?\s+to\s+.+?Credit\s+Card\s*\((?<suffix>\d{2,6})\)\s+at\s+(?<party>.+?)(?=\.\s*(?:std\s+carrier|msg\s*&)|$)""")
    private val cardPayment = p("""\b(?:paid|payment\s+of|your\s+payment\s+of)\s*\$\s*""" + MONEY + """\b""")
    private val c1Info = p("""\b(?:autopay|scheduled|balance|statement|payment due|minimum due)\b""")
    private val chaseAccount = p("""\bChase\s+acct\s+(?<suffix>\d+)\s*:\s*Your\s+(?:recent\s+)?\$\s*""" + MONEY +
        """\s+(?<action>(?:(?:external|internal|wire|zelle)\s+)?transfer\s+to\s+.+?|debit\s+card\s+transaction\s+with\s+.+?|(?:direct\s+)?deposit(?:\s+posted)?|ATM\s+withdrawal)\s+on\s+""" +
        MONTH + """\.?\s+\d{1,2}[^\n]{0,90}?\s+was\s+(?:more|less)\s+than\s+the\b""")
    private val chaseWire = p("""\bChase\s+acct\s+(?<suffix>\d+)\s*:?\s*A\s*\$\s*""" + MONEY +
        """\s+(?<action>incoming\s+wire\s+transfer|wire\s+transfer\s+to\s+.+?)\s+on\s+""" +
        MONTH + """\.?\s+\d{1,2}[^\n]{0,90}?\s+(?:is|was)\s+above\s+the\s+limit""")
    private val chaseAtm = p("""\bChase\s+account\s+(?<suffix>\d+)\s*:?\s*ATM\s+deposit\s+of\s*\$\s*""" + MONEY + """\s+posted\s+to\s+your\s+account\s+on\s+""")
    private val zelleIn = p("""\b(?:Chase\s*\|\s*)?Zelle\s*\(R\)\s*:\s*(?<party>.+?)\s+sent\s+you\s*\$\s*""" +
        MONEY + """\s*(?:&|and)\s*it.?s\s+ready\s+now""")
    private val zelleOut = p("""\bZelle\s*\(R\)\s*:\s*You\s+sent\s+(?<party>.+?)\s*\$\s*""" + MONEY + """\b""")
    private val hdfcCard = p("""\b(?:you.ve\s+)?spent\s+Rs\.?\s*""" + MONEY +
        """\s+(?:on|from)\s+HDFC\s+Bank\s+(?:Debit\s+)?Card\b.{0,65}?\bAt\s+(?<party>.*?)\s+(?:On\s+(?:\d{2}[-/]\d{2}[-/]\d{4}|\d{4}[-/]\d{2}[-/]\d{2})|Purchase\s+On\s+(?:\d{2}[-/]\d{2}[-/]\d{4}|\d{4}[-/]\d{2}[-/]\d{2}))""")
    private val hdfcTransfer = p("""\bDeducted\s+Rs\.?\s*""" + MONEY + """\s+From\s*:\s*HDFC\s+Bank\s+A/c\b""")
    private val indiaReceive = p("""\breceived\s*[-:]?\s*INR\s*""" + MONEY + """\s+in\s+(?:your\s+)?\w{0,20}\s*Bank\s+A/c\b""")
    private val indiaCredit = p("""\byour\s+account\s+is\s+credited\s+INR\s*""" + MONEY + """\b""")
    private val reversal = p("""\b(?:ATM\s+TXN\s+REVERSED\s*:\s*Rs\.?\s*|Rs\.?\s*)""" + MONEY + """\s+reversed\s+to\b""")
    private val sbi = p("""\bThx\s+for\s+INB\s+txn\s+of\s+Rs\.?\s*""" + MONEY + """\s+frm\s+A/c\b""")
    private val generic = listOf(
        Rule("GENERIC_CARD_CHARGE", "PURCHASE", "OUT", "USD", p("""\b(?:card\s+)?charged\s*\$\s*""" + MONEY + """\b""")),
        Rule("GENERIC_SPENT_USD", "PURCHASE", "OUT", "USD", p("""\b(?:you\s+)?spent\s*\$\s*""" + MONEY + """\b""")),
        Rule("GENERIC_DEBITED_INR", "PURCHASE", "OUT", "INR", p("""\b(?:Rs\.?\s*|INR\s*)""" + MONEY + """\s+(?:has\s+been\s+)?debited\s+from\b""")),
        Rule("GENERIC_CREDITED_INR", "DEPOSIT", "IN", "INR", p("""\b(?:Rs\.?\s*|INR\s*)""" + MONEY + """\s+(?:has\s+been\s+)?credited\s+(?:to|in)\b""")),
        Rule("GENERIC_PAYMENT_USD", "PURCHASE", "OUT", "USD", p("""\b(?:thank\s+you\s+for\s+your|we\s+received\s+your)\s+payment\s+of\s*\$\s*""" + MONEY + """\b""")),
        Rule("GENERIC_REFUND_USD", "REFUND", "IN", "USD", p("""\brefund\s+of\s*\$\s*""" + MONEY + """\b"""))
    )
    private data class Rule(val id: String, val kind: String, val direction: String, val currency: String, val regex: Pattern)
    private fun Pattern.find(body: String) = matcher(body).let { if (it.find()) it else null }
    private fun java.util.regex.Matcher.opt(name: String): String? =
        try { group(name) } catch (_: IllegalArgumentException) { null }
    private fun java.util.regex.Matcher.bounds(name: String): Pair<Int,Int> =
        try { if (start(name) >= 0) start(name) to end(name) else (-1 to -1) }
        catch (_: IllegalArgumentException) { -1 to -1 }
    private fun minor(s: String?): Long? = try {
        val n = BigDecimal(s?.replace(",", "") ?: return null).setScale(2, RoundingMode.UNNECESSARY)
        if (n.signum() < 0 || n >= BigDecimal("1000000000000")) null else n.movePointRight(2).longValueExact()
    } catch (_: Exception) { null }
    private fun java.util.regex.Matcher.partySpan(name: String, party: String?): Pair<Int,Int> =
        if (party == null) -1 to -1 else bounds(name)
    private fun clean(s: String?): String? = s?.trim(' ', '\n', '\t', ':', '-')?.replace(Regex("""\s+"""), " ")?.take(180)?.ifBlank { null }
    private fun event(
        rule: String, kind: String, direction: String, life: String, code: String?,
        match: java.util.regex.Matcher, party: String? = null, partyBounds: Pair<Int,Int>? = null,
        note: String = "", review: Boolean = false, account: String? = null
    ): Parsed {
        val a = match.bounds("amount")
        val money = minor(match.opt("amount"))
        val b = partyBounds ?: match.partySpan("party", party)
        return Parsed(rule, if (review || money == null || code == null || direction == "UNKNOWN") "NEEDS_REVIEW" else "TRANSACTION_CANDIDATE",
            kind, direction, life, code, money, a.first, a.second, clean(party), b.first, b.second, account, note)
    }
    fun parse(body: String, sender: String = "", received: Boolean = true): Parsed {
        if (!received) return Parsed("NOT_RECEIVED", note = "Sent/draft message is not a bank transaction confirmation")
        if (body.isBlank() || body == "nullnull") return Parsed("EMPTY_MESSAGE")
        if (auth.find(body) != null) return Parsed("AUTHENTICATION", note = "Code alone does not prove a transaction")
        c1Hold.find(body)?.let { return event("CAPITAL_ONE_AUTH_HOLD","PURCHASE","OUT","PENDING","USD",it,it.opt("party"),
            note="Authorization hold is not a settled expense",account=it.opt("suffix")) }
        c1Purchase.find(body)?.let { return event("CAPITAL_ONE_PURCHASE","PURCHASE","OUT","CONFIRMED","USD",it,it.opt("party"),note="Card purchase alert") }
        c1International.find(body)?.let { return event("CAPITAL_ONE_INTERNATIONAL","PURCHASE","OUT","PENDING","USD",it,it.opt("party"),
            note="International charge alert, not proven settled",account=it.opt("suffix")) }
        if (body.contains("capital one",true) && body.contains("credit card",true)) {
            cardPayment.find(body)?.let {
                if (scheduled.find(body) == null) return event("CAPITAL_ONE_CARD_PAYMENT","CARD_PAYMENT","OUT","CONFIRMED","USD",it,note="Exclude credit card repayment from additional spending")
            }
            if (c1Info.find(body) != null) return Parsed("CAPITAL_ONE_INFORMATION",note="Account information or statement")
        }
        chaseAccount.find(body)?.let {
            val action = it.opt("action").orEmpty().trim()
            val kind = when {
                action.contains("debit card transaction",true) -> "PURCHASE"
                action.contains("deposit",true) -> "DEPOSIT"
                action.contains("withdrawal",true) -> "CASH_WITHDRAWAL"
                else -> "TRANSFER"
            }
            val direction = if (kind=="DEPOSIT") "IN" else "OUT"
            val party = when (kind) {
                "PURCHASE" -> action.replace(Regex("""(?is)^debit\s+card\s+transaction\s+with\s+"""), "")
                "TRANSFER" -> action.replace(Regex("""(?is)^.*?transfer\s+to\s+"""), "")
                else -> null
            }
            val start = if (party != null) it.start("action") + action.indexOf(party).coerceAtLeast(0) else -1
            return event("CHASE_ACCOUNT_ALERT",kind,direction,"CONFIRMED","USD",it,party,
                if (start>=0) start to start+party!!.length else -1 to -1,
                "First amount is transaction; later amount is alert threshold",account=it.opt("suffix"))
        }
        chaseWire.find(body)?.let {
            val action = it.opt("action").orEmpty()
            val outgoing = action.startsWith("wire transfer to",true)
            val party = if (outgoing) action.substring("wire transfer to".length).trim() else null
            val start = if (party!=null) it.start("action")+action.indexOf(party) else -1
            return event("CHASE_WIRE_ALERT","TRANSFER",if(outgoing)"OUT" else "IN","CONFIRMED","USD",it,party,
                if(start>=0) start to start+party!!.length else -1 to -1,"Wire transfer",account=it.opt("suffix"))
        }
        chaseAtm.find(body)?.let { return event("CHASE_ATM_DEPOSIT","DEPOSIT","IN","CONFIRMED","USD",it,account=it.opt("suffix")) }
        zelleIn.find(body)?.let { return event("ZELLE_INCOMING","TRANSFER","IN","CONFIRMED","USD",it,it.opt("party"),note="Incoming transfer is not automatically income") }
        zelleOut.find(body)?.let { return event("ZELLE_OUTGOING","TRANSFER","OUT","CONFIRMED","USD",it,it.opt("party"),note="Outgoing transfer is not automatically spending") }
        hdfcCard.find(body)?.let { return event("HDFC_DEBIT_CARD","PURCHASE","OUT","CONFIRMED","INR",it,it.opt("party"),note="Debit card purchase") }
        hdfcTransfer.find(body)?.let { return event("HDFC_NETBANKING_TRANSFER","TRANSFER","OUT","CONFIRMED","INR",it,note="Transfer, not automatically spending") }
        indiaReceive.find(body)?.let { return event("INDIA_BANK_RECEIVED","TRANSFER","IN","CONFIRMED","INR",it) }
        indiaCredit.find(body)?.let { return event("INDIA_ACCOUNT_CREDIT","DEPOSIT","IN","CONFIRMED","INR",it) }
        reversal.find(body)?.let { return event("ATM_REVERSAL","REVERSAL","IN","CONFIRMED","INR",it,note="Reconcile with originating transaction") }
        sbi.find(body)?.let { return event("SBI_INB_TRANSFER","TRANSFER","OUT","CONFIRMED","INR",it) }
        if (negative.find(body) != null) return if (currency.find(body) != null)
            Parsed("FAILED_OR_DECLINED",outcome="NEEDS_REVIEW",lifecycle="FAILED",note="Failed attempt is not spending")
            else Parsed("FAILED_NO_AMOUNT")
        if (promotional.find(body) != null) return Parsed("PROMOTIONAL")
        if (scheduled.find(body) != null && executed.find(body)==null) return Parsed("ACCOUNT_INFORMATION")
        for (r in generic) {
            val m=r.regex.find(body) ?: continue
            val kind=if(r.id=="GENERIC_PAYMENT_USD" && body.contains("credit card",true))"CARD_PAYMENT" else r.kind
            return event(r.id,kind,r.direction,"UNKNOWN",r.currency,m,review=true,note="Generic rule; manual review required")
        }
        if (triage.find(body) != null && currency.find(body)!=null) return Parsed("UNSUPPORTED_FINANCIAL_FORMAT",outcome="NEEDS_REVIEW",
            note="Money and financial action detected, but amount/type not safely extracted")
        return Parsed("NOT_TRANSACTION")
    }
}
