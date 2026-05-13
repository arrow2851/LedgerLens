package com.example.ledgerlens.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["rawAlertId"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val rawAlertId: Long,
    val sourceKey: String,

    val transactionType: String,
    val accountingTreatment: String = transactionType,
    val amountCents: Long,
    val currency: String = "USD",

    val merchantRaw: String?,
    val displayMerchantName: String?,
    val spendingMerchantName: String? = null,

    val sourceInstitution: String?,
    val accountHint: String?,

    val occurredAtEpochMs: Long,
    val receivedAtEpochMs: Long,

    val parseConfidence: Double,
    val reviewStatus: String,

    val excludedFromSpending: Boolean = false,

    val parserNotes: String? = null,

    val merchantUserEdited: Boolean = false,
    val categoryUserEdited: Boolean = false,
    val treatmentUserEdited: Boolean = false,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,

    val categoryName: String? = null,
)
