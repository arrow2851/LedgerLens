package com.example.ledgerlens.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ledgerlens.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("""
        SELECT *
        FROM transactions
        ORDER BY occurredAtEpochMs DESC
    """)
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE rawAlertId = :rawAlertId")
    suspend fun countByRawAlertId(rawAlertId: Long): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("""
    UPDATE transactions
    SET
        transactionType = :transactionType,
        excludedFromSpending = :excludedFromSpending,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :transactionId
""")
    suspend fun updateTransactionType(
        transactionId: Long,
        transactionType: String,
        excludedFromSpending: Boolean,
        updatedAtEpochMs: Long
    )

    @Query("""
    UPDATE transactions
    SET
        reviewStatus = :reviewStatus,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :transactionId
""")
    suspend fun updateReviewStatus(
        transactionId: Long,
        reviewStatus: String,
        updatedAtEpochMs: Long
    )

    @Query("""
    UPDATE transactions
    SET
        excludedFromSpending = :excludedFromSpending,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :transactionId
""")
    suspend fun updateExcludedFromSpending(
        transactionId: Long,
        excludedFromSpending: Boolean,
        updatedAtEpochMs: Long
    )

    @Query("""
    UPDATE transactions
    SET
        merchantRaw = :merchantRaw,
        displayMerchantName = :displayMerchantName,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :transactionId
""")
    suspend fun updateMerchant(
        transactionId: Long,
        merchantRaw: String?,
        displayMerchantName: String?,
        updatedAtEpochMs: Long
    )

    @Query("""
    SELECT COUNT(*)
    FROM transactions
    WHERE sourceKey = :sourceKey
      AND rawAlertId IN (
          SELECT id
          FROM raw_alerts
          WHERE combinedText LIKE :likePattern
      )
""")
    suspend fun countSimilarBySourceAndRawText(
        sourceKey: String,
        likePattern: String
    ): Int

    @Query("""
    UPDATE transactions
    SET
        merchantRaw = :merchantName,
        displayMerchantName = :merchantName,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE sourceKey = :sourceKey
      AND rawAlertId IN (
          SELECT id
          FROM raw_alerts
          WHERE combinedText LIKE :likePattern
      )
""")
    suspend fun updateMerchantForSimilarRawText(
        sourceKey: String,
        likePattern: String,
        merchantName: String?,
        updatedAtEpochMs: Long
    ): Int

    @Query("""
    UPDATE transactions
    SET
        transactionType = :transactionType,
        reviewStatus = :reviewStatus,
        excludedFromSpending = :excludedFromSpending,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE sourceKey = :sourceKey
      AND rawAlertId IN (
          SELECT id
          FROM raw_alerts
          WHERE combinedText LIKE :likePattern
      )
""")
    suspend fun updateClassificationForSimilarRawText(
        sourceKey: String,
        likePattern: String,
        transactionType: String,
        reviewStatus: String,
        excludedFromSpending: Boolean,
        updatedAtEpochMs: Long
    ): Int

    @Query("""
    UPDATE transactions
    SET
        categoryName = :categoryName,
        subcategoryName = :subcategoryName,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :transactionId
""")
    suspend fun updateCategory(
        transactionId: Long,
        categoryName: String?,
        subcategoryName: String?,
        updatedAtEpochMs: Long
    )

    @Query("""
    UPDATE transactions
    SET
        categoryName = :categoryName,
        subcategoryName = :subcategoryName,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE LOWER(COALESCE(displayMerchantName, merchantRaw, '')) = LOWER(:merchantName)
""")
    suspend fun updateCategoryForMerchantName(
        merchantName: String,
        categoryName: String?,
        subcategoryName: String?,
        updatedAtEpochMs: Long
    ): Int

    @Query("""
    UPDATE transactions
    SET
        categoryName = :categoryName,
        subcategoryName = :subcategoryName,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE sourceKey = :sourceKey
      AND rawAlertId IN (
          SELECT id
          FROM raw_alerts
          WHERE combinedText LIKE :likePattern
      )
""")
    suspend fun updateCategoryForSimilarRawText(
        sourceKey: String,
        likePattern: String,
        categoryName: String?,
        subcategoryName: String?,
        updatedAtEpochMs: Long
    ): Int
}