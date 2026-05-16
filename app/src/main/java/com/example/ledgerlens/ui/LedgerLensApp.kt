package com.example.ledgerlens.ui

import androidx.compose.runtime.Composable
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.domain.privacy.RawSmsRetention

@Composable
fun LedgerLensApp(
    database: AppDatabase,
    syncStatusText: String,
    onSyncSmsAlerts: () -> Unit,
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit,
    onExportTransactions: () -> Unit,
    onExportParserDiagnostics: () -> Unit,
    rawSmsRetention: RawSmsRetention,
    onRawSmsRetentionChanged: (RawSmsRetention) -> Unit,
    onDeleteExportedFiles: () -> Unit
) {
    LedgerLensAppRoot(
        database = database,
        syncStatusText = syncStatusText,
        onSyncSmsAlerts = onSyncSmsAlerts,
        onBackfillSmsHistory = onBackfillSmsHistory,
        onRefreshLatestSms = onRefreshLatestSms,
        onExportTransactions = onExportTransactions,
        onExportParserDiagnostics = onExportParserDiagnostics,
        rawSmsRetention = rawSmsRetention,
        onRawSmsRetentionChanged = onRawSmsRetentionChanged,
        onDeleteExportedFiles = onDeleteExportedFiles
    )
}
