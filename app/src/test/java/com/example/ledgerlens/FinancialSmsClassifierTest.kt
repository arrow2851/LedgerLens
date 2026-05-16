package com.example.ledgerlens

import com.example.ledgerlens.domain.parser.FinancialSmsClassifier
import com.example.ledgerlens.domain.parser.MoneyExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialSmsClassifierTest {

    @Test
    fun acceptsParserSupportedUsdAndInrFormats() {
        val messages = listOf(
            "Chase Alert: You spent $12.34 at Starbucks on card ending 1234.",
            "Capital One: A chrge or hold for $108.24 was placed at Amazon.com.",
            "Alert!You've spent Rs.869.1 On HDFC Bank Debit Card xx4308 At ZOMATO1468673.",
            "Rs.20000 withdrawn from HDFC Bank Card x4308 at +MALLEPALLY OATM.",
            "Money Received - INR 20,000 in your HDFC Bank A/c XX6272."
        )

        messages.forEach { message ->
            assertTrue("Should look financial: $message", FinancialSmsClassifier.looksFinancial(message))
        }
    }

    @Test
    fun distinguishesFinancialInfoAlertsFromTransactions() {
        val balance = "Chase Alert: Your available balance is $1,234.56."

        assertTrue(FinancialSmsClassifier.looksFinancial(balance))
        assertTrue(FinancialSmsClassifier.isLikelyNonTransactionFinancialAlert(balance))
        assertFalse(FinancialSmsClassifier.looksTransactional(balance))
    }

    @Test
    fun rejectsOtpAndRandomNonFinancialMessages() {
        assertFalse(FinancialSmsClassifier.looksFinancial("Your verification code is 123456."))
        assertFalse(FinancialSmsClassifier.looksFinancial("Dinner is at 7, bring snacks."))
    }

    @Test
    fun extractsMinorUnitsAndCurrencyConsistently() {
        val candidates = MoneyExtractor.findAmounts("Spent Rs.869.1 and then USD 12.34")

        assertEquals(2, candidates.size)
        assertEquals(86910L, candidates[0].minorUnits)
        assertEquals("INR", candidates[0].currency)
        assertEquals(1234L, candidates[1].minorUnits)
        assertEquals("USD", candidates[1].currency)
    }
}
