package com.example.ledgerlens.domain.parser

import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.rules.MERCHANT_DEFAULT_RULE_SOURCE_KEY
import com.example.ledgerlens.domain.rules.applyRulesToTransaction
import com.example.ledgerlens.domain.source.SourceDetector

data class ParseRunResult(
    val matchedAlertCount: Int,
    val parsedCount: Int,
    val skippedCount: Int,
    val ignoredNonTransactionCount: Int,
    val failedCount: Int
)

suspend fun parseIdentifiedSourceTransactions(database: AppDatabase): ParseRunResult {
    val rawAlertsOnce = database.rawAlertDao().getAllOnce()
    val identifiedSources = database
        .financialSourceDao()
        .getIdentifiedSourcesOnce()
    val merchantDefaultRules = database
        .transactionRuleDao()
        .getActiveRulesForSource(MERCHANT_DEFAULT_RULE_SOURCE_KEY)

    var parsedCount = 0
    var skippedCount = 0
    var ignoredNonTransactionCount = 0
    var failedCount = 0
    var matchedAlertCount = 0

    identifiedSources.forEach { source ->
        val sourceRules = database
            .transactionRuleDao()
            .getActiveRulesForSource(source.sourceKey)

        val matchingAlerts = rawAlertsOnce.filter { rawAlert ->
            SourceDetector.matchesSource(rawAlert, source)
        }

        matchedAlertCount += matchingAlerts.size

        matchingAlerts.forEach { rawAlert ->
            if (database.transactionDao().countByRawAlertId(rawAlert.id) > 0) {
                skippedCount++
                return@forEach
            }

            val parsedTransaction = SmsTransactionParser.parse(
                rawAlert = rawAlert,
                source = source,
                sourceMessages = matchingAlerts.map { it.combinedText }
            )

            if (parsedTransaction == null) {
                val status = if (SmsTransactionParser.isNonTransactionAlert(rawAlert)) {
                    "IGNORED_NON_TRANSACTION"
                } else {
                    "FAILED_TRANSACTION_PARSE"
                }

                database.rawAlertDao().updateProcessingStatus(
                    rawAlert.id,
                    status
                )

                if (status == "FAILED_TRANSACTION_PARSE") {
                    failedCount++
                } else {
                    ignoredNonTransactionCount++
                }
            } else {
                val ruleAdjustedTransaction = applyRulesToTransaction(
                    transaction = parsedTransaction,
                    rawAlert = rawAlert,
                    sourceRules = sourceRules,
                    merchantDefaultRules = merchantDefaultRules
                )

                database.transactionDao().insert(ruleAdjustedTransaction)

                database.rawAlertDao().updateProcessingStatus(
                    rawAlert.id,
                    "PARSED_TRANSACTION"
                )

                parsedCount++
            }
        }
    }

    return ParseRunResult(
        matchedAlertCount = matchedAlertCount,
        parsedCount = parsedCount,
        skippedCount = skippedCount,
        ignoredNonTransactionCount = ignoredNonTransactionCount,
        failedCount = failedCount
    )
}

suspend fun detectAndSaveSources(
    database: AppDatabase,
    rawAlerts: List<RawAlertEntity>
): Int {
    database.financialSourceDao().deleteLegacyNonSenderSources()

    val detectedSources = SourceDetector.detect(rawAlerts)

    detectedSources.forEach { detected ->
        val existing = database
            .financialSourceDao()
            .getBySourceKey(detected.sourceKey)

        val sourceToSave = if (existing == null) {
            detected
        } else {
            detected.copy(
                confirmedAccountType = existing.confirmedAccountType,
                displayName = existing.displayName ?: detected.displayName,
                userConfirmed = existing.userConfirmed,
                ignored = existing.ignored,
                createdAtEpochMs = existing.createdAtEpochMs,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        }

        database.financialSourceDao().upsert(sourceToSave)
    }

    return detectedSources.size
}

suspend fun reapplySavedRulesToExistingTransactions(database: AppDatabase): Int {
    val rawAlertsById = database.rawAlertDao()
        .getAllOnce()
        .associateBy { it.id }

    val merchantDefaultRules = database
        .transactionRuleDao()
        .getActiveRulesForSource(MERCHANT_DEFAULT_RULE_SOURCE_KEY)

    val sourceRuleCache = mutableMapOf<String, List<TransactionRuleEntity>>()
    var updatedCount = 0

    database.transactionDao().getAllOnce().forEach { transaction ->
        val rawAlert = rawAlertsById[transaction.rawAlertId] ?: return@forEach
        val sourceRules = sourceRuleCache.getOrPut(transaction.sourceKey) {
            database.transactionRuleDao().getActiveRulesForSource(transaction.sourceKey)
        }

        val updatedTransaction = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = sourceRules,
            merchantDefaultRules = merchantDefaultRules
        )

        if (updatedTransaction != transaction) {
            database.transactionDao().update(updatedTransaction)
            updatedCount++
        }
    }

    return updatedCount
}

suspend fun updateRawAlertStatusesForSource(
    database: AppDatabase,
    source: FinancialSourceEntity,
    status: String
) {
    database.rawAlertDao()
        .getAllOnce()
        .filter { rawAlert -> SourceDetector.matchesSource(rawAlert, source) }
        .forEach { rawAlert ->
            database.rawAlertDao().updateProcessingStatus(
                rawAlertId = rawAlert.id,
                status = status
            )
        }
}
