package com.example.ledgerlens.domain.source

import androidx.room.withTransaction
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.data.entity.FinancialSourceEntity
import com.example.ledgerlens.domain.parser.ParseMode
import com.example.ledgerlens.domain.parser.ParseRunResult
import com.example.ledgerlens.domain.parser.RawAlertStatus
import com.example.ledgerlens.domain.parser.parseIdentifiedSourceTransactions
import com.example.ledgerlens.domain.parser.updateRawAlertStatusesForSource

class SourceReviewUseCase(
    private val database: AppDatabase
) {
    suspend fun useSender(
        source: FinancialSourceEntity,
        accountType: String,
        redactRawSmsAfterParse: Boolean = false
    ): ParseRunResult {
        var result = ParseRunResult()
        database.withTransaction {
            database.financialSourceDao().confirmAccountType(
                sourceKey = source.sourceKey,
                accountType = accountType,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            updateRawAlertStatusesForSource(
                database = database,
                source = source,
                status = RawAlertStatus.PARSE_PENDING
            )
            result = parseIdentifiedSourceTransactions(
                database = database,
                mode = ParseMode.NEW_PENDING_ONLY,
                redactRawSmsAfterParse = redactRawSmsAfterParse
            )
        }
        return result
    }

    suspend fun ignoreSender(source: FinancialSourceEntity): Int {
        var existingTransactionCount = 0
        database.withTransaction {
            existingTransactionCount = database.transactionDao()
                .countBySourceKey(source.sourceKey)
            database.financialSourceDao().ignoreSource(
                sourceKey = source.sourceKey,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            updateRawAlertStatusesForSource(
                database = database,
                source = source,
                status = RawAlertStatus.SOURCE_IGNORED
            )
        }
        return existingTransactionCount
    }

    suspend fun moveSenderBackToReview(source: FinancialSourceEntity): Int {
        var existingTransactionCount = 0
        database.withTransaction {
            existingTransactionCount = database.transactionDao()
                .countBySourceKey(source.sourceKey)
            database.financialSourceDao().resetSourceConfirmation(
                sourceKey = source.sourceKey,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            updateRawAlertStatusesForSource(
                database = database,
                source = source,
                status = RawAlertStatus.SOURCE_PENDING
            )
        }
        return existingTransactionCount
    }
}
