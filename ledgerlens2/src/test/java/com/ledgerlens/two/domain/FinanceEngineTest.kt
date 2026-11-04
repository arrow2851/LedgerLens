package com.ledgerlens.two.domain

import org.junit.Assert.*
import org.junit.Test

class FinanceEngineTest {
    private fun sms(body: String, id: Long = 100L, at: Long = 1_735_700_000_000L) =
        SmsInput(id, "SIMULATED-BANK", body, at)

    @Test fun debitPostingIsExpenseAndPreservesMillisecondTime() {
        val parsed = FinanceEngine.parse(sms("Your account was debited USD 63.35 at H-E-B Grocery on November 1."))!!
        assertEquals(6335L, parsed.amountMinor)
        assertEquals("USD", parsed.currency)
        assertEquals(Movement.EXPENSE, parsed.kind)
        assertEquals(1_735_700_000_000L, parsed.occurredAtMs)
        assertEquals("Groceries", parsed.category)
    }

    @Test fun indianCurrencyParsingHasNoFloatingPointLoss() {
        val parsed = FinanceEngine.parse(sms("Deducted Rs.47000.00 from HDFC Bank A/c XXXX6272. Funds transferred via NetBanking."))!!
        assertEquals(4_700_000L, parsed.amountMinor)
        assertEquals("INR", parsed.currency)
        assertEquals(Movement.P2P_REVIEW, parsed.kind)
    }

    @Test fun authorizedTransferIsNotAutomaticallySpending() {
        assertEquals(0L, FinanceEngine.impact(Movement.P2P_REVIEW, 50_000L, false, true))
        assertEquals(0L, FinanceEngine.impact(Movement.TRANSFER, 50_000L, false, true))
        assertEquals(0L, FinanceEngine.impact(Movement.CARD_PAYMENT, 50_000L, false, true))
    }

    @Test fun unapprovedSourceCannotAffectSpending() {
        assertEquals(0L, FinanceEngine.impact(Movement.EXPENSE, 6_999L, false, false))
        assertEquals(6_999L, FinanceEngine.impact(Movement.EXPENSE, 6_999L, false, true))
    }

    @Test fun refundsAndReimbursementsOffsetSpending() {
        assertEquals(-1_250L, FinanceEngine.impact(Movement.REFUND, 1_250L, false, true))
        assertEquals(-1_250L, FinanceEngine.impact(Movement.REIMBURSEMENT, 1_250L, false, true))
    }

    @Test fun zelleIncomingIsOutsideSpending() {
        val parsed = FinanceEngine.parse(sms("Chase | Zelle(R): Alex sent you $2,500.00 and it's ready now."))!!
        assertEquals(250_000L, parsed.amountMinor)
        assertEquals(Movement.TRANSFER, parsed.kind)
        assertEquals(0L, FinanceEngine.impact(parsed.kind, parsed.amountMinor, false, true))
    }

    @Test fun otpContainingAmountIsNotACharge() {
        assertNull(FinanceEngine.parse(sms("OTP to approve transaction of Rs. 732.69 is 49258490. Do not share.")))
    }

    @Test fun promotionalPaymentIsNotACharge() {
        assertNull(FinanceEngine.parse(sms("Pay electricity bill today and get cashback offer of Rs500, shop now.")))
    }

    @Test fun accountSettingIsNotATransaction() {
        assertNull(FinanceEngine.parse(sms("You have added funds transfer beneficiary for HDFC account today.")))
    }

    @Test fun declinedChargeIsNotASpendingTransaction() {
        assertNull(FinanceEngine.parse(sms("Your $58.20 transaction at Coffee Shop was declined.")))
    }

    @Test fun exactDuplicateOnlyFlaggedWithinTimeWindow() {
        val a = FinanceEngine.parse(sms("Spent $12.00 at Walmart", 1L, 1_800_000_000_000L))!!
        val b = FinanceEngine.parse(sms("Spent $12.00 at Walmart", 2L, 1_800_000_120_000L))!!
        assertTrue(FinanceEngine.duplicateCandidate(a, b, true))
        assertFalse(FinanceEngine.duplicateCandidate(a, b, false))
    }

    @Test fun canonicalMerchantNormalizesCommonVariants() {
        assertEquals("WALMART", FinanceEngine.canonicalMerchant("WM SUPERCENTER #2995"))
        assertEquals("CHICHAS", FinanceEngine.canonicalMerchant("TST*CHICHAS"))
        assertNotEquals(FinanceEngine.canonicalMerchant("Plano Dental"),
            FinanceEngine.canonicalMerchant("Dental"))
    }

    @Test fun recurrenceRequiresThreeDistinctMonths() {
        val dates = listOf(
            Triple("Netflix", 1599L, 1_767_000_000_000L),
            Triple("Netflix", 1599L, 1_769_700_000_000L),
            Triple("Netflix", 1599L, 1_772_300_000_000L)
        )
        assertTrue(FinanceEngine.recurring(dates).isNotEmpty())
        assertTrue(FinanceEngine.recurring(dates.take(2)).isEmpty())
    }

    @Test fun allNonExpensesAreExcludedFromSpendingByDefault() {
        listOf(
            Movement.INCOME, Movement.TRANSFER, Movement.P2P_REVIEW, Movement.CARD_PAYMENT
        ).forEach {
            assertEquals(0L, FinanceEngine.impact(it, 4200L, false, true))
        }
    }
}
