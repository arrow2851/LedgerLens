package com.example.ledgerlens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ledgerlens.data.dao.FinancialSourceDao
import com.example.ledgerlens.data.dao.RawAlertDao
import com.example.ledgerlens.data.dao.TransactionDao
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.dao.TransactionRuleDao
import com.example.ledgerlens.data.entity.TransactionRuleEntity

@Database(
    entities = [
        RawAlertEntity::class,
        TransactionEntity::class,
        FinancialSourceEntity::class,
        TransactionRuleEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionRuleDao(): TransactionRuleDao
    abstract fun rawAlertDao(): RawAlertDao
    abstract fun transactionDao(): TransactionDao
    abstract fun financialSourceDao(): FinancialSourceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        rawAlertId INTEGER NOT NULL,
                        sourceKey TEXT NOT NULL,
                        transactionType TEXT NOT NULL,
                        amountCents INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        merchantRaw TEXT,
                        displayMerchantName TEXT,
                        sourceInstitution TEXT,
                        accountHint TEXT,
                        occurredAtEpochMs INTEGER NOT NULL,
                        receivedAtEpochMs INTEGER NOT NULL,
                        parseConfidence REAL NOT NULL,
                        reviewStatus TEXT NOT NULL,
                        excludedFromSpending INTEGER NOT NULL,
                        parserNotes TEXT,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_rawAlertId
                    ON transactions(rawAlertId)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS financial_sources (
                        sourceKey TEXT PRIMARY KEY NOT NULL,
                        sourceAddress TEXT NOT NULL,
                        institutionName TEXT,
                        accountHint TEXT,
                        suggestedAccountType TEXT NOT NULL,
                        confirmedAccountType TEXT,
                        displayName TEXT,
                        detectionConfidence REAL NOT NULL,
                        userConfirmed INTEGER NOT NULL,
                        ignored INTEGER NOT NULL,
                        messageCount INTEGER NOT NULL,
                        firstSeenEpochMs INTEGER NOT NULL,
                        lastSeenEpochMs INTEGER NOT NULL,
                        sampleMessage TEXT,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN categoryName TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN subcategoryName TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS transaction_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sourceKey TEXT NOT NULL,
                matchPhrase TEXT NOT NULL,
                normalizedMatchPhrase TEXT NOT NULL,
                merchantName TEXT,
                categoryName TEXT,
                subcategoryName TEXT,
                transactionType TEXT,
                reviewStatus TEXT,
                excludedFromSpending INTEGER,
                active INTEGER NOT NULL,
                createdAtEpochMs INTEGER NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE UNIQUE INDEX IF NOT EXISTS index_transaction_rules_sourceKey_normalizedMatchPhrase
            ON transaction_rules(sourceKey, normalizedMatchPhrase)
            """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN accountingTreatment TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("UPDATE transactions SET accountingTreatment = transactionType WHERE accountingTreatment = 'UNKNOWN'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN merchantUserEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN categoryUserEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN treatmentUserEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transaction_rules ADD COLUMN appliesToTreatment TEXT")
                db.execSQL("ALTER TABLE transaction_rules ADD COLUMN applyCategoryAutomatically INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE transaction_rules ADD COLUMN requiresReview INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledgerlens.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
