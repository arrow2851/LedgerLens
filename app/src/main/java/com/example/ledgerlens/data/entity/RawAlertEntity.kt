package com.example.ledgerlens.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_alerts")
data class RawAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val notificationKey: String,
    val sourcePackage: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val combinedText: String,

    val postTimeEpochMs: Long,
    val capturedAtEpochMs: Long,

    val processingStatus: String = "NEW",
    val ignoreReason: String? = null
)