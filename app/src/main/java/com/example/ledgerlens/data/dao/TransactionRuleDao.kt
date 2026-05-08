package com.example.ledgerlens.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: TransactionRuleEntity)

    @Query("""
        SELECT *
        FROM transaction_rules
        WHERE sourceKey = :sourceKey
          AND normalizedMatchPhrase = :normalizedMatchPhrase
        LIMIT 1
    """)
    suspend fun getBySourceAndPhrase(
        sourceKey: String,
        normalizedMatchPhrase: String
    ): TransactionRuleEntity?

    @Query("""
        SELECT *
        FROM transaction_rules
        WHERE sourceKey = :sourceKey
          AND active = 1
        ORDER BY updatedAtEpochMs DESC
    """)
    suspend fun getActiveRulesForSource(sourceKey: String): List<TransactionRuleEntity>

    @Query("""
        SELECT *
        FROM transaction_rules
        WHERE active = 1
        ORDER BY updatedAtEpochMs DESC
    """)
    fun observeActiveRules(): Flow<List<TransactionRuleEntity>>

    @Query("SELECT COUNT(*) FROM transaction_rules WHERE active = 1")
    fun observeActiveRuleCount(): Flow<Int>

    @Query("""
        UPDATE transaction_rules
        SET active = :active,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :ruleId
    """)
    suspend fun setRuleActive(
        ruleId: Long,
        active: Boolean,
        updatedAtEpochMs: Long
    ): Int

    @Query("DELETE FROM transaction_rules WHERE id = :ruleId")
    suspend fun deleteById(ruleId: Long): Int

    @Query("DELETE FROM transaction_rules")
    suspend fun deleteAll()
}
