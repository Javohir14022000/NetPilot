package dev.mobile.netpilot.internal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTokenizerTest {

    @Test
    fun `classifies keys, strings, numbers, literals and punctuation`() {
        val text = """{"id": 42, "name": "Ann", "ok": true, "tags": null, "score": -1.5e3}"""

        val tokens = JsonTokenizer.tokenize(text).map { text.substring(it.start, it.end) to it.token }

        assertTrue(""""id"""" to JsonToken.KEY in tokens)
        assertTrue("42" to JsonToken.NUMBER in tokens)
        assertTrue(""""Ann"""" to JsonToken.STRING in tokens)
        assertTrue("true" to JsonToken.LITERAL in tokens)
        assertTrue("null" to JsonToken.LITERAL in tokens)
        assertTrue("-1.5e3" to JsonToken.NUMBER in tokens)
        assertEquals(JsonToken.PUNCTUATION, tokens.first().second)
    }

    @Test
    fun `escaped quotes stay inside the string`() {
        val text = """{"quote": "say \"hi\""}"""

        val strings = JsonTokenizer.tokenize(text).filter { it.token == JsonToken.STRING }

        assertEquals(listOf("\"say \\\"hi\\\"\""), strings.map { text.substring(it.start, it.end) })
    }

    @Test
    fun `truncated input does not throw and spans stay in bounds`() {
        val text = "{\"unterminated\": \"abc\n  \"next\": 1"

        val spans = JsonTokenizer.tokenize(text)

        assertTrue(spans.all { it.start in 0..text.length && it.end in it.start..text.length })
    }
}
