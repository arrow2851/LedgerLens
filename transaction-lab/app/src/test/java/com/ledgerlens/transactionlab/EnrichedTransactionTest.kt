package com.ledgerlens.transactionlab

import org.junit.Assert.*
import org.junit.Test

class EnrichedTransactionTest {
    @Test fun zelleIsRailNotMerchant() {
        val body="Chase | Zelle(R): TEST PERSON sent you $68.00 & it's ready now. Reply STOP to cancel these texts."
        val p=SmsParser.parse(body)
        val d=DetailsExtractor.extract(body,p)
        assertEquals("ZELLE",d.method)
        assertEquals("TEST PERSON",d.counterparty)
        assertEquals("SENDER",d.role)
        assertEquals("Chase",d.institution)
        assertEquals("Zelle received from TEST PERSON",d.title(p))
    }
    @Test fun repaymentIsPaymentToIssuer() {
        val body="Capital One Alert: You paid $1,400.00 to your Venture Credit Card…(1234) on October 4, 2026. Msg & data rates may apply."
        val p=SmsParser.parse(body)
        val d=DetailsExtractor.extract(body,p)
        assertEquals("CARD_PAYMENT",p.kind)
        assertEquals("CARD_REPAYMENT",d.method)
        assertEquals("Capital One",d.counterparty)
        assertEquals("1234",d.cardLast4)
    }
    @Test fun authorizedUserNameIsNotMerchant() {
        val body="Capital One: A purchase for $23.43 at TEST CAFE on Oct. 10, 2026 was charged to Taylor’s Venture Credit Card (1234). Std carrier charges apply."
        val p=SmsParser.parse(body)
        val d=DetailsExtractor.extract(body,p)
        assertEquals("TEST CAFE",p.merchant)
        assertEquals("Taylor",d.cardholder)
        assertEquals("1234",d.cardLast4)
        assertEquals("CREDIT_CARD",d.method)
    }
    @Test fun unnamedCardholderNotGuessed() {
        val body="Capital One: A chrge or hold for $108.24 on November 28, 2026 was placed on your Venture Credit Card (1234) at Example.com. Std carrier chrges apply"
        val d=DetailsExtractor.extract(body,SmsParser.parse(body))
        assertNull(d.cardholder)
        assertEquals("1234",d.cardLast4)
        assertNull(d.accountLast4)
    }
    @Test fun accountAlertThresholdNotAmount() {
        val body="Chase acct 1234: Your $21.60 debit card transaction with TEST MARKET on Sep 5, 2026 at 1:00 PM ET was more than the $1.00 in your Alerts settings."
        val p=SmsParser.parse(body)
        assertEquals(2160L,p.amountMinor)
        assertEquals("DEBIT_CARD",DetailsExtractor.extract(body,p).method)
    }
}
