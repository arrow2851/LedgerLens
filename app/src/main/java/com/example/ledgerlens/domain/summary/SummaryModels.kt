package com.example.ledgerlens.domain.summary

import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CategorySpendSummary(
    val categoryName: String,
    val amountCents: Long,
    val transactionCount: Int,
    val grossExpenseCents: Long = 0,
    val refundOffsetCents: Long = 0
)

data class MerchantSummary(
    val merchantName: String,
    val transactionCount: Int,
    val totalAmountCents: Long,
    val spendingAmountCents: Long,
    val primaryTreatment: String,
    val categoryName: String?,
    val uncategorizedCount: Int,
    val latestTransactionEpochMs: Long
)

const val VIRTUAL_UNCATEGORIZED_CATEGORY = "Uncategorized"

fun isVirtualUncategorizedCategory(categoryName: String?): Boolean {
    val normalized = categoryName?.trim().orEmpty()
    return normalized.isBlank() ||
            normalized.equals(VIRTUAL_UNCATEGORIZED_CATEGORY, ignoreCase = true) ||
            normalized.equals("Unassigned", ignoreCase = true)
}

fun displayCategoryName(categoryName: String?): String {
    val trimmed = categoryName?.trim().orEmpty()
    return if (isVirtualUncategorizedCategory(trimmed)) {
        VIRTUAL_UNCATEGORIZED_CATEGORY
    } else {
        trimmed
    }
}

fun expenseTransactionsForRange(
    transactions: List<TransactionEntity>,
    startEpochMs: Long,
    endEpochMs: Long
): List<TransactionEntity> = spendingTransactionsForRange(
    transactions = transactions,
    startEpochMs = startEpochMs,
    endEpochMs = endEpochMs
)

fun spendingTransactionsForRange(
    transactions: List<TransactionEntity>,
    startEpochMs: Long,
    endEpochMs: Long
): List<TransactionEntity> {
    return transactions
        .filter {
            TransactionTreatments.isInSpendingView(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending
            )
        }
        .filter {
            it.occurredAtEpochMs >= startEpochMs &&
                    it.occurredAtEpochMs < endEpochMs
        }
}

fun categorySpendSummaries(transactions: List<TransactionEntity>): List<CategorySpendSummary> {
    return transactions
        .filter {
            TransactionTreatments.isInSpendingView(
                treatment = it.accountingTreatment,
                excludedFromSpending = it.excludedFromSpending
            )
        }
        .groupBy { displayCategoryName(it.categoryName) }
        .map { (key, group) ->
            val impacts = group.map {
                TransactionTreatments.spendingImpactCents(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending,
                    amountCents = it.amountCents
                )
            }
            CategorySpendSummary(
                categoryName = key,
                amountCents = impacts.sumOf { it },
                transactionCount = group.size,
                grossExpenseCents = impacts.filter { it > 0 }.sumOf { it },
                refundOffsetCents = impacts.filter { it < 0 }.sumOf { -it }
            )
        }
        .sortedByDescending { kotlin.math.abs(it.amountCents) }
}

fun merchantSummaries(transactions: List<TransactionEntity>): List<MerchantSummary> {
    return transactions
        .filter {
            !it.spendingMerchantName.isNullOrBlank() ||
                    !it.displayMerchantName.isNullOrBlank() ||
                    !it.merchantRaw.isNullOrBlank()
        }
        .groupBy {
            merchantSummaryName(it)
        }
        .map { (merchantName, group) ->
            val spendingGroup = group.filter {
                TransactionTreatments.isInSpendingView(
                    treatment = it.accountingTreatment,
                    excludedFromSpending = it.excludedFromSpending
                )
            }

            val primaryTreatment = group
                .groupingBy { it.accountingTreatment }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: TransactionTreatments.UNKNOWN

            val categories = group
                .mapNotNull { transaction ->
                    transaction.categoryName
                        ?.takeIf { !isVirtualUncategorizedCategory(it) }
                }

            val mostCommonCategory = categories
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

            MerchantSummary(
                merchantName = merchantName,
                transactionCount = group.size,
                totalAmountCents = group.sumOf { it.amountCents },
                spendingAmountCents = spendingGroup.sumOf {
                    TransactionTreatments.spendingImpactCents(
                        treatment = it.accountingTreatment,
                        excludedFromSpending = it.excludedFromSpending,
                        amountCents = it.amountCents
                    )
                },
                primaryTreatment = primaryTreatment,
                categoryName = mostCommonCategory,
                uncategorizedCount = group.count {
                    TransactionTreatments.isInSpendingView(
                        treatment = it.accountingTreatment,
                        excludedFromSpending = it.excludedFromSpending
                    ) &&
                            isVirtualUncategorizedCategory(it.categoryName)
                },
                latestTransactionEpochMs = group.maxOf { it.occurredAtEpochMs }
            )
        }
        .sortedWith(
            compareByDescending<MerchantSummary> { it.uncategorizedCount }
                .thenByDescending { kotlin.math.abs(it.spendingAmountCents) }
                .thenByDescending { it.totalAmountCents }
                .thenByDescending { it.transactionCount }
                .thenBy { it.merchantName.lowercase(Locale.US) }
        )
}

fun merchantSummaryName(transaction: TransactionEntity): String {
    val spendingAttribution = transaction.spendingMerchantName
        ?.takeIf { it.isNotBlank() }
    return if (
        spendingAttribution != null &&
        TransactionTreatments.isInSpendingView(
            treatment = transaction.accountingTreatment,
            excludedFromSpending = transaction.excludedFromSpending
        )
    ) {
        spendingAttribution.trim()
    } else {
        (transaction.displayMerchantName ?: transaction.merchantRaw ?: "Unknown merchant").trim()
    }
}

fun getCurrentMonthStartEpochMs(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun getNextMonthStartEpochMs(monthStartEpochMs: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = monthStartEpochMs
    calendar.add(Calendar.MONTH, 1)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun getPreviousMonthStartEpochMs(monthStartEpochMs: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = monthStartEpochMs
    calendar.add(Calendar.MONTH, -1)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun formatMonthYear(monthStartEpochMs: Long): String {
    val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return formatter.format(Date(monthStartEpochMs))
}

fun treatmentLabel(treatment: String): String {
    return when (treatment) {
        TransactionTreatments.EXPENSE -> "Expense"
        TransactionTreatments.INCOME -> "Income"
        TransactionTreatments.REFUND -> "Refund"
        TransactionTreatments.REIMBURSEMENT -> "Reimbursement"
        TransactionTreatments.CREDIT_CARD_PAYMENT -> "Credit card payment"
        TransactionTreatments.TRANSFER -> "Transfer"
        TransactionTreatments.PERSON_TO_PERSON -> "Person to person"
        else -> "Unknown"
    }
}

fun hasMissingMerchant(transaction: TransactionEntity): Boolean {
    return transaction.accountingTreatment in setOf(
        TransactionTreatments.EXPENSE,
        TransactionTreatments.REFUND,
        TransactionTreatments.REIMBURSEMENT,
        TransactionTreatments.PERSON_TO_PERSON,
        TransactionTreatments.CREDIT_CARD_PAYMENT,
        TransactionTreatments.INCOME
    ) &&
            transaction.displayMerchantName.isNullOrBlank() &&
            transaction.merchantRaw.isNullOrBlank()
}

fun hasMissingCategory(transaction: TransactionEntity): Boolean {
    return TransactionTreatments.isInSpendingView(
        treatment = transaction.accountingTreatment,
        excludedFromSpending = transaction.excludedFromSpending
    ) &&
            isVirtualUncategorizedCategory(transaction.categoryName)
}

fun hasLowConfidence(transaction: TransactionEntity): Boolean {
    return transaction.parseConfidence < 0.70
}

fun hasAnyReviewIssue(transaction: TransactionEntity): Boolean {
    return transaction.reviewStatus == "NEEDS_REVIEW" ||
            hasMissingMerchant(transaction) ||
            hasMissingCategory(transaction) ||
            hasLowConfidence(transaction)
}
