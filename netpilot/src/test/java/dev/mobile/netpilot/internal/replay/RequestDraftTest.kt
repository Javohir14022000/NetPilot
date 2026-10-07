package dev.mobile.netpilot.internal.replay

import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestDraftTest {

    private val redacted = NetPilotConfig.REDACTED_VALUE

    @Test
    fun `fromTransaction prefills method, url, headers and a complete body`() {
        val draft = RequestDraft.fromTransaction(
            transaction(body = """{"a":1}""", headers = listOf(HttpHeader("Accept", "application/json"))),
        )

        assertEquals("POST", draft.method)
        assertEquals("https://api.example.com/items", draft.url)
        assertEquals("Accept: application/json", draft.headers)
        assertEquals("""{"a":1}""", draft.body)
        assertEquals(false, draft.isOriginalBodyMissing)
    }

    @Test
    fun `fromTransaction leaves out bodies that were not fully captured`() {
        val draft = RequestDraft.fromTransaction(transaction(body = "abc\n\n[truncated: showing first 3 bytes]"))

        assertEquals("", draft.body)
        assertTrue(draft.isOriginalBodyMissing)
    }

    @Test
    fun `restores redacted headers from memory, in order`() {
        val draft = draft(headers = "Authorization: $redacted\nCookie: $redacted\nCookie: $redacted\nAccept: */*")
        val secrets = listOf(
            HttpHeader("Authorization", "Bearer real"),
            HttpHeader("Cookie", "a=1"),
            HttpHeader("Cookie", "b=2"),
        )

        val request = (draft.toRequest(secrets) as RequestDraftResult.Valid).request

        assertEquals("Bearer real", request.header("Authorization"))
        assertEquals(listOf("a=1", "b=2"), request.headers("Cookie"))
        assertEquals("*/*", request.header("Accept"))
    }

    @Test
    fun `reports redacted headers that are no longer in memory`() {
        val errors = (draft(headers = "Authorization: $redacted").toRequest(emptyList()) as RequestDraftResult.Invalid).errors

        assertTrue(errors.getValue(RequestField.HEADERS).contains("no longer in memory"))
    }

    @Test
    fun `validates url and body for the chosen method`() {
        val errors = (draft(method = "GET", url = "not a url", body = "x").toRequest(emptyList()) as RequestDraftResult.Invalid).errors

        assertEquals(setOf(RequestField.URL, RequestField.BODY), errors.keys)
    }

    @Test
    fun `builds a body with the exact Content-Type from the headers`() {
        val draft = draft(method = "post", headers = "Content-Type: application/json", body = """{"a":1}""")

        val request = (draft.toRequest(emptyList()) as RequestDraftResult.Valid).request

        assertEquals("POST", request.method)
        assertEquals("application/json", request.body!!.contentType().toString())
        assertEquals("""{"a":1}""", Buffer().also { request.body!!.writeTo(it) }.readUtf8())
    }

    @Test
    fun `POST with an empty body is still allowed`() {
        val request = (draft(method = "POST", body = "").toRequest(emptyList()) as RequestDraftResult.Valid).request

        assertEquals(0L, request.body!!.contentLength())
    }

    private fun draft(
        method: String = "GET",
        url: String = "https://api.example.com/items",
        headers: String = "",
        body: String = "",
    ) = RequestDraft(method = method, url = url, headers = headers, body = body)

    private fun transaction(body: String?, headers: List<HttpHeader> = emptyList()) = HttpTransaction(
        requestDate = 0,
        method = "POST",
        url = "https://api.example.com/items",
        host = "api.example.com",
        path = "/items",
        scheme = "https",
        requestHeaders = headers,
        requestBody = body,
    )
}
