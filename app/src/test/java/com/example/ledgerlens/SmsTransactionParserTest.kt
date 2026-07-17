package com.example.ledgerlens

import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.parser.SmsTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTransactionParserTest {

    @Test
    fun parsePurchaseExtractsMerchantAndCategory() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert("Chase Alert: You spent $12.34 at Starbucks on card ending 1234."),
            source = source()
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals(TransactionTreatments.EXPENSE, transaction.transactionType)
        assertEquals(TransactionTreatments.EXPENSE, transaction.accountingTreatment)
        assertEquals(1234L, transaction.amountCents)
        assertEquals("Starbucks", transaction.displayMerchantName)
        assertEquals("Dining & Restaurants", transaction.categoryName)
        assertEquals("1234", transaction.accountHint)
    }

    @Test
    fun parseBalanceAlertReturnsNull() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert("Chase Alert: Your available balance is $1,234.56."),
            source = source()
        )

        assertNull(transaction)
    }

    @Test
    fun parseZelleTransferNeedsReviewAndCountsTowardSpending() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert("Chase Alert: You sent $25.00 to Omar with Zelle."),
            source = source()
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals(TransactionTreatments.PERSON_TO_PERSON, transaction.transactionType)
        assertEquals(TransactionTreatments.PERSON_TO_PERSON, transaction.accountingTreatment)
        assertEquals("Omar", transaction.displayMerchantName)
        assertEquals("NEEDS_REVIEW", transaction.reviewStatus)
        assertEquals(false, transaction.excludedFromSpending)
        assertTrue(transaction.parserNotes.orEmpty().contains("Zelle"))
    }

    @Test
    fun parseCreditCardPaymentConfirmationIsIgnored() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert("Chase Alert: Your credit card payment of $250.00 was made to your card ending 1234."),
            source = source()
        )

        assertNull(transaction)
    }

    @Test
    fun parseUsesSourceTemplateOutlierWhenGenericPatternMisses() {
        val sourceMessages = listOf(
            "Bank Alert: Card 1234 purchase $12.10 merchant ALDI STORE 4421 approved.",
            "Bank Alert: Card 1234 purchase $9.40 merchant STARBUCKS 883 approved.",
            "Bank Alert: Card 1234 purchase $41.00 merchant TARGET T-203 approved."
        )

        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert(sourceMessages[1]),
            source = source(),
            sourceMessages = sourceMessages
        )

        assertNotNull(transaction)
        transaction!!
        assertTrue(transaction.parserNotes.orEmpty().contains("source template outlier"))
    }

    @Test
    fun capitalOnePurchaseUsesBoundedMerchantSpan() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Capital One: A chrge or hold for $108.24 on November 28, 2024 was placed on your Venture Credit Card (5944) at Amazon.com. Std carrier chrges apply"
            ),
            source = source(
                sourceAddress = "227898",
                institutionName = "Capital One",
                accountType = "CREDIT_CARD"
            )
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals(TransactionTreatments.EXPENSE, transaction.accountingTreatment)
        assertEquals(10824L, transaction.amountCents)
        assertEquals("Amazon.com", transaction.displayMerchantName)
        assertEquals("5944", transaction.accountHint)
        assertTrue(transaction.parserNotes.orEmpty().contains("Profile: Capital One"))
        assertTrue(transaction.parserNotes.orEmpty().contains("capital_one_charge_or_hold"))
    }

    @Test
    fun capitalOnePaymentAndScheduledPaymentAreIgnoredConfirmations() {
        val paid = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Capital One Alert: You paid $4,924.10 to your Venture Credit Card (5944) on December 16, 2024. Msg & data rates may apply."
            ),
            source = source(
                sourceAddress = "227898",
                institutionName = "Capital One",
                accountType = "CREDIT_CARD"
            )
        )

        val scheduled = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Capital One Alert: Your payment of $1706.69 is scheduled for Feb 26,2025. Contact us if your payment details are incorrect. Std carrier charges apply"
            ),
            source = source(
                sourceAddress = "227898",
                institutionName = "Capital One",
                accountType = "CREDIT_CARD"
            )
        )

        assertNull(paid)
        assertNull(scheduled)
    }

    @Test
    fun capitalOneBalanceAlertIsIgnored() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Capital One Alert: Your Venture Credit Card (5944) bal is $960.97 as of December 26, 2024. Msg & data rates may apply."
            ),
            source = source(
                sourceAddress = "227898",
                institutionName = "Capital One",
                accountType = "CREDIT_CARD"
            )
        )

        assertNull(transaction)
    }

    @Test
    fun chaseDebitCardCutsMerchantBeforeDateAndTime() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Chase acct 3039: Your $132.84 debit card transaction with GOOGLE *FI MF86D3 on Nov 17, 2023 at 6:06 AM ET was more than the $1.00 in your Alerts settings."
            ),
            source = source()
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals(TransactionTreatments.EXPENSE, transaction.accountingTreatment)
        assertEquals("GOOGLE *FI MF86D3", transaction.displayMerchantName)
        assertTrue(transaction.parserNotes.orEmpty().contains("chase_debit_card_transaction"))
    }

    @Test
    fun chaseTransferCutsPayeeBeforeDateAndTime() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Chase acct 3039: Your $1,039.07 external transfer to DISCOVER on Nov 28, 2023 at 4:39 AM ET was more than the $1.00 in your Alerts settings."
            ),
            source = source()
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals(TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER, transaction.accountingTreatment)
        assertEquals("DISCOVER", transaction.displayMerchantName)
        assertEquals(false, transaction.excludedFromSpending)
        assertEquals("NEEDS_REVIEW", transaction.reviewStatus)
    }

    @Test
    fun chaseDirectDepositIsIncome() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Chase acct 3039: Your recent $2,888.27 Direct Deposit posted on Nov 15, 2023 at 3:51 AM ET was more than the $1.00 limit in your Alerts settings."
            ),
            source = source()
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals(TransactionTreatments.INCOME, transaction.accountingTreatment)
        assertEquals("Direct Deposit", transaction.displayMerchantName)
        assertTrue(transaction.excludedFromSpending)
    }

    @Test
    fun chaseZelleKeepsFullPayeeNameWithoutSignatureTokens() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Chase | Zelle(R): ARSHAD QAVI sent you $2,500.00 & it's ready now. Reply STOP to cancel these texts."
            ),
            source = source()
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals(TransactionTreatments.INCOME, transaction.accountingTreatment)
        assertEquals("ARSHAD QAVI", transaction.displayMerchantName)
        assertEquals("NEEDS_REVIEW", transaction.reviewStatus)
        assertTrue(transaction.excludedFromSpending)
    }

    @Test
    fun hdfcInrSpendWithdrawAndIncomePatternsParse() {
        val hdfcSource = source(
            sourceAddress = "HDFCBK",
            institutionName = "HDFC Bank",
            accountType = "CHECKING"
        )

        val spend = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Alert!You've spent Rs.869.1 On HDFC Bank Debit Card xx4308 At ZOMATO1468673 On 2023-07-11:23:36:15 Avl bal..."
            ),
            source = hdfcSource
        )
        val withdrawal = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Rs.20000 withdrawn from HDFC Bank Card x4308 at +MALLEPALLY OATM on 2025-03-31."
            ),
            source = hdfcSource
        )
        val income = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Money Received - INR 8,190.97 in your HDFC Bank A/c XX6272 on 17-07-23 by A/c linked."
            ),
            source = hdfcSource
        )
        val transfer = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "ALERT!\nDeducted Rs.47000.00\nFrom: HDFC Bank A/c XXXX6272\nOn:07/10/2022\nFunds transferred via NetBanking\nNot you? Call 18002586161"
            ),
            source = hdfcSource
        )

        assertNotNull(spend)
        assertNotNull(withdrawal)
        assertNotNull(income)
        assertNotNull(transfer)
        assertEquals(TransactionTreatments.EXPENSE, spend!!.accountingTreatment)
        assertEquals("ZOMATO1468673", spend.displayMerchantName)
        assertEquals("INR", spend.currency)
        assertEquals(TransactionTreatments.EXPENSE, withdrawal!!.accountingTreatment)
        assertEquals("+MALLEPALLY OATM", withdrawal.displayMerchantName)
        assertEquals(TransactionTreatments.INCOME, income!!.accountingTreatment)
        assertEquals(819097L, income.amountCents)
        assertEquals(TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER, transfer!!.accountingTreatment)
        assertEquals("INR", transfer.currency)
    }

    @Test
    fun chaseMentioningCapitalOneKeepsChaseProfileAndTreatsCapitalOneAsPayee() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert(
                "Chase acct 1234: Payment to Capital One $500."
            ),
            source = source(
                sourceAddress = "24273",
                institutionName = "Chase",
                accountType = "CHECKING"
            )
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals("Chase", transaction.sourceInstitution)
        assertEquals("Capital One", transaction.displayMerchantName)
        assertEquals(TransactionTreatments.POSSIBLE_PAYMENT_TRANSFER, transaction.accountingTreatment)
        assertEquals(false, transaction.excludedFromSpending)
        assertEquals("NEEDS_REVIEW", transaction.reviewStatus)
    }

    @Test
    fun directRefundDefaultsOutsideSpending() {
        val transaction = SmsTransactionParser.parse(
            rawAlert = rawAlert("Refund from Target $23.19."),
            source = source()
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals(TransactionTreatments.REFUND, transaction.accountingTreatment)
        assertTrue(transaction.excludedFromSpending)
        assertEquals("NEEDS_REVIEW", transaction.reviewStatus)
    }

    @Test
    fun sanitizedSourceProfileFixtureExamplesParseAsExpected() {
        val fixture = javaClass.classLoader
            ?.getResourceAsStream("parser-fixtures/source-profiles.jsonl")
            ?.bufferedReader()
            ?.readLines()
            .orEmpty()
            .filter { it.isNotBlank() }

        assertTrue(fixture.isNotEmpty())

        fixture.forEach { line ->
            val sourceAddress = jsonString(line, "source")
            val rawSmsText = jsonString(line, "rawSmsText")
            val expectedTreatment = jsonString(line, "expectedTreatment")
            val expectedMerchant = jsonString(line, "expectedMerchant")
            val institution = when (jsonString(line, "profile")) {
                "Capital One" -> "Capital One"
                "HDFC" -> "HDFC Bank"
                else -> "Chase"
            }

            val transaction = SmsTransactionParser.parse(
                rawAlert = rawAlert(rawSmsText),
                source = source(
                    sourceAddress = sourceAddress,
                    institutionName = institution,
                    accountType = if (institution == "Capital One") "CREDIT_CARD" else "CHECKING"
                )
            )

            if (expectedTreatment == TransactionTreatments.CREDIT_CARD_PAYMENT &&
                institution == "Capital One"
            ) {
                assertNull("Capital One payment confirmations should be ignored: $line", transaction)
            } else if (
                expectedTreatment == TransactionTreatments.PERSON_TO_PERSON &&
                rawSmsText.contains("sent you", ignoreCase = true)
            ) {
                assertNotNull("Fixture should parse incoming P2P: $line", transaction)
                assertEquals(TransactionTreatments.INCOME, transaction!!.accountingTreatment)
                assertEquals(expectedMerchant, transaction.displayMerchantName)
            } else {
                assertNotNull("Fixture should parse: $line", transaction)
                assertEquals(expectedTreatment, transaction!!.accountingTreatment)
                assertEquals(expectedMerchant, transaction.displayMerchantName)
            }
        }
    }

    private fun jsonString(line: String, name: String): String {
        val match = Regex(""""$name":"([^"]*)"""")
            .find(line)
            ?: error("Missing JSON field $name in $line")

        return match.groupValues[1]
    }

    private fun rawAlert(body: String): RawAlertEntity {
        return RawAlertEntity(
            id = 1,
            notificationKey = "sms:1",
            sourcePackage = "sms",
            title = "SMS from 24273",
            text = body,
            bigText = null,
            subText = null,
            combinedText = body,
            postTimeEpochMs = 1_700_000_000_000,
            capturedAtEpochMs = 1_700_000_000_100,
            processingStatus = "IMPORTED_SMS"
        )
    }

    private fun source(
        sourceAddress: String = "24273",
        institutionName: String = "Chase",
        accountType: String = "CREDIT_CARD"
    ): FinancialSourceEntity {
        return FinancialSourceEntity(
            sourceKey = "sender:$sourceAddress",
            sourceAddress = sourceAddress,
            institutionName = institutionName,
            accountHint = null,
            suggestedAccountType = accountType,
            confirmedAccountType = accountType,
            displayName = institutionName,
            detectionConfidence = 0.9,
            userConfirmed = true,
            ignored = false,
            messageCount = 1,
            firstSeenEpochMs = 1_700_000_000_000,
            lastSeenEpochMs = 1_700_000_000_000,
            sampleMessage = null,
            createdAtEpochMs = 1_700_000_000_000,
            updatedAtEpochMs = 1_700_000_000_000
        )
    }
}
