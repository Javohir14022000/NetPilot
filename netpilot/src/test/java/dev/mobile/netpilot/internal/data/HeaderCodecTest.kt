package dev.mobile.netpilot.internal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderCodecTest {

    @Test
    fun `round-trips headers including values that contain the separator`() {
        val headers = listOf(
            HttpHeader("Content-Type", "application/json"),
            HttpHeader("X-Trace", "a: b: c"),
            HttpHeader("Accept", "*/*"),
        )

        assertEquals(headers, HeaderCodec.decode(HeaderCodec.encode(headers)))
    }

    @Test
    fun `decodes null and empty input to an empty list`() {
        assertEquals(emptyList<HttpHeader>(), HeaderCodec.decode(null))
        assertEquals(emptyList<HttpHeader>(), HeaderCodec.decode(""))
    }
}
