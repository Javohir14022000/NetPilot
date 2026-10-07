package dev.mobile.netpilot.internal.mock

import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRuleDraftTest {

    @Test
    fun `valid draft becomes a rule with parsed fields`() {
        val draft = MockRuleDraft(
            method = "POST",
            urlPattern = "  */login ",
            statusCode = "401",
            headers = "X-Error: expired\n\nRetry-After: 30",
            body = """{"error":"expired"}""",
            delayMillis = "250",
            priority = "5",
        )

        val rule = (draft.validate() as DraftResult.Valid).rule

        assertEquals("POST */login", rule.name)
        assertEquals("*/login", rule.urlPattern)
        assertEquals(401, rule.statusCode)
        assertEquals(listOf(HttpHeader("X-Error", "expired"), HttpHeader("Retry-After", "30")), rule.headers)
        assertEquals(250L, rule.delayMillis)
        assertEquals(5, rule.priority)
    }

    @Test
    fun `reports every invalid field`() {
        val draft = MockRuleDraft(
            urlPattern = "",
            statusCode = "99",
            headers = "no separator",
            delayMillis = "-1",
            priority = "high",
        )

        val errors = (draft.validate() as DraftResult.Invalid).errors

        assertEquals(DraftField.entries.toSet(), errors.keys)
    }

    @Test
    fun `rejects invalid regex`() {
        val errors = (MockRuleDraft(urlPattern = "posts/(", isRegex = true).validate() as DraftResult.Invalid).errors

        assertTrue(DraftField.URL_PATTERN in errors)
    }

    @Test
    fun `status code is not required for failure outcomes`() {
        val result = MockRuleDraft(urlPattern = "/x", outcome = MockOutcome.NO_INTERNET, statusCode = "").validate()

        assertTrue(result is DraftResult.Valid)
    }

    @Test
    fun `round-trips through fromRule`() {
        val rule = MockRule(
            id = 7,
            name = "rule",
            method = "GET",
            urlPattern = "/a",
            headers = listOf(HttpHeader("X-A", "1")),
            delayMillis = 100,
            priority = 2,
        )

        assertEquals(rule, (MockRuleDraft.fromRule(rule).validate() as DraftResult.Valid).rule)
    }

    @Test
    fun `fromTransaction replays the captured response for the same URL`() {
        val transaction = HttpTransaction(
            requestDate = 0,
            method = "GET",
            url = "https://api.example.com/posts/1?page=2",
            host = "api.example.com",
            path = "/posts/1?page=2",
            scheme = "https",
            responseCode = 404,
            responseContentType = "application/json",
            responseBody = """{"error":"missing"}""",
        )

        val draft = MockRuleDraft.fromTransaction(transaction)

        assertEquals("GET /posts/1", draft.name)
        assertEquals("https://api.example.com/posts/1", draft.urlPattern)
        assertEquals("404", draft.statusCode)
        assertEquals("""{"error":"missing"}""", draft.body)
    }
}
