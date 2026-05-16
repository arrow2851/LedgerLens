package com.example.ledgerlens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.domain.privacy.RawSmsRetention
import com.example.ledgerlens.domain.sync.SmsSyncMode
import com.example.ledgerlens.domain.sync.SmsSyncUseCase
import com.example.ledgerlens.platform.AndroidSmsImporter
import com.example.ledgerlens.platform.LedgerLensShareExporter
import com.example.ledgerlens.platform.SharedPreferencesPrivacySettingsStore
import com.example.ledgerlens.platform.SharedPreferencesSmsSyncCursorStore
import com.example.ledgerlens.ui.LedgerLensApp
import com.example.ledgerlens.ui.theme.LedgerLensTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var smsImporter: AndroidSmsImporter
    private lateinit var smsSyncUseCase: SmsSyncUseCase
    private lateinit var shareExporter: LedgerLensShareExporter
    private lateinit var privacySettingsStore: SharedPreferencesPrivacySettingsStore

    private var pendingSmsImportMode: SmsSyncMode = SmsSyncMode.REFRESH_LATEST
    private var smsSyncStatusText by mutableStateOf("")
    private var rawSmsRetention by mutableStateOf(RawSmsRetention.KEEP_FOR_AUDIT)

    private val requestSmsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                runSmsImport(pendingSmsImportMode)
            } else {
                smsSyncStatusText =
                    "SMS permission was denied. LedgerLens needs SMS access to sync alerts on this device."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getInstance(applicationContext)
        smsImporter = AndroidSmsImporter(
            contentResolver = contentResolver,
            rawAlertDao = database.rawAlertDao()
        )
        privacySettingsStore = SharedPreferencesPrivacySettingsStore(applicationContext)
        rawSmsRetention = privacySettingsStore.getRawSmsRetention()
        smsSyncUseCase = SmsSyncUseCase(
            database = database,
            smsImporter = smsImporter,
            cursorStore = SharedPreferencesSmsSyncCursorStore(applicationContext),
            privacySettingsStore = privacySettingsStore
        )
        shareExporter = LedgerLensShareExporter(
            activity = this,
            database = database
        )

        setContent {
            LedgerLensTheme(dynamicColor = false) {
                LedgerLensApp(
                    database = database,
                    syncStatusText = smsSyncStatusText,
                    onSyncSmsAlerts = {
                        requestSmsImport(SmsSyncMode.REFRESH_LATEST)
                    },
                    onBackfillSmsHistory = {
                        requestSmsImport(SmsSyncMode.BACKFILL_HISTORY)
                    },
                    onRefreshLatestSms = {
                        requestSmsImport(SmsSyncMode.REFRESH_LATEST)
                    },
                    onExportTransactions = {
                        shareExporter.exportTransactionsCsv()
                    },
                    onExportParserDiagnostics = {
                        shareExporter.exportParserDiagnosticsJsonl()
                    },
                    rawSmsRetention = rawSmsRetention,
                    onRawSmsRetentionChanged = { retention ->
                        privacySettingsStore.setRawSmsRetention(retention)
                        rawSmsRetention = retention
                        smsSyncStatusText = when (retention) {
                            RawSmsRetention.KEEP_FOR_AUDIT ->
                                "LedgerLens will keep original SMS text locally for audit and troubleshooting."
                            RawSmsRetention.REDACT_AFTER_PARSE ->
                                "LedgerLens will redact stored original SMS text after successful parsing."
                        }
                    },
                    onDeleteExportedFiles = {
                        val deleted = shareExporter.deleteExportedFiles()
                        smsSyncStatusText = "Deleted $deleted exported LedgerLens files from this device."
                    }
                )
            }
        }
    }

    private fun requestSmsImport(mode: SmsSyncMode) {
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

    private fun runSmsImport(mode: SmsSyncMode) {
        smsSyncStatusText = "Syncing SMS alerts..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                smsSyncUseCase.sync(mode)
            }

            smsSyncStatusText = result.userMessage()
            Log.d(
                "LedgerLensSmsImport",
                "mode=$mode scanned=${result.scannedCount} financeLooking=${result.financeLookingCount} imported=${result.importedCount} duplicates=${result.duplicateCount} detectedSources=${result.detectedSourceCount} parsed=${result.parsedCount}"
            )
        }
    }
}
