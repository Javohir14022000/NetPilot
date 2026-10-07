package dev.mobile.netpilot.internal.export

import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HarFormatterTest {

    @Test
    fun `produces a HAR 1_2 log with entries in time order`() {
        val transactions = listOf(
            transaction(requestDate = 2_000, url = "https://api.example.com/b"),
            transaction(requestDate = 1_000, url = "https://api.example.com/a"),
        )

        val log = JSONObject(HarFormatter.format(transactions)).getJSONObject("log")

        assertEquals("1.2", log.getString("version"))
        assertEquals("NetPilot", log.getJSONObject("creator").getString("name"))
        val entries = log.getJSONArray("entries")
        assertEquals(2, entries.length())
        val first = entries.getJSONObject(0)
        assertEquals("https://api.example.com/a", first.getJSONObject("request").getString("url"))
        assertEquals("1970-01-01T00:00:01.000Z", first.getString("startedDateTime"))
    }

    @Test
    fun `maps request and response details`() {
        val transaction = transaction(
            method = "POST",
            url = "https://api.example.com/search?q=a%20b&flag",
            requestBody = """{"q":"a b"}""",
        ).copy(
            requestContentType = "application/json",
            protocol = "h2",
            responseCode = 201,
            responseMessage = "Created",
            responseHeaders = listOf(HttpHeader("Location", "/items/1")),
            responseContentType = "application/json",
            responseSize = 9,
            responseBody = """{"id":1}""",
            tookMs = 120,
            mockRuleName = "create item",
        )

        val entry = JSONObject(HarFormatter.format(listOf(transaction)))
            .getJSONObject("log").getJSONArray("entries").getJSONObject(0)

        val request = entry.getJSONObject("request")
        assertEquals("POST", request.getString("method"))
        assertEquals("HTTP/2", request.getString("httpVersion"))
        val query = request.getJSONArray("queryString")
        assertEquals("a b", query.getJSONObject(0).getString("value"))
        assertEquals("flag", query.getJSONObject(1).getString("name"))
        assertEquals("""{"q":"a b"}""", request.getJSONObject("postData").getString("text"))

        val response = entry.getJSONObject("response")
        assertEquals(201, response.getInt("status"))
        assertEquals("Created", response.getString("statusText"))
        assertEquals("/items/1", response.getString("redirectURL"))
        assertEquals("""{"id":1}""", response.getJSONObject("content").getString("text"))
        assertEquals(120, entry.getInt("time"))
        assertEquals("Mocked by NetPilot rule \"create item\"", entry.getString("comment"))
    }

    @Test
    fun `GET without body has no postData and failed calls keep the error`() {
        val transaction = transaction().copy(error = "java.net.UnknownHostException: offline")

        val entry = JSONObject(HarFormatter.format(listOf(transaction)))
            .getJSONObject("log").getJSONArray("entries").getJSONObject(0)

        assertFalse(entry.getJSONObject("request").has("postData"))
        assertEquals(0, entry.getJSONObject("response").getInt("status"))
        assertEquals("java.net.UnknownHostException: offline", entry.getJSONObject("response").getString("statusText"))
    }

    @Test
    fun `escapes quotes, backslashes and control characters`() {
        val body = "line1\nline2\t\"quoted\" \\ \u0001 end"

        val text = JSONObject(HarFormatter.format(listOf(transaction(requestBody = body))))
            .getJSONObject("log").getJSONArray("entries").getJSONObject(0)
            .getJSONObject("request").getJSONObject("postData").getString("text")

        assertEquals(body, text)
    }

    private fun transaction(
        requestDate: Long = 0,
        method: String = "GET",
        url: String = "https://api.example.com/a",
        requestBody: String? = null,
    ) = HttpTransaction(
        requestDate = requestDate,
        method = method,
        url = url,
        host = "api.example.com",
        path = "/a",
        scheme = "https",
        requestBody = requestBody,
    )
}
