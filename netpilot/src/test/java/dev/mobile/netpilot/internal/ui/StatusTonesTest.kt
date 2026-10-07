package dev.mobile.netpilot.internal.ui

import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.mock.MockOutcome
import dev.mobile.netpilot.internal.mock.MockRule
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTonesTest {

    private val palette = LightStatusPalette

    @Test
    fun `status codes map to their tone`() {
        assertEquals(palette.success, palette.forStatus(204, TransactionStatus.COMPLETE))
        assertEquals(palette.redirect, palette.forStatus(301, TransactionStatus.COMPLETE))
        assertEquals(palette.clientError, palette.forStatus(429, TransactionStatus.COMPLETE))
        assertEquals(palette.serverError, palette.forStatus(500, TransactionStatus.COMPLETE))
        assertEquals(palette.serverError, palette.forStatus(null, TransactionStatus.FAILED))
        assertEquals(palette.neutral, palette.forStatus(null, TransactionStatus.IN_PROGRESS))
    }

    @Test
    fun `rules are colored by what the app receives`() {
        val respond404 = MockRule(name = "missing", urlPattern = "/x", statusCode = 404)
        val offline = MockRule(name = "offline", urlPattern = "/x", outcome = MockOutcome.NO_INTERNET)

        assertEquals(palette.clientError, palette.forRule(respond404))
        assertEquals(palette.clientError, palette.forRule(offline))
        assertEquals(palette.success, palette.forRule(MockRule(name = "ok", urlPattern = "/x")))
    }
}
