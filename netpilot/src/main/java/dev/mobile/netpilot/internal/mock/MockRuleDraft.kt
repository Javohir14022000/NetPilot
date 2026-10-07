package dev.mobile.netpilot.internal.mock

import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import okhttp3.Headers

internal enum class DraftField { URL_PATTERN, STATUS_CODE, HEADERS, DELAY, PRIORITY }

internal sealed interface DraftResult {
    data class Valid(val rule: MockRule) : DraftResult
    data class Invalid(val errors: Map<DraftField, String>) : DraftResult
}

/** Editable form state of a mock rule; every numeric field is kept as typed text. */
internal data class MockRuleDraft(
    val id: Long = 0,
    val name: String = "",
    val method: String? = null,
    val urlPattern: String = "",
    val isRegex: Boolean = false,
    val outcome: MockOutcome = MockOutcome.RESPOND,
    val statusCode: String = MockRule.DEFAULT_STATUS_CODE.toString(),
    val contentType: String = MockRule.DEFAULT_CONTENT_TYPE,
    val headers: String = "",
    val body: String = "",
    val delayMillis: String = "0",
    val priority: String = "0",
    val isEnabled: Boolean = true,
) {

    fun validate(): DraftResult {
        val pattern = urlPattern.trim()
        val status = statusCode.trim().toIntOrNull()
        val delay = delayMillis.trim().ifEmpty { "0" }.toLongOrNull()
        val parsedPriority = priority.trim().ifEmpty { "0" }.toIntOrNull()
        val parsedHeaders = parseHeaders(headers)

        val errors = buildMap<DraftField, String> {
            when {
                pattern.isEmpty() -> put(DraftField.URL_PATTERN, "URL pattern is required")
                UrlPattern.compile(pattern, isRegex) == null -> put(DraftField.URL_PATTERN, "Invalid regular expression")
            }
            if (outcome == MockOutcome.RESPOND && (status == null || status !in STATUS_RANGE)) {
                put(DraftField.STATUS_CODE, "Status code must be between 100 and 599")
            }
            if (delay == null || delay !in 0..MAX_DELAY_MILLIS) {
                put(DraftField.DELAY, "Delay must be between 0 and $MAX_DELAY_MILLIS ms")
            }
            if (parsedPriority == null) put(DraftField.PRIORITY, "Priority must be a whole number")
            if (parsedHeaders == null) put(DraftField.HEADERS, "Use one \"Name: value\" header per line")
        }
        if (errors.isNotEmpty()) return DraftResult.Invalid(errors)

        return DraftResult.Valid(
            MockRule(
                id = id,
                name = name.trim().ifEmpty { "${method ?: ANY_METHOD} $pattern" },
                method = method,
                urlPattern = pattern,
                isRegex = isRegex,
                outcome = outcome,
                statusCode = status ?: MockRule.DEFAULT_STATUS_CODE,
                contentType = contentType.trim().ifEmpty { null },
                headers = parsedHeaders.orEmpty(),
                body = body,
                delayMillis = delay ?: 0,
                priority = parsedPriority ?: 0,
                isEnabled = isEnabled,
            ),
        )
    }

    companion object {
        const val ANY_METHOD = "ANY"
        const val MAX_DELAY_MILLIS = 60_000L
        val METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
        private val STATUS_RANGE = 100..599

        fun fromRule(rule: MockRule) = MockRuleDraft(
            id = rule.id,
            name = rule.name,
            method = rule.method,
            urlPattern = rule.urlPattern,
            isRegex = rule.isRegex,
            outcome = rule.outcome,
            statusCode = rule.statusCode.toString(),
            contentType = rule.contentType.orEmpty(),
            headers = rule.headers.joinToString("\n") { "${it.name}: ${it.value}" },
            body = rule.body,
            delayMillis = rule.delayMillis.toString(),
            priority = rule.priority.toString(),
            isEnabled = rule.isEnabled,
        )

        /** Prefills a rule that replays a captured response for the same URL (any query). */
        fun fromTransaction(transaction: HttpTransaction) = MockRuleDraft(
            name = "${transaction.method} ${transaction.path.substringBefore('?')}",
            method = transaction.method,
            urlPattern = transaction.url.substringBefore('?'),
            statusCode = (transaction.responseCode ?: MockRule.DEFAULT_STATUS_CODE).toString(),
            contentType = transaction.responseContentType ?: MockRule.DEFAULT_CONTENT_TYPE,
            body = transaction.responseBody.orEmpty(),
        )

        /** Returns `null` when any non-blank line is not a valid `Name: value` header. */
        private fun parseHeaders(text: String): List<HttpHeader>? =
            text.lines().filter { it.isNotBlank() }.map { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return null
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                try {
                    Headers.Builder().add(name, value)
                } catch (e: IllegalArgumentException) {
                    return null
                }
                HttpHeader(name, value)
            }
    }
}
