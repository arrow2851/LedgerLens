package com.example.ledgerlens.domain.transactions

import androidx.room.withTransaction
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.TransactionEntity

data class TransactionEditDraft(
    val merchantName: String?,
    val categoryName: String?,
    val accountingTreatment: String,
    val excludedFromSpending: Boolean,
    val reviewStatus: String,
    val spendingMerchantName: String?
)

class TransactionCorrectionUseCase(
    private val database: AppDatabase
) {
    suspend fun updateTransactionFromUserEdit(
        transactionId: Long,
        draft: TransactionEditDraft
    ): TransactionEntity? {
        var updatedTransaction: TransactionEntity? = null
        database.withTransaction {
            val current = database.transactionDao().getById(transactionId) ?: return@withTransaction
            val merchant = draft.merchantName.cleanedOrNull()
            val category = draft.categoryName.cleanedOrNull()
            val spendingMerchant = draft.spendingMerchantName.cleanedOrNull()
            val treatment = draft.accountingTreatment.trim().ifBlank { current.accountingTreatment }
            val now = System.currentTimeMillis()

            val merchantChanged = merchant != current.displayMerchantName ||
                merchant != current.merchantRaw
            val categoryChanged = category != current.categoryName ||
                spendingMerchant != current.spendingMerchantName
            val treatmentChanged = treatment != current.accountingTreatment ||
                draft.excludedFromSpending != current.excludedFromSpending

            val updated = current.copy(
                merchantRaw = merchant,
                displayMerchantName = merchant,
                spendingMerchantName = spendingMerchant,
                categoryName = category,
                transactionType = treatment,
                accountingTreatment = treatment,
                excludedFromSpending = draft.excludedFromSpending,
                reviewStatus = draft.reviewStatus,
                merchantUserEdited = current.merchantUserEdited || merchantChanged,
                categoryUserEdited = current.categoryUserEdited || categoryChanged,
                treatmentUserEdited = current.treatmentUserEdited || treatmentChanged,
                updatedAtEpochMs = now
            )

            if (updated != current) {
                database.transactionDao().update(updated)
            }
            updatedTransaction = updated
        }
        return updatedTransaction
    }
}

private fun String?.cleanedOrNull(): String? {
    return this?.trim()?.ifBlank { null }
}
