package com.example.ledgerlens

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.domain.export.buildParserDiagnosticsJsonl
import com.example.ledgerlens.domain.export.buildTransactionsCsv
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    @Test
    fun buildTransactionsCsvEscapesMerchantAndNotes() {
        val csv = buildTransactionsCsv(
            listOf(
                transaction(
                    merchant = "Cafe, \"North\"",
                    notes = "Parser note\nsecond line"
                )
            )
        )

        assertTrue(csv.contains("\"Cafe, \"\"North\"\"\""))
        assertTrue(csv.contains("\"Parser note\nsecond line\""))
        assertTrue(csv.lineSequence().first().contains("spendingMerchant"))
        assertTrue(csv.lineSequence().first().contains("spendingImpact"))
        assertFalse(csv.lineSequence().first().contains("subcategory"))
    }

    @Test
    fun buildTransactionsCsvIncludesNegativeReimbursementImpact() {
        val csv = buildTransactionsCsv(
            listOf(
                transaction(
                    merchant = "Ali",
                    notes = "Family plan reimbursement",
                    treatment = "REIMBURSEMENT",
                    amountCents = 3500,
                    spendingMerchant = "T-Mobile"
                )
            )
        )

        assertTrue(csv.contains("REIMBURSEMENT"))
        assertTrue(csv.contains("T-Mobile"))
        assertTrue(csv.contains("-35.00"))
    }

    @Test
    fun buildParserDiagnosticsJsonlIncludesRawSmsAndCorrections() {
        val rawAlert = RawAlertEntity(
            id = 1,
            notificationKey = "sms:1",
            sourcePackage = "sms",
            title = "SMS from 24273",
            text = "Chase Alert: You spent $12.34 at Cafe North.",
            bigText = null,
            subText = null,
            combinedText = "Chase Alert: You spent $12.34 at Cafe North.",
            postTimeEpochMs = 1_700_000_000_000,
            capturedAtEpochMs = 1_700_000_000_100,
            processingStatus = "PARSED_TRANSACTION"
        )

        val jsonl = buildParserDiagnosticsJsonl(
            rawAlerts = listOf(rawAlert),
            transactionsByRawAlertId = mapOf(1L to transaction("Cafe North", "Parser note")),
            includeRawSmsText = true
        )

        assertTrue(jsonl.contains("\"rawSmsText\":\"Chase Alert: You spent $12.34 at Cafe North.\""))
        assertTrue(jsonl.contains("\"accountingTreatment\":\"EXPENSE\""))
        assertTrue(jsonl.contains("\"spendingMerchantName\":\"Cafe North\""))
        assertTrue(jsonl.contains("\"spendingImpactCents\":1234"))
        assertTrue(jsonl.contains("\"merchantUserEdited\":false"))
        assertFalse(jsonl.contains("subcategoryName"))
    }

    @Test
    fun buildParserDiagnosticsJsonlRedactsRawSmsByDefault() {
        val rawAlert = RawAlertEntity(
            id = 1,
            notificationKey = "sms:1",
            sourcePackage = "sms",
            title = "SMS from 24273",
            text = "Chase Alert: You spent $12.34 at Cafe North.",
            bigText = null,
            subText = null,
            combinedText = "Chase Alert: You spent $12.34 at Cafe North.",
            postTimeEpochMs = 1_700_000_000_000,
            capturedAtEpochMs = 1_700_000_000_100,
            processingStatus = "PARSED_TRANSACTION"
        )

        val jsonl = buildParserDiagnosticsJsonl(
            rawAlerts = listOf(rawAlert),
            transactionsByRawAlertId = emptyMap()
        )

        assertTrue(jsonl.contains("\"rawSmsText\":\"[redacted]\""))
        assertFalse(jsonl.contains("Cafe North"))
    }

    @Test
    fun buildParserDiagnosticsJsonlIncludesNegativeReimbursementImpact() {
        val rawAlert = RawAlertEntity(
            id = 2,
            notificationKey = "sms:2",
            sourcePackage = "sms",
            title = "SMS from 24273",
            text = "Zelle payment received from Ali for $35.00.",
            bigText = null,
            subText = null,
            combinedText = "Zelle payment received from Ali for $35.00.",
            postTimeEpochMs = 1_700_000_000_000,
            capturedAtEpochMs = 1_700_000_000_100,
            processingStatus = "PARSED_TRANSACTION"
        )

        val jsonl = buildParserDiagnosticsJsonl(
            rawAlerts = listOf(rawAlert),
            transactionsByRawAlertId = mapOf(
                2L to transaction(
                    merchant = "Ali",
                    notes = "Reimbursement",
                    treatment = "REIMBURSEMENT",
                    amountCents = 3500,
                    spendingMerchant = "T-Mobile"
                ).copy(id = 2, rawAlertId = 2)
            )
        )

        assertTrue(jsonl.contains("\"accountingTreatment\":\"REIMBURSEMENT\""))
        assertTrue(jsonl.contains("\"spendingMerchantName\":\"T-Mobile\""))
        assertTrue(jsonl.contains("\"spendingImpactCents\":-3500"))
    }

    private fun transaction(
        merchant: String,
        notes: String,
        treatment: String = "EXPENSE",
        amountCents: Long = 1234,
        spendingMerchant: String? = merchant
    ): TransactionEntity {
        return TransactionEntity(
            id = 1,
            rawAlertId = 1,
            sourceKey = "sender:24273",
            transactionType = treatment,
            accountingTreatment = treatment,
            amountCents = amountCents,
            currency = "USD",
            merchantRaw = merchant,
            displayMerchantName = merchant,
            spendingMerchantName = spendingMerchant,
            sourceInstitution = "Chase",
            accountHint = "1234",
            occurredAtEpochMs = 1_700_000_000_000,
            receivedAtEpochMs = 1_700_000_000_100,
            parseConfidence = 0.88,
            reviewStatus = "AUTO_PARSED",
            excludedFromSpending = false,
            parserNotes = notes,
            createdAtEpochMs = 1_700_000_000_000,
            updatedAtEpochMs = 1_700_000_000_000,
            categoryName = "Dining & Restaurants"
        )
    }
}
