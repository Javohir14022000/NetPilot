package dev.mobile.netpilot.internal.mock

import dev.mobile.netpilot.internal.HttpMethods
import dev.mobile.netpilot.internal.data.HeaderCodec
import dev.mobile.netpilot.internal.data.HttpTransaction

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
        val parsedHeaders = HeaderCodec.parseUserInput(headers)

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
        val METHODS = HttpMethods.ALL
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
            headers = HeaderCodec.encode(rule.headers),
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
    }
}
