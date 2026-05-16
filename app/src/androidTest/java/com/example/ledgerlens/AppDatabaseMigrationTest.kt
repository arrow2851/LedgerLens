package com.example.ledgerlens

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ledgerlens.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val currentVersion = 10

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrateFromVersion5ToCurrentValidatesSchema() {
        helper.createDatabase(TEST_DB, 5).close()
        helper.runMigrationsAndValidate(TEST_DB, currentVersion, true, *AppDatabase.ALL_MIGRATIONS).close()
    }

    @Test
    fun migrateFromVersion6ToCurrentValidatesSchema() {
        helper.createDatabase(TEST_DB, 6).close()
        helper.runMigrationsAndValidate(TEST_DB, currentVersion, true, *AppDatabase.ALL_MIGRATIONS).close()
    }

    @Test
    fun migrateFromVersion7ToCurrentValidatesSchema() {
        helper.createDatabase(TEST_DB, 7).close()
        helper.runMigrationsAndValidate(TEST_DB, currentVersion, true, *AppDatabase.ALL_MIGRATIONS).close()
    }

    @Test
    fun migrateFromVersion8AddsRuleKindAndPreservesRules() {
        val db = helper.createDatabase(TEST_DB, 8)
        db.execSQL(
            """
            INSERT INTO transaction_rules (
                id, sourceKey, matchPhrase, normalizedMatchPhrase, merchantName,
                categoryName, transactionType, reviewStatus, excludedFromSpending,
                appliesToTreatment, applyCategoryAutomatically, requiresReview,
                active, createdAtEpochMs, updatedAtEpochMs
            ) VALUES (
                1, '__merchant_defaults__', 'Cafe', 'cafe', 'Cafe',
                'Dining', 'EXPENSE', NULL, 0,
                NULL, 1, 0,
                1, 100, 100
            )
            """.trimIndent()
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, currentVersion, true, *AppDatabase.ALL_MIGRATIONS)
        val cursor = migrated.query("SELECT ruleKind FROM transaction_rules WHERE id = 1")
        cursor.use {
            it.moveToFirst()
            assertEquals("MERCHANT_DEFAULT", it.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrateFromVersion9AddsIgnoreReasonColumn() {
        helper.createDatabase(TEST_DB, 9).close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, currentVersion, true, *AppDatabase.ALL_MIGRATIONS)
        val cursor = migrated.query("PRAGMA table_info(raw_alerts)")
        cursor.use {
            var found = false
            while (it.moveToNext()) {
                if (it.getString(1) == "ignoreReason") {
                    found = true
                }
            }
            assertEquals(true, found)
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "ledgerlens-migration-test"
    }
}
