package dev.mobile.netpilot.internal.export

import dev.mobile.netpilot.internal.capture.BodyCapture
import dev.mobile.netpilot.internal.data.HttpTransaction

/**
 * Turns a captured request into a shell command. Redacted headers stay redacted: secrets are
 * never copied to the clipboard or shared.
 */
internal object CurlFormatter {
    private const val LINE_BREAK = " \\\n  "

    fun format(transaction: HttpTransaction): String = buildString {
        append("curl")
        if (transaction.method != "GET") append(" -X ").append(transaction.method)
        append(' ').append(quote(transaction.url))
        transaction.requestHeaders.forEach { header ->
            append(LINE_BREAK).append("-H ").append(quote("${header.name}: ${header.value}"))
        }
        val body = transaction.requestBody
        if (!body.isNullOrEmpty() && BodyCapture.isCompleteBody(body)) {
            append(LINE_BREAK).append("--data-raw ").append(quote(body))
        }
    }

    /** POSIX single quoting: close the quote, emit an escaped quote, reopen. */
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
