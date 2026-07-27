package com.example.ledgerlens

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.summary.categorySpendSummaries
import com.example.ledgerlens.domain.summary.expenseTransactionsForRange
import com.example.ledgerlens.domain.summary.merchantSummaries
import com.example.ledgerlens.domain.summary.spendingImpactByCurrency
import com.example.ledgerlens.ui.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryDomainTest {

    @Test
    fun spendingSummaryIncludesOnlyTransactionsWithSpendingImpact() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "Target",
                amountCents = 4500,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false
            ),
            transaction(
                id = 2,
                merchant = "Capital One",
                amountCents = 4500,
                treatment = TransactionTreatments.CREDIT_CARD_PAYMENT,
                excludedFromSpending = true
            ),
            transaction(
                id = 3,
                merchant = "Payroll",
                amountCents = 90000,
                treatment = TransactionTreatments.INCOME,
                excludedFromSpending = true
            ),
            transaction(
                id = 4,
                merchant = "Mom",
                amountCents = 2500,
                treatment = TransactionTreatments.REIMBURSEMENT,
                excludedFromSpending = true,
                categoryName = "Utilities"
            )
        )

        val expenses = expenseTransactionsForRange(
            transactions = transactions,
            startEpochMs = 1_000,
            endEpochMs = 3_000
        )

        assertEquals(1, expenses.size)
        assertTrue(expenses.any { it.displayMerchantName == "Target" })
    }

    @Test
    fun categorySpendSummariesGroupByCategoryOnly() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "Walmart",
                amountCents = 2500,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false,
                categoryName = "Groceries"
            ),
            transaction(
                id = 2,
                merchant = "Costco",
                amountCents = 7500,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false,
                categoryName = "Groceries"
            ),
            transaction(
                id = 3,
                merchant = "Mom",
                amountCents = 1500,
                treatment = TransactionTreatments.REIMBURSEMENT,
                excludedFromSpending = false,
                categoryName = "Groceries"
            )
        )

        val summaries = categorySpendSummaries(transactions)

        assertEquals(1, summaries.size)
        assertEquals("Groceries", summaries.single().categoryName)
        assertEquals(8500L, summaries.single().amountCents)
        assertEquals(3, summaries.single().transactionCount)
        assertEquals(10000L, summaries.single().grossExpenseCents)
        assertEquals(1500L, summaries.single().refundOffsetCents)
    }

    @Test
    fun categorySpendSummariesUseVirtualUncategorizedBucketForMissingCategories() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "Target",
                amountCents = 2500,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false,
                categoryName = null
            ),
            transaction(
                id = 2,
                merchant = "Walmart",
                amountCents = 1200,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false,
                categoryName = "Unassigned"
            ),
            transaction(
                id = 3,
                merchant = "Costco",
                amountCents = 800,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false,
                categoryName = "Uncategorized"
            )
        )

        val summary = categorySpendSummaries(transactions).single()

        assertEquals("Uncategorized", summary.categoryName)
        assertEquals(4500L, summary.amountCents)
        assertEquals(3, summary.transactionCount)
    }

    @Test
    fun categorySummariesDoNotCombineMixedCurrencies() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "Target",
                amountCents = 1200,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false,
                categoryName = "Groceries",
                currency = "USD"
            ),
            transaction(
                id = 2,
                merchant = "Zomato",
                amountCents = 86910,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false,
                categoryName = "Groceries",
                currency = "INR"
            )
        )

        val summaries = categorySpendSummaries(transactions)
        val totals = spendingImpactByCurrency(transactions)

        assertEquals(2, summaries.size)
        assertEquals(1200L, totals["USD"])
        assertEquals(86910L, totals["INR"])
    }

    @Test
    fun moneyFormattingSupportsUsdInrAndFallback() {
        assertEquals("${'$'}12.34", formatMoney(1234, "USD"))
        assertEquals("INR 869.10", formatMoney(86910, "INR"))
        assertEquals("EUR 12.34", formatMoney(1234, "EUR"))
    }

    @Test
    fun merchantSummariesIncludeAllAccountingTreatments() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "Zelle - Omar",
                amountCents = 2500,
                treatment = TransactionTreatments.PERSON_TO_PERSON,
                excludedFromSpending = false
            ),
            transaction(
                id = 2,
                merchant = "Payroll",
                amountCents = 100000,
                treatment = TransactionTreatments.INCOME,
                excludedFromSpending = true
            ),
            transaction(
                id = 3,
                merchant = "Target",
                amountCents = 4200,
                treatment = TransactionTreatments.EXPENSE,
                excludedFromSpending = false
            )
        )

        val summaries = merchantSummaries(transactions)

        assertTrue(summaries.any { it.merchantName == "Zelle - Omar" })
        assertTrue(summaries.any { it.merchantName == "Payroll" })
        assertTrue(summaries.any { it.merchantName == "Target" })
        assertEquals(0L, summaries.first { it.merchantName == "Payroll" }.spendingAmountCents)
        assertEquals(100000L, summaries.first { it.merchantName == "Payroll" }.totalAmountCents)
        assertEquals(2500L, summaries.first { it.merchantName == "Zelle - Omar" }.spendingAmountCents)
    }

    @Test
    fun merchantSummariesUseSpendingMerchantForSpendingAttribution() {
        val transactions = listOf(
            transaction(
                id = 1,
                merchant = "Ali",
                amountCents = 2200,
                treatment = TransactionTreatments.REIMBURSEMENT,
                excludedFromSpending = false,
                categoryName = "Utilities",
                spendingMerchantName = "T-Mobile"
            )
        )

        val summaries = merchantSummaries(transactions)

        assertTrue(summaries.any { it.merchantName == "T-Mobile" })
        assertEquals(-2200L, summaries.first { it.merchantName == "T-Mobile" }.spendingAmountCents)
        assertEquals(2200L, summaries.first { it.merchantName == "T-Mobile" }.totalAmountCents)
    }

    @Test
    fun spendingImpactCentsHandlesTransferLikeTreatmentsAndOffsets() {
        assertEquals(
            1200L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.EXPENSE, false, 1200)
        )
        assertEquals(
            -1200L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.REFUND, false, 1200)
        )
        assertEquals(
            -1200L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.REIMBURSEMENT, false, 1200)
        )
        assertEquals(
            1200L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.PERSON_TO_PERSON, false, 1200)
        )
        assertEquals(
            1200L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER, false, 1200)
        )
        assertEquals(
            0L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.INCOME, true, 1200)
        )
        assertEquals(
            0L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.TRANSFER, true, 1200)
        )
        assertEquals(
            0L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.CREDIT_CARD_PAYMENT, true, 1200)
        )
        assertEquals(
            0L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.UNKNOWN, true, 1200)
        )
    }

    @Test
    fun reimbursementDefaultsToNegativeSpendingOffset() {
        assertFalse(TransactionTreatments.defaultExcludedFromSpending(TransactionTreatments.REIMBURSEMENT))
        assertEquals(
            -3500L,
            TransactionTreatments.spendingImpactCents(TransactionTreatments.REIMBURSEMENT, false, 3500)
        )
    }

    private fun transaction(
        id: Long,
        merchant: String,
        amountCents: Long,
        treatment: String,
        excludedFromSpending: Boolean,
        categoryName: String? = null,
        spendingMerchantName: String? = null,
        currency: String = "USD"
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            rawAlertId = id,
            sourceKey = "sender:24273",
            transactionType = treatment,
            accountingTreatment = treatment,
            amountCents = amountCents,
            currency = currency,
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = "Test Bank",
            accountHint = "1234",
            occurredAtEpochMs = 1_500,
            receivedAtEpochMs = 1_500,
            parseConfidence = 0.90,
            reviewStatus = "AUTO_PARSED",
            excludedFromSpending = excludedFromSpending,
            parserNotes = null,
            createdAtEpochMs = 1_500,
            updatedAtEpochMs = 1_500,
            categoryName = categoryName,
            spendingMerchantName = spendingMerchantName
        )
    }
}
