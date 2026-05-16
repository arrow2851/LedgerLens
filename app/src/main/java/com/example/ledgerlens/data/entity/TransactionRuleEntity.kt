package com.example.ledgerlens.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_rules",
    indices = [
        Index(
            value = ["sourceKey", "normalizedMatchPhrase"],
            unique = true
        ),
        Index(
            value = ["active", "ruleKind"]
        )
    ]
)
data class TransactionRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sourceKey: String,

    val matchPhrase: String,
    val normalizedMatchPhrase: String,
    val ruleKind: String = "SOURCE_ALIAS",

    val merchantName: String? = null,
    val categoryName: String? = null,

    val transactionType: String? = null,
    val reviewStatus: String? = null,
    val excludedFromSpending: Boolean? = null,
    val appliesToTreatment: String? = null,
    val applyCategoryAutomatically: Boolean = true,
    val requiresReview: Boolean = false,

    val active: Boolean = true,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
