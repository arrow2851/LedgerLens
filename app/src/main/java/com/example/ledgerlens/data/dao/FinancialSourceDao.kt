package com.example.ledgerlens.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialSourceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: FinancialSourceEntity)

    @Query("""
        SELECT *
        FROM financial_sources
        ORDER BY
            ignored ASC,
            userConfirmed ASC,
            detectionConfidence DESC,
            messageCount DESC,
            lastSeenEpochMs DESC,
            institutionName ASC,
            sourceAddress ASC,
            accountHint ASC
    """)
    fun observeAll(): Flow<List<FinancialSourceEntity>>

    @Query("SELECT COUNT(*) FROM financial_sources")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM financial_sources WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getBySourceKey(sourceKey: String): FinancialSourceEntity?

    @Query("""
        UPDATE financial_sources
        SET
            confirmedAccountType = :accountType,
            userConfirmed = 1,
            ignored = 0,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE sourceKey = :sourceKey
    """)
    suspend fun confirmAccountType(
        sourceKey: String,
        accountType: String,
        updatedAtEpochMs: Long
    )

    @Query("""
        UPDATE financial_sources
        SET
            ignored = 1,
            userConfirmed = 1,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE sourceKey = :sourceKey
    """)
    suspend fun ignoreSource(
        sourceKey: String,
        updatedAtEpochMs: Long
    )

    @Query("""
        UPDATE financial_sources
        SET
            ignored = 0,
            userConfirmed = 0,
            confirmedAccountType = NULL,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE sourceKey = :sourceKey
    """)
    suspend fun resetSourceConfirmation(
        sourceKey: String,
        updatedAtEpochMs: Long
    )

    @Query("DELETE FROM financial_sources WHERE sourceKey NOT LIKE 'sender:%'")
    suspend fun deleteLegacyNonSenderSources()

    @Query("DELETE FROM financial_sources")
    suspend fun deleteAll()

    @Query("""
    SELECT *
    FROM financial_sources
    WHERE userConfirmed = 1
      AND ignored = 0
""")
    suspend fun getIdentifiedSourcesOnce(): List<FinancialSourceEntity>
}