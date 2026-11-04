package com.ledgerlens.two

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.ledgerlens.two.ui.LedgerApp

class MainActivity : ComponentActivity() {
    private val model: LedgerViewModel by viewModels()
    private val smsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) model.scanPhone()
        else model.notice.value = "SMS permission denied. You can still import a Source Scout JSON."
    }
    private val importScout = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(model::importSourceScoutFile) }
    private val exportCsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(model::exportCsv) }
    private val exportBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(model::exportBackup) }
    private val restoreBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(model::restoreBackup) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LedgerApp(
                model = model,
                onScan = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                        PackageManager.PERMISSION_GRANTED) model.scanPhone()
                    else smsPermission.launch(Manifest.permission.READ_SMS)
                },
                onImport = { importScout.launch(arrayOf("application/json", "*/*")) },
                onExportCsv = { exportCsv.launch("LedgerLens2-transactions.csv") },
                onExportBackup = { exportBackup.launch("LedgerLens2-full-backup.json") },
                onRestoreBackup = { restoreBackup.launch(arrayOf("application/json", "*/*")) }
            )
        }
    }
}
