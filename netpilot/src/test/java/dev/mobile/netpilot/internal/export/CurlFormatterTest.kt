package dev.mobile.netpilot.internal.export

import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlFormatterTest {

    @Test
    fun `GET request has no method flag and a quoted URL`() {
        val curl = CurlFormatter.format(transaction(url = "https://api.example.com/a?b=1"))

        assertEquals("curl 'https://api.example.com/a?b=1'", curl)
    }

    @Test
    fun `includes method, headers and body with escaped single quotes`() {
        val transaction = transaction(
            method = "POST",
            headers = listOf(HttpHeader("Content-Type", "application/json")),
            body = """{"name":"O'Neil"}""",
        )

        val curl = CurlFormatter.format(transaction)

        val expected = """curl -X POST 'https://api.example.com/a' \
  -H 'Content-Type: application/json' \
  --data-raw '{"name":"O'\''Neil"}'"""
        assertEquals(expected, curl)
    }

    @Test
    fun `omits bodies that were not fully captured`() {
        val curl = CurlFormatter.format(transaction(method = "POST", body = "[binary body not shown]"))

        assertFalse(curl.contains("--data-raw"))
    }

    @Test
    fun `keeps redacted headers redacted`() {
        val transaction = transaction(headers = listOf(HttpHeader("Authorization", NetPilotConfig.REDACTED_VALUE)))

        assertTrue(CurlFormatter.format(transaction).contains("'Authorization: ${NetPilotConfig.REDACTED_VALUE}'"))
    }

    private fun transaction(
        method: String = "GET",
        url: String = "https://api.example.com/a",
        headers: List<HttpHeader> = emptyList(),
        body: String? = null,
    ) = HttpTransaction(
        requestDate = 0,
        method = method,
        url = url,
        host = "api.example.com",
        path = "/a",
        scheme = "https",
        requestHeaders = headers,
        requestBody = body,
    )
}
