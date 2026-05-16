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
    version = 10,
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        rawAlertId INTEGER NOT NULL,
                        sourceKey TEXT NOT NULL,
                        transactionType TEXT NOT NULL,
                        accountingTreatment TEXT NOT NULL,
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
                        merchantUserEdited INTEGER NOT NULL,
                        categoryUserEdited INTEGER NOT NULL,
                        treatmentUserEdited INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        categoryName TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO transactions_new (
                        id,
                        rawAlertId,
                        sourceKey,
                        transactionType,
                        accountingTreatment,
                        amountCents,
                        currency,
                        merchantRaw,
                        displayMerchantName,
                        sourceInstitution,
                        accountHint,
                        occurredAtEpochMs,
                        receivedAtEpochMs,
                        parseConfidence,
                        reviewStatus,
                        excludedFromSpending,
                        parserNotes,
                        merchantUserEdited,
                        categoryUserEdited,
                        treatmentUserEdited,
                        createdAtEpochMs,
                        updatedAtEpochMs,
                        categoryName
                    )
                    SELECT
                        id,
                        rawAlertId,
                        sourceKey,
                        transactionType,
                        accountingTreatment,
                        amountCents,
                        currency,
                        merchantRaw,
                        displayMerchantName,
                        sourceInstitution,
                        accountHint,
                        occurredAtEpochMs,
                        receivedAtEpochMs,
                        parseConfidence,
                        reviewStatus,
                        excludedFromSpending,
                        parserNotes,
                        merchantUserEdited,
                        categoryUserEdited,
                        treatmentUserEdited,
                        createdAtEpochMs,
                        updatedAtEpochMs,
                        categoryName
                    FROM transactions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_rawAlertId
                    ON transactions(rawAlertId)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_rules_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceKey TEXT NOT NULL,
                        matchPhrase TEXT NOT NULL,
                        normalizedMatchPhrase TEXT NOT NULL,
                        merchantName TEXT,
                        categoryName TEXT,
                        transactionType TEXT,
                        reviewStatus TEXT,
                        excludedFromSpending INTEGER,
                        appliesToTreatment TEXT,
                        applyCategoryAutomatically INTEGER NOT NULL,
                        requiresReview INTEGER NOT NULL,
                        active INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO transaction_rules_new (
                        id,
                        sourceKey,
                        matchPhrase,
                        normalizedMatchPhrase,
                        merchantName,
                        categoryName,
                        transactionType,
                        reviewStatus,
                        excludedFromSpending,
                        appliesToTreatment,
                        applyCategoryAutomatically,
                        requiresReview,
                        active,
                        createdAtEpochMs,
                        updatedAtEpochMs
                    )
                    SELECT
                        id,
                        sourceKey,
                        matchPhrase,
                        normalizedMatchPhrase,
                        merchantName,
                        categoryName,
                        transactionType,
                        reviewStatus,
                        excludedFromSpending,
                        appliesToTreatment,
                        applyCategoryAutomatically,
                        requiresReview,
                        active,
                        createdAtEpochMs,
                        updatedAtEpochMs
                    FROM transaction_rules
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE transaction_rules")
                db.execSQL("ALTER TABLE transaction_rules_new RENAME TO transaction_rules")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_transaction_rules_sourceKey_normalizedMatchPhrase
                    ON transaction_rules(sourceKey, normalizedMatchPhrase)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN spendingMerchantName TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transaction_rules ADD COLUMN ruleKind TEXT NOT NULL DEFAULT 'SOURCE_ALIAS'")
                db.execSQL(
                    """
                    UPDATE transaction_rules
                    SET ruleKind = 'MERCHANT_DEFAULT'
                    WHERE sourceKey = '__merchant_defaults__'
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_sourceKey ON transactions(sourceKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_occurredAtEpochMs ON transactions(occurredAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accountingTreatment ON transactions(accountingTreatment)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_reviewStatus ON transactions(reviewStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_currency ON transactions(currency)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_alerts_processingStatus ON raw_alerts(processingStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_alerts_postTimeEpochMs ON raw_alerts(postTimeEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_sources_userConfirmed_ignored ON financial_sources(userConfirmed, ignored)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_active_ruleKind ON transaction_rules(active, ruleKind)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE raw_alerts ADD COLUMN ignoreReason TEXT")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledgerlens.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
