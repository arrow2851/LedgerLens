package com.ledgerlens.transactionlab

import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {
    private fun parse(text:String, received:Boolean=true)=SmsParser.parse(text,"TEST",received)
    @Test fun c1Hold() {
        val b="Capital One: A chrge or hold for $23.45 on October 10, 2026 was placed on your Venture Credit Card (1234) at EXAMPLE STORE #19. Std carrier chrges apply"
        val x=parse(b)
        assertEquals("CAPITAL_ONE_AUTH_HOLD",x.rule)
        assertEquals(2345L,x.amountMinor)
        assertEquals("PENDING",x.lifecycle)
        assertEquals("EXAMPLE STORE #19",x.merchant)
        assertEquals("23.45",b.substring(x.amountStart,x.amountEnd))
        assertEquals("EXAMPLE STORE #19",b.substring(x.merchantStart,x.merchantEnd))
    }
    @Test fun c1Purchase() {
        val x=parse("Capital One: A purchase for $5.90 at EXAMPLE SHOP on Aug. 5, 2026 was charged to Your Venture Credit Card (9901). Std carrier charges apply.")
        assertEquals("CAPITAL_ONE_PURCHASE",x.rule)
        assertEquals("EXAMPLE SHOP",x.merchant)
        assertEquals(590L,x.amountMinor)
    }
    @Test fun chaseThreshold() {
        val x=parse("Chase acct 1234: Your $2,015.50 external transfer to BROKERAGE TEST on Sep 5, 2026 at 10:20 AM ET was more than the $25.00 in your Alerts settings.")
        assertEquals("TRANSFER",x.kind)
        assertEquals(201550L,x.amountMinor)
        assertEquals("BROKERAGE TEST",x.merchant)
    }
    @Test fun chaseDeposit() {
        val x=parse("Chase acct 9876: Your recent $3,000.00 Direct Deposit posted on Sep 15, 2026 at 11:01 AM ET was more than the $500.00 limit in your Alerts settings.")
        assertEquals("DEPOSIT",x.kind)
        assertEquals("IN",x.direction)
        assertEquals(300000L,x.amountMinor)
    }
    @Test fun chaseDebitCard() {
        val x=parse("Chase acct 1234: Your $75.21 debit card transaction with GROCERY TEST #11 on Nov 2, 2025 at 2:32 PM ET was more than the $25.00 in your Alerts settings.")
        assertEquals("PURCHASE",x.kind)
        assertEquals("GROCERY TEST #11",x.merchant)
    }
    @Test fun zelleIncoming() {
        val x=parse("Chase | Zelle(R): JOHN TEST sent you $1,234.00 & it's ready now. Reply STOP to cancel these texts.")
        assertEquals("ZELLE_INCOMING",x.rule)
        assertEquals(123400L,x.amountMinor)
        assertEquals("IN",x.direction)
    }
    @Test fun international() {
        val x=parse("Capital One Alert: International chrg $19.95 on March 6, 2025 to your Venture Credit Card (1234) at INTERNATIONAL TEST SHOP. Std carrier chrges apply")
        assertEquals("CAPITAL_ONE_INTERNATIONAL",x.rule)
        assertEquals("INTERNATIONAL TEST SHOP",x.merchant)
    }
    @Test fun wire() {
        val x=parse("Chase acct 1001 A $250.00 incoming wire transfer on May 15, 2025 at 9:00 AM ET is above the limit in your alert settings.")
        assertEquals("CHASE_WIRE_ALERT",x.rule)
        assertEquals("IN",x.direction)
    }
    @Test fun hdfcCard() {
        val x=parse("Alert!You've spent Rs.455.25 On HDFC Bank Debit Card xx1234 At COFFEE TEST On 2026-01-02:15:00:00 Avl bal: 20,000 Not you?Call 000")
        assertEquals("HDFC_DEBIT_CARD",x.rule)
        assertEquals(45525L,x.amountMinor)
        assertEquals("COFFEE TEST",x.merchant)
    }
    @Test fun hdfcTransfer() {
        val x=parse("ALERT! Deducted Rs.47000.00 From: HDFC Bank A/c XXXX1234 On:07/10/2026 Funds transferred via NetBanking")
        assertEquals("HDFC_NETBANKING_TRANSFER",x.rule)
        assertEquals(4700000L,x.amountMinor)
    }
    @Test fun sbi() {
        val x=parse("Thx for INB txn of Rs.732.69 frm A/c X0000 to SOME PAYEE Ref#ABC123")
        assertEquals("SBI_INB_TRANSFER",x.rule)
    }
    @Test fun ignored() {
        assertEquals("AUTHENTICATION",parse("OTP to approve transaction of Rs 900 from your A/c is 123456. Do not share this code.").rule)
        assertEquals("IGNORED",parse("Spent $15 at SHOP",false).outcome)
        assertEquals("IGNORED",parse("Congratulations! You get $10 off a purchase of $60 or more at Coffee Test on your next visit.").outcome)
    }
    @Test fun malformedAmount() {
        assertEquals("IGNORED",parse("Card charged $0.1234 at TEST").outcome)
    }
}
