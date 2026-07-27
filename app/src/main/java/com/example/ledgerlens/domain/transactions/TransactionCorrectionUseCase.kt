package com.example.ledgerlens.domain.transactions

import androidx.room.withTransaction
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.TransactionTreatments
import com.example.ledgerlens.domain.automation.isPaymentInstrumentLabel
import com.example.ledgerlens.domain.automation.resolvedReviewStatusAfterUserDecision
import com.example.ledgerlens.domain.automation.transactionDirectionFromParserNotes

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
            val requestedMerchant = draft.merchantName.cleanedOrNull()
            val merchantRejectedAsCardLabel = isPaymentInstrumentLabel(requestedMerchant)
            val merchant = requestedMerchant.takeUnless { merchantRejectedAsCardLabel }
            val category = draft.categoryName.cleanedOrNull()
            val spendingMerchant = draft.spendingMerchantName.cleanedOrNull()
            val treatment = draft.accountingTreatment.trim().ifBlank { current.accountingTreatment }
            val now = System.currentTimeMillis()

            val merchantChanged = merchant != current.displayMerchantName ||
                merchant != current.merchantRaw
            val categoryChanged = category != current.categoryName ||
                spendingMerchant != current.spendingMerchantName
            val treatmentValueChanged = treatment != current.accountingTreatment
            val direction = transactionDirectionFromParserNotes(current.parserNotes)
            val excludedFromSpending = if (treatmentValueChanged) {
                TransactionTreatments.defaultExcludedFromSpending(
                    treatment = treatment,
                    direction = direction
                )
            } else {
                draft.excludedFromSpending
            }
            val treatmentChanged = treatmentValueChanged ||
                excludedFromSpending != current.excludedFromSpending

            val requestedReviewStatus = draft.reviewStatus.trim().ifBlank { current.reviewStatus }
            val reviewStatus = when {
                merchantRejectedAsCardLabel -> ReviewStatus.NEEDS_REVIEW
                requestedReviewStatus != ReviewStatus.NEEDS_REVIEW -> requestedReviewStatus
                else -> resolvedReviewStatusAfterUserDecision(
                    previousStatus = current.reviewStatus,
                    merchantChanged = merchantChanged,
                    categoryChanged = categoryChanged,
                    treatmentChanged = treatmentChanged
                )
            }

            val updated = current.copy(
                merchantRaw = merchant,
                displayMerchantName = merchant,
                spendingMerchantName = spendingMerchant,
                categoryName = category,
                transactionType = treatment,
                accountingTreatment = treatment,
                excludedFromSpending = excludedFromSpending,
                reviewStatus = reviewStatus,
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
