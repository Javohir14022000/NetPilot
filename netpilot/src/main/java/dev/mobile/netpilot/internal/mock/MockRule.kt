package dev.mobile.netpilot.internal.mock

import dev.mobile.netpilot.internal.data.HttpHeader
import okhttp3.HttpUrl
import okhttp3.Request

/** What a matching rule does instead of calling the network. */
internal enum class MockOutcome { RESPOND, NO_INTERNET, TIMEOUT }

internal data class MockRule(
    val id: Long = 0,
    val name: String,
    /** `null` matches any method. */
    val method: String? = null,
    val urlPattern: String,
    val isRegex: Boolean = false,
    val outcome: MockOutcome = MockOutcome.RESPOND,
    val statusCode: Int = DEFAULT_STATUS_CODE,
    val contentType: String? = DEFAULT_CONTENT_TYPE,
    val headers: List<HttpHeader> = emptyList(),
    val body: String = "",
    val delayMillis: Long = 0,
    /** Higher priority rules are checked first. */
    val priority: Int = 0,
    val isEnabled: Boolean = true,
) {
    private val compiledPattern: Regex? by lazy { UrlPattern.compile(urlPattern, isRegex) }

    fun matches(requestMethod: String, url: HttpUrl): Boolean {
        if (method != null && !method.equals(requestMethod, ignoreCase = true)) return false
        val regex = compiledPattern ?: return false
        return UrlPattern.matches(regex, urlPattern, isRegex, url.toString())
    }

    companion object {
        const val DEFAULT_STATUS_CODE = 200
        const val DEFAULT_CONTENT_TYPE = "application/json; charset=utf-8"
    }
}

/** Rules must already be sorted by priority; the first enabled match wins. */
internal fun List<MockRule>.firstMatch(request: Request): MockRule? =
    firstOrNull { it.isEnabled && it.matches(request.method, request.url) }

/**
 * URL matching for mock rules.
 *
 * Glob patterns use `*` as "anything" and must match the whole URL. A glob without
 * `://` gets an implicit leading wildcard, so `/posts/1` matches the end of the URL, and
 * a glob without `?` ignores the query string. Regex patterns match anywhere in the URL.
 */
internal object UrlPattern {
    private const val WILDCARD = "*"
    private const val SCHEME_SEPARATOR = "://"
    private const val QUERY_START = '?'

    fun compile(pattern: String, isRegex: Boolean): Regex? = try {
        Regex(if (isRegex) pattern else globToRegex(pattern), RegexOption.IGNORE_CASE)
    } catch (e: IllegalArgumentException) {
        // Invalid user regex: the rule simply never matches; the editor reports the error.
        null
    }

    fun matches(regex: Regex, pattern: String, isRegex: Boolean, url: String): Boolean {
        if (isRegex) return regex.containsMatchIn(url)
        val target = if (QUERY_START in pattern) url else url.substringBefore(QUERY_START)
        return regex.matches(target)
    }

    private fun globToRegex(glob: String): String {
        val anchored = if (SCHEME_SEPARATOR in glob || glob.startsWith(WILDCARD)) glob else WILDCARD + glob
        return anchored.split(WILDCARD).joinToString(".*") { Regex.escape(it) }
    }
}
