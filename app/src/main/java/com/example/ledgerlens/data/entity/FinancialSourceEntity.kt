package com.example.ledgerlens.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "financial_sources",
    indices = [
        Index(value = ["userConfirmed", "ignored"])
    ]
)
data class FinancialSourceEntity(
    @PrimaryKey
    val sourceKey: String,

    val sourceAddress: String,
    val institutionName: String?,
    val accountHint: String?,

    val suggestedAccountType: String,
    val confirmedAccountType: String? = null,

    val displayName: String? = null,

    val detectionConfidence: Double,

    val userConfirmed: Boolean = false,
    val ignored: Boolean = false,

    val messageCount: Int,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,

    val sampleMessage: String?,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
