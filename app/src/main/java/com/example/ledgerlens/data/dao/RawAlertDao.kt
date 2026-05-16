package com.example.ledgerlens.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ledgerlens.data.entity.RawAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawAlertDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rawAlert: RawAlertEntity): Long

    @Query("SELECT COUNT(*) FROM raw_alerts WHERE notificationKey = :notificationKey")
    suspend fun countByNotificationKey(notificationKey: String): Int

    @Query("""
        SELECT *
        FROM raw_alerts
        ORDER BY postTimeEpochMs DESC
    """)
    fun observeAll(): Flow<List<RawAlertEntity>>

    @Query("""
        SELECT *
        FROM raw_alerts
        ORDER BY postTimeEpochMs DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int = 250): Flow<List<RawAlertEntity>>

    @Query("SELECT COUNT(*) FROM raw_alerts")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM raw_alerts")
    suspend fun deleteAll()

    @Query("""
    SELECT *
    FROM raw_alerts
    ORDER BY postTimeEpochMs DESC
""")
    suspend fun getAllOnce(): List<RawAlertEntity>

    @Query("""
    UPDATE raw_alerts
    SET processingStatus = :status
    WHERE id = :rawAlertId
""")
    suspend fun updateProcessingStatus(rawAlertId: Long, status: String)

    @Query("""
    UPDATE raw_alerts
    SET processingStatus = :status,
        ignoreReason = :ignoreReason
    WHERE id = :rawAlertId
""")
    suspend fun updateProcessingStatus(
        rawAlertId: Long,
        status: String,
        ignoreReason: String?
    )

    @Query("""
    UPDATE raw_alerts
    SET text = :placeholder,
        bigText = NULL,
        combinedText = :placeholder
    WHERE id = :rawAlertId
""")
    suspend fun redactStoredSmsText(rawAlertId: Long, placeholder: String)
}
