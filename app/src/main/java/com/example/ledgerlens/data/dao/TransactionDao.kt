package com.example.ledgerlens.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.ledgerlens.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity): Int

    @Query("""
        SELECT *
        FROM transactions
        ORDER BY occurredAtEpochMs DESC
    """)
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("""
        SELECT *
        FROM transactions
        ORDER BY occurredAtEpochMs DESC
    """)
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE rawAlertId = :rawAlertId")
    suspend fun countByRawAlertId(rawAlertId: Long): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE sourceKey = :sourceKey")
    suspend fun deleteBySourceKey(sourceKey: String): Int

    @Query("""
    UPDATE transactions
    SET
        transactionType = :transactionType,
        accountingTreatment = :transactionType,
        excludedFromSpending = :excludedFromSpending,
        treatmentUserEdited = 1,
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
        treatmentUserEdited = 1,
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
        merchantUserEdited = 1,
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
    UPDATE transactions
    SET
        merchantRaw = :merchantName,
        displayMerchantName = :merchantName,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE sourceKey = :sourceKey
      AND merchantUserEdited = 0
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
        accountingTreatment = :transactionType,
        reviewStatus = :reviewStatus,
        excludedFromSpending = :excludedFromSpending,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE sourceKey = :sourceKey
      AND treatmentUserEdited = 0
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
        categoryUserEdited = 1,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :transactionId
""")
    suspend fun updateCategory(
        transactionId: Long,
        categoryName: String?,
        updatedAtEpochMs: Long
    )

    @Query("""
    UPDATE transactions
    SET
        categoryName = :categoryName,
        spendingMerchantName = :spendingMerchantName,
        categoryUserEdited = 1,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE id = :transactionId
""")
    suspend fun updateSpendingAttribution(
        transactionId: Long,
        categoryName: String?,
        spendingMerchantName: String?,
        updatedAtEpochMs: Long
    )

    @Query("""
    UPDATE transactions
    SET
        categoryName = :categoryName,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE LOWER(COALESCE(spendingMerchantName, displayMerchantName, merchantRaw, '')) = LOWER(:merchantName)
      AND categoryUserEdited = 0
""")
    suspend fun updateCategoryForMerchantName(
        merchantName: String,
        categoryName: String?,
        updatedAtEpochMs: Long
    ): Int

    @Query("""
    UPDATE transactions
    SET
        transactionType = :accountingTreatment,
        accountingTreatment = :accountingTreatment,
        excludedFromSpending = :excludedFromSpending,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE LOWER(COALESCE(spendingMerchantName, displayMerchantName, merchantRaw, '')) = LOWER(:merchantName)
      AND treatmentUserEdited = 0
""")
    suspend fun updateTreatmentForMerchantName(
        merchantName: String,
        accountingTreatment: String,
        excludedFromSpending: Boolean,
        updatedAtEpochMs: Long
    ): Int

    @Query("""
    UPDATE transactions
    SET
        categoryName = :categoryName,
        updatedAtEpochMs = :updatedAtEpochMs
    WHERE sourceKey = :sourceKey
      AND categoryUserEdited = 0
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
        updatedAtEpochMs: Long
    ): Int
}
