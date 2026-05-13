package com.example.ledgerlens.ui

import androidx.compose.runtime.Composable
import com.example.ledgerlens.data.AppDatabase

@Composable
fun LedgerLensApp(
    database: AppDatabase,
    onBackfillSmsHistory: () -> Unit,
    onRefreshLatestSms: () -> Unit,
    onExportTransactions: () -> Unit,
    onExportParserCorpus: () -> Unit
) {
    LedgerLensSourceSetupApp(
        database = database,
        onBackfillSmsHistory = onBackfillSmsHistory,
        onRefreshLatestSms = onRefreshLatestSms,
        onExportTransactions = onExportTransactions,
        onExportParserCorpus = onExportParserCorpus
    )
}
