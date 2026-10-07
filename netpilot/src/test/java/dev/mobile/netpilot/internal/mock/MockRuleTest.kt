package dev.mobile.netpilot.internal.mock

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRuleTest {

    private val url = "https://api.example.com/posts/1?page=2".toHttpUrl()

    @Test
    fun `glob with scheme must match the whole URL`() {
        assertTrue(rule("https://api.example.com/posts/*").matches("GET", url))
        assertFalse(rule("https://api.example.com/posts").matches("GET", url))
    }

    @Test
    fun `glob without scheme matches the end of the URL`() {
        assertTrue(rule("/posts/1").matches("GET", url))
        assertTrue(rule("api.example.com/posts/*").matches("GET", url))
        assertFalse(rule("/posts").matches("GET", url))
    }

    @Test
    fun `glob ignores the query unless it contains a question mark`() {
        assertTrue(rule("*/posts/1").matches("GET", url))
        assertTrue(rule("*/posts/1?page=*").matches("GET", url))
        assertFalse(rule("*/posts/1?page=3").matches("GET", url))
    }

    @Test
    fun `glob treats regex characters literally and ignores case`() {
        assertTrue(rule("API.EXAMPLE.COM/posts/*").matches("GET", url))
        assertFalse(rule("api-example-com/posts/*").matches("GET", url))
    }

    @Test
    fun `regex matches anywhere in the URL`() {
        assertTrue(rule("""posts/\d+""", isRegex = true).matches("GET", url))
        assertFalse(rule("""users/\d+""", isRegex = true).matches("GET", url))
    }

    @Test
    fun `invalid regex never matches`() {
        assertFalse(rule("posts/(", isRegex = true).matches("GET", url))
    }

    @Test
    fun `method is matched case-insensitively and null means any`() {
        assertTrue(rule("*", method = "get").matches("GET", url))
        assertFalse(rule("*", method = "POST").matches("GET", url))
        assertTrue(rule("*", method = null).matches("DELETE", url))
    }

    @Test
    fun `firstMatch skips disabled rules and keeps list order`() {
        val request = Request.Builder().url(url).build()
        val disabled = rule("*", name = "disabled").copy(isEnabled = false)
        val first = rule("*/posts/*", name = "first")
        val second = rule("*", name = "second")

        assertEquals("first", listOf(disabled, first, second).firstMatch(request)?.name)
        assertNull(listOf(disabled).firstMatch(request))
    }

    private fun rule(pattern: String, isRegex: Boolean = false, method: String? = null, name: String = "rule") =
        MockRule(name = name, urlPattern = pattern, isRegex = isRegex, method = method)
}
