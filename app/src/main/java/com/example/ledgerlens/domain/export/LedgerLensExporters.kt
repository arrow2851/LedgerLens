package com.example.ledgerlens.domain.export

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.data.entity.TransactionEntity
import com.example.ledgerlens.domain.TransactionTreatments
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun buildTransactionsCsv(transactions: List<TransactionEntity>): String {
    val header = listOf(
        "id",
        "occurredAt",
        "amount",
        "currency",
        "type",
        "accountingTreatment",
        "merchant",
        "spendingMerchant",
        "category",
        "spendingImpact",
        "source",
        "accountHint",
        "reviewStatus",
        "excludedFromSpending",
        "parseConfidence",
        "notes"
    )

    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val rows = transactions.map { transaction ->
        listOf(
            transaction.id.toString(),
            formatter.format(Date(transaction.occurredAtEpochMs)),
            "%.2f".format(Locale.US, transaction.amountCents / 100.0),
            transaction.currency,
            transaction.transactionType,
            transaction.accountingTreatment,
            transaction.displayMerchantName ?: transaction.merchantRaw ?: "",
            transaction.spendingMerchantName ?: "",
            transaction.categoryName ?: "",
            "%.2f".format(
                Locale.US,
                TransactionTreatments.spendingImpactCents(
                    treatment = transaction.accountingTreatment,
                    excludedFromSpending = transaction.excludedFromSpending,
                    amountCents = transaction.amountCents
                ) / 100.0
            ),
            transaction.sourceInstitution ?: transaction.sourceKey,
            transaction.accountHint ?: "",
            transaction.reviewStatus,
            transaction.excludedFromSpending.toString(),
            "%.2f".format(Locale.US, transaction.parseConfidence),
            transaction.parserNotes ?: ""
        )
    }

    return (listOf(header) + rows)
        .joinToString(separator = "\n") { row ->
            row.joinToString(separator = ",") { csvEscape(it) }
        } + "\n"
}

fun buildParserDiagnosticsJsonl(
    rawAlerts: List<RawAlertEntity>,
    transactionsByRawAlertId: Map<Long, TransactionEntity>,
    includeRawSmsText: Boolean = false
): String {
    return rawAlerts
        .sortedBy { it.postTimeEpochMs }
        .joinToString(separator = "\n", postfix = "\n") { alert ->
            val transaction = transactionsByRawAlertId[alert.id]
            buildString {
                append("{")
                appendJsonField("rawAlertId", alert.id)
                append(",")
                appendJsonField("notificationKey", alert.notificationKey)
                append(",")
                appendJsonField("sourcePackage", alert.sourcePackage)
                append(",")
                appendJsonField("sender", alert.title.orEmpty().removePrefix("SMS from ").trim())
                append(",")
                appendJsonField("timestampEpochMs", alert.postTimeEpochMs)
                append(",")
                appendJsonField("processingStatus", alert.processingStatus)
                append(",")
                appendJsonField(
                    "rawSmsText",
                    if (includeRawSmsText) alert.combinedText else "[redacted]"
                )
                append(",")
                append("\"parsed\":")
                if (transaction == null) {
                    append("null")
                } else {
                    append("{")
                    appendJsonField("transactionId", transaction.id)
                    append(",")
                    appendJsonField("sourceKey", transaction.sourceKey)
                    append(",")
                    appendJsonField("amountCents", transaction.amountCents)
                    append(",")
                    appendJsonField("currency", transaction.currency)
                    append(",")
                    appendJsonField("transactionType", transaction.transactionType)
                    append(",")
                    appendJsonField("accountingTreatment", transaction.accountingTreatment)
                    append(",")
                    appendJsonField("merchantRaw", transaction.merchantRaw)
                    append(",")
                    appendJsonField("displayMerchantName", transaction.displayMerchantName)
                    append(",")
                    appendJsonField("spendingMerchantName", transaction.spendingMerchantName)
                    append(",")
                    appendJsonField("categoryName", transaction.categoryName)
                    append(",")
                    appendJsonField(
                        "spendingImpactCents",
                        TransactionTreatments.spendingImpactCents(
                            treatment = transaction.accountingTreatment,
                            excludedFromSpending = transaction.excludedFromSpending,
                            amountCents = transaction.amountCents
                        )
                    )
                    append(",")
                    appendJsonField("reviewStatus", transaction.reviewStatus)
                    append(",")
                    appendJsonField("parseConfidence", transaction.parseConfidence)
                    append(",")
                    appendJsonField("parserNotes", transaction.parserNotes)
                    append(",")
                    appendJsonField("merchantUserEdited", transaction.merchantUserEdited)
                    append(",")
                    appendJsonField("categoryUserEdited", transaction.categoryUserEdited)
                    append(",")
                    appendJsonField("treatmentUserEdited", transaction.treatmentUserEdited)
                    append("}")
                }
                append("}")
            }
        }
}

private fun StringBuilder.appendJsonField(name: String, value: String?) {
    append("\"")
    append(jsonEscape(name))
    append("\":")
    if (value == null) {
        append("null")
    } else {
        append("\"")
        append(jsonEscape(value))
        append("\"")
    }
}

private fun StringBuilder.appendJsonField(name: String, value: Long) {
    append("\"")
    append(jsonEscape(name))
    append("\":")
    append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Double) {
    append("\"")
    append(jsonEscape(name))
    append("\":")
    append("%.4f".format(Locale.US, value))
}

private fun StringBuilder.appendJsonField(name: String, value: Boolean) {
    append("\"")
    append(jsonEscape(name))
    append("\":")
    append(value)
}

private fun jsonEscape(value: String): String {
    return buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}

private fun csvEscape(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"$escaped\""
    } else {
        escaped
    }
}
