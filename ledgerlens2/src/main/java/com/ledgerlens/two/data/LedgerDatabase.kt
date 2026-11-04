package com.ledgerlens.two.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Schema v1: deliberately independent of LedgerLens 1.x with no legacy migrations. */
@Entity(tableName = "sms")
data class StoredSms(
    @PrimaryKey val smsId: Long,
    val sender: String,
    val body: String,
    val receivedAtMs: Long,
    val sentAtMs: Long?,
    val insertedAtMs: Long
)

@Entity(tableName = "transactions", indices = [
    Index(value = ["smsId"], unique = true),
    Index(value = ["sourceKey"]),
    Index(value = ["occurredAtMs"]),
    Index(value = ["merchantKey"])
])
data class LedgerTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val smsId: Long,
    val sourceKey: String,
    val sender: String,
    val accountHint: String? = null,
    val amountMinor: Long,
    val currency: String,
    val kind: String,
    val merchant: String?,
    val merchantKey: String,
    val category: String?,
    val confidence: Double,
    val reason: String,
    val occurredAtMs: Long,
    val receivedAtMs: Long,
    val excluded: Boolean = false,
    val verified: Boolean = false,
    val duplicateOfId: Long? = null,
    val duplicateDismissed: Boolean = false,
    val merchantUserEdited: Boolean = false,
    val categoryUserEdited: Boolean = false,
    val kindUserEdited: Boolean = false,
    val updatedAtMs: Long
)

@Entity(tableName = "sources")
data class FinancialSource(
    @PrimaryKey val sender: String,
    val approved: Boolean = false,
    val institution: String? = null,
    val updatedAtMs: Long
)

@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val merchantDisplay: String,
    val category: String,
    val active: Boolean = true,
    val updatedAtMs: Long
)

/** Immutable local history of important user edits and automated rule changes. */
@Entity(tableName = "audit_events", indices = [Index(value = ["transactionId"])])
data class TransactionAudit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transactionId: Long,
    val operation: String,
    val beforeJson: String,
    val afterJson: String,
    val editedAtMs: Long
)

@Entity(tableName = "budgets")
data class MonthlyBudget(
    @PrimaryKey val key: String,
    val month: String,
    val category: String,
    val currency: String,
    val limitMinor: Long,
    val updatedAtMs: Long
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtMs DESC, id DESC")
    fun observeTransactions(): Flow<List<LedgerTransaction>>

    @Query("SELECT * FROM sources ORDER BY approved DESC, sender")
    fun observeSources(): Flow<List<FinancialSource>>

    @Query("SELECT * FROM merchant_rules ORDER BY merchantDisplay")
    fun observeRules(): Flow<List<MerchantRule>>

    @Query("SELECT * FROM budgets ORDER BY month DESC, category")
    fun observeBudgets(): Flow<List<MonthlyBudget>>

    @Query("SELECT * FROM audit_events ORDER BY editedAtMs DESC")
    suspend fun allAudit(): List<TransactionAudit>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAudit(audit: TransactionAudit): Long

    @Query("SELECT * FROM sms ORDER BY receivedAtMs")
    suspend fun allSms(): List<StoredSms>

    @Query("SELECT * FROM transactions ORDER BY occurredAtMs DESC, id DESC")
    suspend fun allTransactions(): List<LedgerTransaction>

    @Query("SELECT * FROM sources")
    suspend fun allSources(): List<FinancialSource>

    @Query("SELECT * FROM merchant_rules")
    suspend fun allRules(): List<MerchantRule>

    @Query("SELECT * FROM budgets")
    suspend fun allBudgets(): List<MonthlyBudget>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun transactionById(id: Long): LedgerTransaction?

    @Query("SELECT * FROM transactions WHERE smsId = :smsId LIMIT 1")
    suspend fun bySmsId(smsId: Long): LedgerTransaction?

    @Query("SELECT * FROM sms WHERE smsId = :smsId LIMIT 1")
    suspend fun smsById(smsId: Long): StoredSms?

    @Query("SELECT * FROM sources WHERE sender = :sender LIMIT 1")
    suspend fun sourceBySender(sender: String): FinancialSource?

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key LIMIT 1")
    suspend fun ruleByKey(key: String): MerchantRule?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSms(sms: StoredSms): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: LedgerTransaction): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: FinancialSource): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: MerchantRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(budget: MonthlyBudget)

    @Update suspend fun updateTransaction(transaction: LedgerTransaction)
    @Update suspend fun updateSource(source: FinancialSource)

    @Query("SELECT * FROM transactions WHERE merchantKey = :key")
    suspend fun transactionsForMerchant(key: String): List<LedgerTransaction>

    @Query("SELECT * FROM transactions WHERE sourceKey = :sender ORDER BY occurredAtMs DESC")
    suspend fun transactionsForSource(sender: String): List<LedgerTransaction>

    @Query("DELETE FROM sms") suspend fun clearSms()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM sources") suspend fun clearSources()
    @Query("DELETE FROM merchant_rules") suspend fun clearRules()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM audit_events") suspend fun clearAudit()
}

@Database(
    entities = [StoredSms::class, LedgerTransaction::class, FinancialSource::class,
        MerchantRule::class, MonthlyBudget::class, TransactionAudit::class],
    version = 1,
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    companion object {
        @Volatile private var instance: LedgerDatabase? = null
        fun get(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, LedgerDatabase::class.java,
                    "ledgerlens2-v1.db"
                ).build().also { instance = it }
            }
    }
}
