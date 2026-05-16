package com.example.ledgerlens

import com.example.ledgerlens.domain.parser.DateExtractor
import com.example.ledgerlens.domain.parser.MoneyExtractor
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAndDateExtractorTest {

    @Test
    fun parsesMoneyWithoutFloatingPointRounding() {
        mapOf(
            "$20" to 2000L,
            "$20.0" to 2000L,
            "$20.00" to 2000L,
            "Rs.869.1" to 86910L,
            "$1,234.56" to 123456L,
            "₹20,000" to 2_000_000L,
            "$0.99" to 99L
        ).forEach { (text, expected) ->
            assertEquals(text, expected, MoneyExtractor.parseAmountToMinorUnits(text))
        }
    }

    @Test
    fun rejectsInvalidMoney() {
        assertNull(MoneyExtractor.parseAmountToMinorUnits("Your code is 123456"))
        assertNull(MoneyExtractor.parseAmountToMinorUnits("Amount $12.345"))
    }

    @Test
    fun extractsExplicitYearDate() {
        val received = date("2024-12-01")
        val parsed = DateExtractor.extractTransactionDateEpochMs(
            text = "Capital One charge on November 28, 2024 at Amazon.",
            receivedAtEpochMs = received
        )

        assertEquals(date("2024-11-28"), parsed)
    }

    @Test
    fun infersClosestPlausibleYearForMonthDay() {
        val received = date("2024-01-02")
        val parsed = DateExtractor.extractTransactionDateEpochMs(
            text = "Chase debit card transaction on Dec 31 at 6:06 AM.",
            receivedAtEpochMs = received
        )

        assertEquals(date("2023-12-31"), parsed)
    }

    @Test
    fun fallsBackWhenNoReliableDateExists() {
        val received = date("2024-03-10")

        assertEquals(
            received,
            DateExtractor.extractTransactionDateEpochMs(
                text = "Your purchase was approved.",
                receivedAtEpochMs = received
            )
        )
    }

    private fun date(value: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)!!.time
    }
}
