package com.example.ledgerlens.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialSemanticsTest {

    @Test
    fun `fixture ledger reconciles to eighty three dollars spending`() {
        val events = listOf(
            event("purchase", 10_000, TransactionTreatment.PURCHASE, MoneyDirection.DEBIT),
            event("fee", 300, TransactionTreatment.FEE, MoneyDirection.DEBIT),
            event("refund", 2_000, TransactionTreatment.REFUND, MoneyDirection.CREDIT),
            event("transfer", 50_000, TransactionTreatment.TRANSFER, MoneyDirection.DEBIT),
            event(
                "card-payment",
                30_000,
                TransactionTreatment.CREDIT_CARD_PAYMENT,
                MoneyDirection.DEBIT,
            ),
            event("salary", 200_000, TransactionTreatment.INCOME, MoneyDirection.CREDIT),
        )

        val total = SpendingPolicy.total(events)

        assertEquals(8_300L, total.amountMinor)
        assertEquals(CurrencyCode.USD, total.currency)
    }

    @Test
    fun `default policy separates treatment from spending effect`() {
        assertEquals(
            SpendingEffect.INCREASE,
            SpendingPolicy.defaultEffect(TransactionTreatment.PURCHASE),
        )
        assertEquals(
            SpendingEffect.INCREASE,
            SpendingPolicy.defaultEffect(TransactionTreatment.FEE),
        )
        assertEquals(
            SpendingEffect.DECREASE,
            SpendingPolicy.defaultEffect(TransactionTreatment.REFUND),
        )
        assertEquals(
            SpendingEffect.DECREASE,
            SpendingPolicy.defaultEffect(TransactionTreatment.REVERSAL),
        )
        assertEquals(
            SpendingEffect.DECREASE,
            SpendingPolicy.defaultEffect(TransactionTreatment.REIMBURSEMENT),
        )
        assertEquals(
            SpendingEffect.NONE,
            SpendingPolicy.defaultEffect(TransactionTreatment.TRANSFER),
        )
        assertEquals(
            SpendingEffect.NONE,
            SpendingPolicy.defaultEffect(TransactionTreatment.CREDIT_CARD_PAYMENT),
        )
        assertEquals(
            SpendingEffect.NONE,
            SpendingPolicy.defaultEffect(TransactionTreatment.INCOME),
        )
        assertEquals(
            SpendingEffect.NONE,
            SpendingPolicy.defaultEffect(TransactionTreatment.INFORMATIONAL),
        )
        assertEquals(
            SpendingEffect.UNRESOLVED,
            SpendingPolicy.defaultEffect(TransactionTreatment.CASH_WITHDRAWAL),
        )
        assertEquals(
            SpendingEffect.UNRESOLVED,
            SpendingPolicy.defaultEffect(TransactionTreatment.UNKNOWN),
        )
    }

    @Test
    fun `unresolved cash withdrawal contributes zero and requires review`() {
        val withdrawal = event(
            id = "atm",
            amountMinor = 6_000,
            treatment = TransactionTreatment.CASH_WITHDRAWAL,
            direction = MoneyDirection.DEBIT,
        )

        assertEquals(0L, withdrawal.spendingContributionMinor())
        assertTrue(
            withdrawal.reviewReasons().contains(
                ReviewReason.SPENDING_EFFECT_UNRESOLVED,
            ),
        )
    }

    @Test
    fun `user spending override resolves cash withdrawal`() {
        val withdrawal = event(
            id = "atm",
            amountMinor = 6_000,
            treatment = TransactionTreatment.CASH_WITHDRAWAL,
            direction = MoneyDirection.DEBIT,
            spendingEffectOverride = SpendingEffect.INCREASE,
        )

        assertEquals(6_000L, withdrawal.spendingContributionMinor())
        assertFalse(
            withdrawal.reviewReasons().contains(
                ReviewReason.SPENDING_EFFECT_UNRESOLVED,
            ),
        )
    }

    @Test
    fun `unknown treatment and direction expose independent review reasons`() {
        val event = event(
            id = "unknown",
            amountMinor = 1_500,
            treatment = TransactionTreatment.UNKNOWN,
            direction = MoneyDirection.UNKNOWN,
        )

        assertEquals(
            setOf(
                ReviewReason.TREATMENT_UNKNOWN,
                ReviewReason.DIRECTION_UNKNOWN,
                ReviewReason.SPENDING_EFFECT_UNRESOLVED,
            ),
            event.reviewReasons(),
        )
    }

    @Test
    fun `currency mismatch is rejected instead of silently combined`() {
        val usd = event(
            id = "usd",
            amountMinor = 1_000,
            treatment = TransactionTreatment.PURCHASE,
            direction = MoneyDirection.DEBIT,
        )
        val eur = event(
            id = "eur",
            amountMinor = 1_000,
            treatment = TransactionTreatment.PURCHASE,
            direction = MoneyDirection.DEBIT,
            currency = CurrencyCode.of("EUR"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            SpendingPolicy.total(listOf(usd, eur), CurrencyCode.USD)
        }
    }

    @Test
    fun `total uses exact arithmetic and rejects overflow`() {
        val maximum = event(
            id = "maximum",
            amountMinor = Long.MAX_VALUE,
            treatment = TransactionTreatment.PURCHASE,
            direction = MoneyDirection.DEBIT,
        )
        val one = event(
            id = "one",
            amountMinor = 1,
            treatment = TransactionTreatment.PURCHASE,
            direction = MoneyDirection.DEBIT,
        )

        assertThrows(ArithmeticException::class.java) {
            SpendingPolicy.total(listOf(maximum, one))
        }
    }

    @Test
    fun `money values validate magnitude and currency`() {
        assertThrows(IllegalArgumentException::class.java) {
            MinorAmount(-1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode("usd")
        }

        assertEquals(CurrencyCode.USD, CurrencyCode.of(" usd "))
    }

    private fun event(
        id: String,
        amountMinor: Long,
        treatment: TransactionTreatment,
        direction: MoneyDirection,
        spendingEffectOverride: SpendingEffect? = null,
        currency: CurrencyCode = CurrencyCode.USD,
    ): FinancialEvent = FinancialEvent(
        id = id,
        money = Money(
            amount = MinorAmount(amountMinor),
            currency = currency,
        ),
        treatment = treatment,
        direction = direction,
        spendingEffectOverride = spendingEffectOverride,
    )
}
