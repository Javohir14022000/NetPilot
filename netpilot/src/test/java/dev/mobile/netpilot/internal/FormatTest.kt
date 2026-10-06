package dev.mobile.netpilot.internal

import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun `formats sizes with decimal units`() {
        assertNull(Format.size(null))
        assertEquals("999 B", Format.size(999))
        assertEquals("1.5 KB", Format.size(1_500))
        assertEquals("2.0 MB", Format.size(2_000_000))
    }

    @Test
    fun `formats durations in ms below one second`() {
        assertNull(Format.duration(null))
        assertEquals("250 ms", Format.duration(250))
        assertEquals("1.50 s", Format.duration(1_500))
    }

    @Test
    fun `status label reflects transaction state`() {
        assertEquals("404", Format.statusLabel(404, TransactionStatus.COMPLETE))
        assertEquals("ERR", Format.statusLabel(null, TransactionStatus.FAILED))
        assertEquals("…", Format.statusLabel(null, TransactionStatus.IN_PROGRESS))
    }

    @Test
    fun `share text cuts very long bodies`() {
        val transaction = HttpTransaction(
            requestDate = 0,
            method = "GET",
            url = "https://example.com/a",
            host = "example.com",
            path = "/a",
            scheme = "https",
            responseCode = 200,
            responseMessage = "OK",
            responseHeaders = listOf(HttpHeader("Content-Type", "text/plain")),
            responseBody = "x".repeat(TransactionText.MAX_BODY_CHARS + 10),
        )

        val text = TransactionText.format(transaction)

        assertTrue(text.startsWith("GET https://example.com/a"))
        assertTrue(text.contains("Status: 200 OK"))
        assertTrue(text.contains("[cut to ${TransactionText.MAX_BODY_CHARS} characters for sharing]"))
    }
}
