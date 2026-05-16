package com.example.ledgerlens.domain.parser

import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.data.entity.TransactionRuleEntity
import com.example.ledgerlens.domain.ReviewStatus
import com.example.ledgerlens.domain.rules.MERCHANT_DEFAULT_RULE_SOURCE_KEY
import com.example.ledgerlens.domain.rules.applyRulesToTransaction
import com.example.ledgerlens.domain.source.SourceDetector
import com.example.ledgerlens.domain.privacy.REDACTED_SMS_PLACEHOLDER

data class ParseRunResult(
    val matchedAlertCount: Int = 0,
    val parsedCount: Int = 0,
    val skippedCount: Int = 0,
    val ignoredNonTransactionCount: Int = 0,
    val failedCount: Int = 0,
    val skippedFailedCount: Int = 0,
    val changedCount: Int = 0,
    val unchangedCount: Int = 0
)

data class RuleReapplyResult(
    val scanned: Int = 0,
    val matched: Int = 0,
    val changed: Int = 0,
    val unchanged: Int = 0,
    val skipped: Int = 0
)

enum class ParseMode {
    NEW_PENDING_ONLY,
    REPROCESS_FAILED,
    REPARSE_ALL_APPROVED
}

suspend fun parseIdentifiedSourceTransactions(
    database: AppDatabase,
    mode: ParseMode = ParseMode.NEW_PENDING_ONLY,
    redactRawSmsAfterParse: Boolean = false
): ParseRunResult {
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
    var skippedFailedCount = 0
    var changedCount = 0
    var unchangedCount = 0
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
            val existingTransaction = database.transactionDao().getByRawAlertId(rawAlert.id)
            if (existingTransaction != null && mode != ParseMode.REPARSE_ALL_APPROVED) {
                skippedCount++
                return@forEach
            }
            if (!shouldAttemptParse(rawAlert, mode)) {
                when (rawAlert.processingStatus) {
                    RawAlertStatus.IGNORED_NON_TRANSACTION -> ignoredNonTransactionCount++
                    RawAlertStatus.FAILED_TRANSACTION_PARSE -> skippedFailedCount++
                    else -> skippedCount++
                }
                return@forEach
            }
            if (rawAlert.combinedText == REDACTED_SMS_PLACEHOLDER) {
                skippedCount++
                return@forEach
            }

            val parsedTransaction = SmsTransactionParser.parse(
                rawAlert = rawAlert,
                source = source,
                sourceMessages = matchingAlerts.map { it.combinedText }
            )

            if (parsedTransaction == null) {
                val ignoreReason = SmsTransactionParser.ignoreReason(rawAlert, source)
                val status = if (
                    ignoreReason != null ||
                    SmsTransactionParser.isNonTransactionAlert(rawAlert) ||
                    FinancialSmsClassifier.isLikelyNonTransactionFinancialAlert(rawAlert.combinedText)
                ) {
                    RawAlertStatus.IGNORED_NON_TRANSACTION
                } else {
                    RawAlertStatus.FAILED_TRANSACTION_PARSE
                }

                database.rawAlertDao().updateProcessingStatus(
                    rawAlert.id,
                    status,
                    ignoreReason
                )

                if (status == RawAlertStatus.FAILED_TRANSACTION_PARSE) {
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

                if (existingTransaction == null) {
                    database.transactionDao().insert(ruleAdjustedTransaction)
                    parsedCount++
                } else {
                    val mergedTransaction = mergeReparsedTransaction(
                        existing = existingTransaction,
                        reparsed = ruleAdjustedTransaction
                    )
                    if (mergedTransaction != existingTransaction) {
                        database.transactionDao().update(mergedTransaction)
                        changedCount++
                    } else {
                        unchangedCount++
                    }
                }

                database.rawAlertDao().updateProcessingStatus(
                    rawAlert.id,
                    RawAlertStatus.PARSED_TRANSACTION
                )

                if (redactRawSmsAfterParse) {
                    database.rawAlertDao().redactStoredSmsText(
                        rawAlertId = rawAlert.id,
                        placeholder = REDACTED_SMS_PLACEHOLDER
                    )
                }
            }
        }
    }

    return ParseRunResult(
        matchedAlertCount = matchedAlertCount,
        parsedCount = parsedCount,
        skippedCount = skippedCount,
        ignoredNonTransactionCount = ignoredNonTransactionCount,
        failedCount = failedCount,
        skippedFailedCount = skippedFailedCount,
        changedCount = changedCount,
        unchangedCount = unchangedCount
    )
}

internal fun mergeReparsedTransaction(
    existing: TransactionEntity,
    reparsed: TransactionEntity
): TransactionEntity {
    val parserOwnedChanged = existing.amountCents != reparsed.amountCents ||
        !existing.currency.equals(reparsed.currency, ignoreCase = true) ||
        existing.occurredAtEpochMs != reparsed.occurredAtEpochMs

    val preservedReviewStatus = when {
        existing.reviewStatus == ReviewStatus.REVIEWED -> ReviewStatus.REVIEWED
        parserOwnedChanged -> ReviewStatus.NEEDS_REVIEW
        else -> reparsed.reviewStatus
    }

    return existing.copy(
        sourceKey = reparsed.sourceKey,
        amountCents = reparsed.amountCents,
        currency = reparsed.currency,
        merchantRaw = if (existing.merchantUserEdited) existing.merchantRaw else reparsed.merchantRaw,
        displayMerchantName = if (existing.merchantUserEdited) existing.displayMerchantName else reparsed.displayMerchantName,
        sourceInstitution = reparsed.sourceInstitution,
        accountHint = reparsed.accountHint,
        occurredAtEpochMs = reparsed.occurredAtEpochMs,
        receivedAtEpochMs = reparsed.receivedAtEpochMs,
        parseConfidence = reparsed.parseConfidence,
        reviewStatus = preservedReviewStatus,
        excludedFromSpending = if (existing.treatmentUserEdited) existing.excludedFromSpending else reparsed.excludedFromSpending,
        parserNotes = reparsed.parserNotes,
        transactionType = if (existing.treatmentUserEdited) existing.transactionType else reparsed.transactionType,
        accountingTreatment = if (existing.treatmentUserEdited) existing.accountingTreatment else reparsed.accountingTreatment,
        categoryName = if (existing.categoryUserEdited) existing.categoryName else reparsed.categoryName,
        spendingMerchantName = existing.spendingMerchantName,
        updatedAtEpochMs = if (parserOwnedChanged ||
            existing.parserNotes != reparsed.parserNotes ||
            existing.parseConfidence != reparsed.parseConfidence
        ) {
            System.currentTimeMillis()
        } else {
            existing.updatedAtEpochMs
        }
    )
}

private fun shouldAttemptParse(
    rawAlert: RawAlertEntity,
    mode: ParseMode
): Boolean {
    return when (mode) {
        ParseMode.NEW_PENDING_ONLY -> rawAlert.processingStatus !in setOf(
            RawAlertStatus.SOURCE_IGNORED,
            RawAlertStatus.PARSED_TRANSACTION,
            RawAlertStatus.IGNORED_NON_TRANSACTION,
            RawAlertStatus.FAILED_TRANSACTION_PARSE
        )
        ParseMode.REPROCESS_FAILED -> rawAlert.processingStatus == RawAlertStatus.FAILED_TRANSACTION_PARSE
        ParseMode.REPARSE_ALL_APPROVED -> rawAlert.processingStatus != RawAlertStatus.SOURCE_IGNORED
    }
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

suspend fun reapplySavedRulesToExistingTransactions(database: AppDatabase): RuleReapplyResult {
    val rawAlertsById = database.rawAlertDao()
        .getAllOnce()
        .associateBy { it.id }

    val merchantDefaultRules = database
        .transactionRuleDao()
        .getActiveRulesForSource(MERCHANT_DEFAULT_RULE_SOURCE_KEY)

    val sourceRuleCache = mutableMapOf<String, List<TransactionRuleEntity>>()
    var scanned = 0
    var matched = 0
    var changed = 0
    var unchanged = 0
    var skipped = 0

    database.transactionDao().getAllOnce().forEach { transaction ->
        scanned++
        val rawAlert = rawAlertsById[transaction.rawAlertId] ?: run {
            skipped++
            return@forEach
        }
        val sourceRules = sourceRuleCache.getOrPut(transaction.sourceKey) {
            database.transactionRuleDao().getActiveRulesForSource(transaction.sourceKey)
        }

        val updatedTransaction = applyRulesToTransaction(
            transaction = transaction,
            rawAlert = rawAlert,
            sourceRules = sourceRules,
            merchantDefaultRules = merchantDefaultRules
        )

        if (hasMeaningfulRuleChange(transaction, updatedTransaction)) {
            database.transactionDao().update(updatedTransaction)
            matched++
            changed++
        } else if (transaction != updatedTransaction) {
            matched++
            unchanged++
        }
    }

    return RuleReapplyResult(
        scanned = scanned,
        matched = matched,
        changed = changed,
        unchanged = unchanged,
        skipped = skipped
    )
}

private fun hasMeaningfulRuleChange(
    old: TransactionEntity,
    new: TransactionEntity
): Boolean {
    return old.merchantRaw != new.merchantRaw ||
        old.displayMerchantName != new.displayMerchantName ||
        old.spendingMerchantName != new.spendingMerchantName ||
        old.categoryName != new.categoryName ||
        old.transactionType != new.transactionType ||
        old.accountingTreatment != new.accountingTreatment ||
        old.excludedFromSpending != new.excludedFromSpending ||
        old.reviewStatus != new.reviewStatus ||
        old.parserNotes != new.parserNotes
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
