package com.example.ledgerlens.domain.parser

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object DateExtractor {
    private val monthDayYearFormats = listOf(
        "MMMM d, yyyy",
        "MMM d, yyyy",
        "MMMM d yyyy",
        "MMM d yyyy"
    )

    private val monthDayFormats = listOf(
        "MMMM d",
        "MMM d"
    )

    fun extractTransactionDateEpochMs(
        text: String,
        receivedAtEpochMs: Long
    ): Long {
        extractExplicitYearDate(text)?.let { parsed ->
            if (!isFarFuture(parsed, receivedAtEpochMs)) return parsed
        }

        extractMonthDayWithoutYear(text, receivedAtEpochMs)?.let { parsed ->
            if (!isFarFuture(parsed, receivedAtEpochMs)) return parsed
        }

        return receivedAtEpochMs
    }

    private fun extractExplicitYearDate(text: String): Long? {
        val match = Regex(
            """(?i)\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{4}\b"""
        ).find(text) ?: return null
        val candidate = match.value.replace(Regex("""\s+"""), " ")
        return monthDayYearFormats.firstNotNullOfOrNull { format ->
            parseStrict(candidate, format)
        }
    }

    private fun extractMonthDayWithoutYear(text: String, receivedAtEpochMs: Long): Long? {
        val match = Regex(
            """(?i)\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\s+\d{1,2}\b"""
        ).find(text) ?: return null
        val candidate = match.value.replace(Regex("""\s+"""), " ")
        val receivedCalendar = Calendar.getInstance().apply {
            timeInMillis = receivedAtEpochMs
        }
        val receivedYear = receivedCalendar.get(Calendar.YEAR)

        return listOf(receivedYear, receivedYear - 1, receivedYear + 1)
            .mapNotNull { year ->
                monthDayFormats.firstNotNullOfOrNull { format ->
                    parseStrict(candidate, format)?.let { parsed ->
                        Calendar.getInstance().apply {
                            timeInMillis = parsed
                            set(Calendar.YEAR, year)
                        }.timeInMillis
                    }
                }
            }
            .filter { !isFarFuture(it, receivedAtEpochMs) }
            .minByOrNull { abs(it - receivedAtEpochMs) }
    }

    private fun parseStrict(value: String, pattern: String): Long? {
        return runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
            }.parse(value)?.time
        }.getOrNull()
    }

    private fun isFarFuture(candidateEpochMs: Long, receivedAtEpochMs: Long): Boolean {
        val oneDayMs = 24L * 60L * 60L * 1000L
        return candidateEpochMs > receivedAtEpochMs + oneDayMs
    }
}
