package com.example.ledgerlens

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.rules.MerchantAliasRuleDraft
import com.example.ledgerlens.domain.rules.aliasMatchesText
import com.example.ledgerlens.domain.rules.applyMerchantAliasRuleToTransaction
import com.example.ledgerlens.domain.rules.previewMerchantAliasRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantAliasRulesTest {

    @Test
    fun guidedAliasMatchingIgnoresPunctuationAndSpacing() {
        assertTrue(aliasMatchesText("SAMSCLUB", "Purchase at SAMS Club #8839"))
        assertTrue(aliasMatchesText("SAMSCLUB", "SAMS Club 2202"))
        assertTrue(aliasMatchesText("Walmart", "WAL-MART 9292"))
        assertTrue(aliasMatchesText("Walmart", "WALMART Supercenter"))
    }

    @Test
    fun previewIsSourceScoped() {
        val draft = MerchantAliasRuleDraft(
            sourceKey = "sender:24273",
            canonicalMerchantName = "Sam's Club",
            aliases = listOf("SAMSCLUB")
        )
        val matching = transaction(
            id = 1,
            rawAlertId = 1,
            sourceKey = "sender:24273",
            merchant = "SAMSCLUB #8839"
        )
        val otherSource = transaction(
            id = 2,
            rawAlertId = 2,
            sourceKey = "sender:227898",
            merchant = "SAMS Club 2202"
        )
        val preview = previewMerchantAliasRule(
            draft = draft,
            transactions = listOf(matching, otherSource),
            rawAlertsById = mapOf(
                1L to rawAlert(1, "Purchase at SAMSCLUB #8839"),
                2L to rawAlert(2, "Purchase at SAMS Club 2202")
            )
        )

        assertEquals(1, preview.size)
        assertEquals(1, preview.single().transaction.id)
    }

    @Test
    fun applyingAliasRulePreservesManualOverrides() {
        val draft = MerchantAliasRuleDraft(
            sourceKey = "sender:24273",
            canonicalMerchantName = "Walmart",
            aliases = listOf("WAL-MART"),
            applyCategory = true,
            categoryName = "Groceries",
            applyTreatment = true,
            transactionType = TransactionTreatments.EXPENSE
        )
        val manuallyEdited = transaction(
            id = 1,
            rawAlertId = 1,
            merchant = "Manual Walmart",
            merchantUserEdited = true,
            categoryName = "Healthcare",
            categoryUserEdited = true,
            transactionType = TransactionTreatments.INCOME,
            treatmentUserEdited = true
        )
        val updated = applyMerchantAliasRuleToTransaction(
            transaction = manuallyEdited,
            draft = draft,
            now = 2_000
        )

        assertEquals("Manual Walmart", updated.displayMerchantName)
        assertEquals("Healthcare", updated.categoryName)
        assertEquals(TransactionTreatments.INCOME, updated.accountingTreatment)
    }

    @Test
    fun applyingAliasRuleUpdatesNonEditedTransaction() {
        val draft = MerchantAliasRuleDraft(
            sourceKey = "sender:24273",
            canonicalMerchantName = "Sam's Club",
            aliases = listOf("SAMSCLUB"),
            applyCategory = true,
            categoryName = "Groceries"
        )
        val transaction = transaction(
            id = 1,
            rawAlertId = 1,
            merchant = "SAMSCLUB #8839"
        )
        val updated = applyMerchantAliasRuleToTransaction(
            transaction = transaction,
            draft = draft,
            now = 2_000
        )

        assertEquals("Sam's Club", updated.displayMerchantName)
        assertEquals("Groceries", updated.categoryName)
        assertFalse(updated.merchantUserEdited)
    }

    private fun rawAlert(id: Long, body: String): RawAlertEntity {
        return RawAlertEntity(
            id = id,
            notificationKey = "sms:$id",
            sourcePackage = "sms",
            title = "SMS",
            text = body,
            bigText = null,
            subText = null,
            combinedText = body,
            postTimeEpochMs = 1_000,
            capturedAtEpochMs = 1_000
        )
    }

    private fun transaction(
        id: Long,
        rawAlertId: Long,
        sourceKey: String = "sender:24273",
        merchant: String,
        merchantUserEdited: Boolean = false,
        categoryName: String? = null,
        categoryUserEdited: Boolean = false,
        transactionType: String = TransactionTreatments.EXPENSE,
        treatmentUserEdited: Boolean = false
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            rawAlertId = rawAlertId,
            sourceKey = sourceKey,
            transactionType = transactionType,
            accountingTreatment = transactionType,
            amountCents = 1500,
            currency = "USD",
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = "Test Bank",
            accountHint = "1234",
            occurredAtEpochMs = 1_000,
            receivedAtEpochMs = 1_000,
            parseConfidence = 0.80,
            reviewStatus = "NEEDS_REVIEW",
            excludedFromSpending = false,
            merchantUserEdited = merchantUserEdited,
            categoryName = categoryName,
            categoryUserEdited = categoryUserEdited,
            treatmentUserEdited = treatmentUserEdited,
            createdAtEpochMs = 1_000,
            updatedAtEpochMs = 1_000
        )
    }
}
