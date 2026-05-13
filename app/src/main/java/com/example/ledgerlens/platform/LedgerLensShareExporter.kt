package com.example.ledgerlens.platform

import android.content.Intent
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.ledgerlens.data.AppDatabase
import com.example.ledgerlens.domain.export.buildParserCorpusJsonl
import com.example.ledgerlens.domain.export.buildTransactionsCsv
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LedgerLensShareExporter(
    private val activity: ComponentActivity,
    private val database: AppDatabase
) {

    fun exportTransactionsCsv() {
        activity.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val transactions = database.transactionDao().getAllOnce()
                val exportFile = createTimestampedExportFile(
                    directoryName = "exports",
                    filePrefix = "ledgerlens-transactions",
                    fileExtension = "csv"
                )
                exportFile.writeText(buildTransactionsCsv(transactions))
                exportFile
            }

            shareFile(
                file = file,
                mimeType = "text/csv",
                subject = "LedgerLens transaction export",
                chooserTitle = "Export LedgerLens CSV"
            )
        }
    }

    fun exportParserCorpusJsonl() {
        activity.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val rawAlerts = database.rawAlertDao().getAllOnce()
                val transactionsByRawAlertId = database.transactionDao()
                    .getAllOnce()
                    .associateBy { it.rawAlertId }
                val exportFile = createTimestampedExportFile(
                    directoryName = "parser-corpus",
                    filePrefix = "ledgerlens-parser-corpus",
                    fileExtension = "jsonl"
                )
                exportFile.writeText(
                    buildParserCorpusJsonl(
                        rawAlerts = rawAlerts,
                        transactionsByRawAlertId = transactionsByRawAlertId
                    )
                )
                exportFile
            }

            shareFile(
                file = file,
                mimeType = "application/json",
                subject = "LedgerLens parser corpus export",
                chooserTitle = "Export parser corpus"
            )
        }
    }

    private fun createTimestampedExportFile(
        directoryName: String,
        filePrefix: String,
        fileExtension: String
    ): File {
        val exportDir = File(
            activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            directoryName
        )
        exportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(Date())
        return File(exportDir, "$filePrefix-$timestamp.$fileExtension")
    }

    private fun shareFile(
        file: File,
        mimeType: String,
        subject: String,
        chooserTitle: String
    ) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        activity.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
}
