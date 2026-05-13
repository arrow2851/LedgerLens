package com.example.ledgerlens

import com.example.ledgerlens.data.entity.RawAlertEntity
import com.example.ledgerlens.domain.source.SourceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDetectorTest {

    @Test
    fun detectGroupsBySenderNotAccountHint() {
        val sources = SourceDetector.detect(
            listOf(
                rawAlert(
                    id = 1,
                    sender = "24273",
                    body = "Chase card ending 1234 purchase $10.00 at Starbucks."
                ),
                rawAlert(
                    id = 2,
                    sender = "24273",
                    body = "Chase card ending 9876 purchase $20.00 at Walmart."
                )
            )
        )

        assertEquals(1, sources.size)
        assertEquals("sender:24273", sources.first().sourceKey)
        assertEquals("24273", sources.first().sourceAddress)
        assertTrue(sources.first().accountHint.orEmpty().contains("1234"))
        assertTrue(sources.first().accountHint.orEmpty().contains("9876"))
    }

    private fun rawAlert(
        id: Long,
        sender: String,
        body: String
    ): RawAlertEntity {
        return RawAlertEntity(
            id = id,
            notificationKey = "sms:$id",
            sourcePackage = "sms",
            title = "SMS from $sender",
            text = body,
            bigText = null,
            subText = null,
            combinedText = body,
            postTimeEpochMs = 1_700_000_000_000 + id,
            capturedAtEpochMs = 1_700_000_000_100 + id,
            processingStatus = "IMPORTED_SMS"
        )
    }
}
