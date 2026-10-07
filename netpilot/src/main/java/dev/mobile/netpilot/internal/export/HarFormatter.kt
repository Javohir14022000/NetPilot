package dev.mobile.netpilot.internal.export

import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** HTTP Archive 1.2 export, readable by Chrome DevTools, Charles, Proxyman and others. */
internal object HarFormatter {
    private const val HAR_VERSION = "1.2"
    private const val CREATOR_NAME = "NetPilot"
    const val CREATOR_VERSION = "0.3.0"
    private const val UNKNOWN_SIZE = "-1"
    private const val DEFAULT_HTTP_VERSION = "HTTP/1.1"

    fun format(transactions: List<HttpTransaction>): String = Json.obj(
        "log" to Json.obj(
            "version" to Json.string(HAR_VERSION),
            "creator" to Json.obj(
                "name" to Json.string(CREATOR_NAME),
                "version" to Json.string(CREATOR_VERSION),
            ),
            "entries" to Json.array(transactions.sortedBy { it.requestDate }.map { entry(it) }),
        ),
    )

    private fun entry(transaction: HttpTransaction): String {
        val httpVersion = httpVersion(transaction.protocol)
        val time = (transaction.tookMs ?: 0).toString()
        return Json.obj(
            "startedDateTime" to Json.string(isoDate(transaction.requestDate)),
            "time" to time,
            "request" to request(transaction, httpVersion),
            "response" to response(transaction, httpVersion),
            "cache" to "{}",
            "timings" to Json.obj("send" to "0", "wait" to time, "receive" to "0"),
            transaction.mockRuleName?.let { "comment" to Json.string("Mocked by NetPilot rule \"$it\"") },
        )
    }

    private fun request(transaction: HttpTransaction, httpVersion: String): String = Json.obj(
        "method" to Json.string(transaction.method),
        "url" to Json.string(transaction.url),
        "httpVersion" to Json.string(httpVersion),
        "cookies" to "[]",
        "headers" to headers(transaction.requestHeaders),
        "queryString" to queryString(transaction.url),
        transaction.requestBody?.let { body ->
            "postData" to Json.obj(
                "mimeType" to Json.string(transaction.requestContentType.orEmpty()),
                "text" to Json.string(body),
            )
        },
        "headersSize" to UNKNOWN_SIZE,
        "bodySize" to transaction.requestSize.toString(),
    )

    private fun response(transaction: HttpTransaction, httpVersion: String): String = Json.obj(
        "status" to (transaction.responseCode ?: 0).toString(),
        "statusText" to Json.string(transaction.responseMessage ?: transaction.error.orEmpty()),
        "httpVersion" to Json.string(httpVersion),
        "cookies" to "[]",
        "headers" to headers(transaction.responseHeaders),
        "content" to Json.obj(
            "size" to (transaction.responseSize ?: 0).toString(),
            "mimeType" to Json.string(transaction.responseContentType.orEmpty()),
            "text" to Json.string(transaction.responseBody.orEmpty()),
        ),
        "redirectURL" to Json.string(
            transaction.responseHeaders.firstOrNull { it.name.equals("Location", ignoreCase = true) }?.value.orEmpty(),
        ),
        "headersSize" to UNKNOWN_SIZE,
        "bodySize" to (transaction.responseSize?.toString() ?: UNKNOWN_SIZE),
    )

    private fun headers(headers: List<HttpHeader>): String =
        Json.array(headers.map { Json.obj("name" to Json.string(it.name), "value" to Json.string(it.value)) })

    private fun queryString(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return "[]"
        return Json.array(
            (0 until parsed.querySize).map { index ->
                Json.obj(
                    "name" to Json.string(parsed.queryParameterName(index)),
                    "value" to Json.string(parsed.queryParameterValue(index).orEmpty()),
                )
            },
        )
    }

    /** OkHttp protocol names ("http/1.1", "h2") to HAR's "HTTP/1.1" style. */
    private fun httpVersion(protocol: String?): String = when (protocol) {
        null -> DEFAULT_HTTP_VERSION
        "h2", "h2_prior_knowledge" -> "HTTP/2"
        else -> protocol.uppercase(Locale.ROOT)
    }

    // SimpleDateFormat is not thread-safe, so a new instance is created per call.
    private fun isoDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMillis))
}
