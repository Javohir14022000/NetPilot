package dev.mobile.netpilot

import org.junit.Assert.assertTrue
import org.junit.Test

class NetPilotConfigTest {

    @Test
    fun `default config redacts authorization and cookies`() {
        val headers = NetPilotConfig().redactHeaders

        assertTrue(headers.containsAll(listOf("Authorization", "Cookie", "Set-Cookie")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects maxContentLength above the SQLite-safe limit`() {
        NetPilotConfig(maxContentLength = NetPilotConfig.MAX_CONTENT_LENGTH_LIMIT + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive maxRecords`() {
        NetPilotConfig(maxRecords = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive retention period`() {
        NetPilotConfig(retentionPeriodMillis = 0)
    }
}
