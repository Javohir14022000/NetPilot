package dev.mobile.netpilot.internal

import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction

/** Plain-text export used by the share action. */
internal object TransactionText {

    /**
     * Each body is cut to this many characters: share intents travel through Binder,
     * which fails for payloads over ~1 MB.
     */
    const val MAX_BODY_CHARS = 50_000

    fun format(transaction: HttpTransaction): String = buildString {
        with(transaction) {
            appendLine("$method $url")
            appendLine("Date: ${Format.dateTime(requestDate)}")
            responseCode?.let { appendLine("Status: $it ${responseMessage.orEmpty()}".trimEnd()) }
            Format.duration(tookMs)?.let { appendLine("Duration: $it") }
            error?.let { appendLine("Error: $it") }
            appendSection("Request", requestHeaders, requestBody)
            if (responseCode != null) appendSection("Response", responseHeaders, responseBody)
        }
    }.trimEnd()

    private fun StringBuilder.appendSection(title: String, headers: List<HttpHeader>, body: String?) {
        appendLine()
        appendLine("---------- $title ----------")
        headers.forEach { appendLine("${it.name}: ${it.value}") }
        if (!body.isNullOrEmpty()) {
            appendLine()
            appendLine(body.take(MAX_BODY_CHARS))
            if (body.length > MAX_BODY_CHARS) appendLine("[cut to $MAX_BODY_CHARS characters for sharing]")
        }
    }
}
