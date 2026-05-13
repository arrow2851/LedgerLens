package com.example.ledgerlens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.domain.parser.detectAndSaveSources
import com.example.ledgerlens.platform.AndroidSmsImporter
import com.example.ledgerlens.platform.LedgerLensShareExporter
import com.example.ledgerlens.ui.LedgerLensApp
import com.example.ledgerlens.ui.theme.LedgerLensTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var smsImporter: AndroidSmsImporter
    private lateinit var shareExporter: LedgerLensShareExporter

    private var pendingSmsImportMode: SmsImportMode = SmsImportMode.REFRESH_LATEST

    private val requestSmsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                runSmsImport(pendingSmsImportMode)
            } else {
                Log.d("LedgerLensSmsImport", "READ_SMS permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getInstance(applicationContext)
        smsImporter = AndroidSmsImporter(
            contentResolver = contentResolver,
            rawAlertDao = database.rawAlertDao()
        )
        shareExporter = LedgerLensShareExporter(
            activity = this,
            database = database
        )

        setContent {
            LedgerLensTheme(dynamicColor = false) {
                LedgerLensApp(
                    database = database,
                    onBackfillSmsHistory = {
                        requestSmsImport(SmsImportMode.BACKFILL_HISTORY)
                    },
                    onRefreshLatestSms = {
                        requestSmsImport(SmsImportMode.REFRESH_LATEST)
                    },
                    onExportTransactions = {
                        shareExporter.exportTransactionsCsv()
                    },
                    onExportParserCorpus = {
                        shareExporter.exportParserCorpusJsonl()
                    }
                )
            }
        }
    }

    private fun requestSmsImport(mode: SmsImportMode) {
        pendingSmsImportMode = mode

        val permissionStatus = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        )

        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            runSmsImport(mode)
        } else {
            requestSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun runSmsImport(mode: SmsImportMode) {
        lifecycleScope.launch {
            val (importedCount, detectedCount) = withContext(Dispatchers.IO) {
                val imported = when (mode) {
                    SmsImportMode.BACKFILL_HISTORY -> {
                        smsImporter.importFinanceSmsMessages(daysBack = 365 * 5)
                    }
                    SmsImportMode.REFRESH_LATEST -> {
                        smsImporter.importFinanceSmsMessages(daysBack = 90)
                    }
                }
                val detected = detectAndSaveSources(
                    database = database,
                    rawAlerts = database.rawAlertDao().getAllOnce()
                )
                imported to detected
            }

            Log.d(
                "LedgerLensSmsImport",
                "mode=$mode importedCount=$importedCount detectedSources=$detectedCount"
            )
        }
    }
}

private enum class SmsImportMode {
    BACKFILL_HISTORY,
    REFRESH_LATEST
}
