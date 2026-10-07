package dev.mobile.netpilot.internal.capture

import dev.mobile.netpilot.internal.data.HttpHeader
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureHelpersTest {

    @Test
    fun `redacted header store evicts the least recently used entry`() {
        val store = RedactedHeaderStore(capacity = 2)
        store.put(1, listOf(HttpHeader("Authorization", "one")))
        store.put(2, listOf(HttpHeader("Authorization", "two")))
        store.get(1)

        store.put(3, listOf(HttpHeader("Authorization", "three")))

        assertEquals("one", store.get(1).single().value)
        assertTrue(store.get(2).isEmpty())
        assertEquals("three", store.get(3).single().value)
    }

    @Test
    fun `originals returns only sensitive headers with their real values`() {
        val headers = headersOf("authorization", "Bearer x", "Accept", "*/*", "Cookie", "a=1")

        val originals = HeaderRedactor.originals(headers, setOf("Authorization", "Cookie"))

        assertEquals(listOf(HttpHeader("authorization", "Bearer x"), HttpHeader("Cookie", "a=1")), originals)
    }

    @Test
    fun `complete bodies are told apart from capture notes`() {
        assertTrue(BodyCapture.isCompleteBody("""{"a":1}"""))
        assertTrue(BodyCapture.isCompleteBody("""[1,2,3]"""))
        assertTrue(BodyCapture.isCompleteBody(""))
        assertFalse(BodyCapture.isCompleteBody(null))
        assertFalse(BodyCapture.isCompleteBody("[binary body not shown]"))
        assertFalse(BodyCapture.isCompleteBody("[streaming body not captured]"))
        assertFalse(BodyCapture.isCompleteBody("[body of 900000 bytes exceeds capture limit]"))
        assertFalse(BodyCapture.isCompleteBody("[br-encoded body not shown]"))
        assertFalse(BodyCapture.isCompleteBody("abc\n\n[truncated: showing first 3 bytes]"))
    }
}
