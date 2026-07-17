package com.example.ledgerlens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Debug-only screenshot helper.
 *
 * This activity is never the launcher and is present only in debug builds. It replaces the
 * emulator's local transaction table with deterministic sample data, then opens the real
 * LedgerLensActivity. Production/release builds do not contain this class.
 */
class PreviewSeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.getInstance(applicationContext)
                database.transactionDao().deleteAll()
                previewTransactions().forEach { database.transactionDao().insert(it) }
            }

            startActivity(Intent(this@PreviewSeedActivity, LedgerLensActivity::class.java))
            finish()
        }
    }
}

private fun previewTransactions(): List<TransactionEntity> {
    val now = System.currentTimeMillis()

    fun timestamp(daysAgo: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun transaction(
        id: Long,
        daysAgo: Int,
        hour: Int,
        minute: Int,
        type: String,
        amountCents: Long,
        merchant: String,
        category: String?,
        account: String,
        reviewStatus: String = "REVIEWED",
        excluded: Boolean = false,
        confidence: Double = 0.96,
    ): TransactionEntity {
        val occurredAt = timestamp(daysAgo, hour, minute)
        return TransactionEntity(
            id = id,
            rawAlertId = id,
            sourceKey = "preview-source-${id % 3}",
            transactionType = type,
            amountCents = amountCents,
            currency = "USD",
            merchantRaw = merchant,
            displayMerchantName = merchant,
            sourceInstitution = when (id % 3) {
                0L -> "Chase"
                1L -> "Capital One"
                else -> "American Express"
            },
            accountHint = account,
            occurredAtEpochMs = occurredAt,
            receivedAtEpochMs = occurredAt,
            parseConfidence = confidence,
            reviewStatus = reviewStatus,
            excludedFromSpending = excluded,
            parserNotes = "Debug preview sample",
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            categoryName = category,
            subcategoryName = null,
        )
    }

    return listOf(
        transaction(1, 0, 18, 42, "PURCHASE", 8347, "Whole Foods Market", "Groceries", "Checking •• 4471"),
        transaction(2, 0, 14, 10, "PURCHASE", 2418, "Uber", "Transport", "Amex •• 1002", "NEEDS_REVIEW", confidence = 0.71),
        transaction(3, 0, 9, 0, "FEE", 1549, "Netflix", "Bills", "Checking •• 4471"),
        transaction(4, 1, 8, 0, "INCOME", 426000, "Northwind Payroll", null, "Checking •• 4471"),
        transaction(5, 1, 17, 31, "PURCHASE", 4732, "Shell", "Transport", "Amex •• 1002", "NEEDS_REVIEW", confidence = 0.69),
        transaction(6, 1, 10, 15, "TRANSFER", 80000, "Transfer to savings", null, "Checking → Savings", excluded = true),
        transaction(7, 2, 15, 22, "PURCHASE", 5428, "Target", "Shopping", "Capital One •• 9380"),
        transaction(8, 2, 8, 10, "PURCHASE", 675, "Starbucks", "Dining", "Amex •• 1002"),
        transaction(9, 3, 19, 4, "PURCHASE", 6421, "Kroger", "Groceries", "Checking •• 4471"),
        transaction(10, 3, 9, 0, "CREDIT_CARD_PAYMENT", 40000, "Card payment", null, "Checking → Amex", excluded = true),
        transaction(11, 4, 12, 0, "REFUND", 3861, "Amazon refund", "Shopping", "Capital One •• 9380"),
        transaction(12, 4, 20, 11, "PURCHASE", 1942, "Unassigned merchant", null, "Amex •• 1002", "NEEDS_REVIEW", confidence = 0.42),
        transaction(13, 5, 13, 24, "PURCHASE", 12620, "Costco", "Groceries", "Checking •• 4471"),
        transaction(14, 6, 19, 5, "PURCHASE", 6875, "Local Kitchen", "Dining", "Amex •• 1002"),
        transaction(15, 7, 7, 45, "PURCHASE", 3899, "Exxon", "Transport", "Capital One •• 9380"),
        transaction(16, 8, 16, 40, "PURCHASE", 12995, "Amazon", "Shopping", "Capital One •• 9380"),
        transaction(17, 9, 9, 0, "FEE", 9425, "Electric utility", "Bills", "Checking •• 4471"),
        transaction(18, 10, 11, 30, "PURCHASE", 8764, "Kroger", "Groceries", "Checking •• 4471"),
        transaction(19, 11, 18, 15, "PURCHASE", 5240, "DoorDash", "Dining", "Amex •• 1002"),
        transaction(20, 12, 14, 50, "PURCHASE", 3375, "CVS Pharmacy", "Other", "Capital One •• 9380"),
    )
}
