package dev.mobile.netpilot.internal.mock

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Produces the response (or failure) for a matched [MockRule] without touching the network. */
internal object MockResponder {

    /** Added to every mocked response so the app's own logs can tell it apart. */
    const val MOCK_HEADER = "X-NetPilot-Mock"

    private const val CONTENT_TYPE = "Content-Type"
    private const val SLEEP_STEP_MS = 50L

    fun respond(chain: Interceptor.Chain, rule: MockRule): Response {
        waitFor(chain, rule.delayMillis)
        return when (rule.outcome) {
            MockOutcome.NO_INTERNET ->
                throw UnknownHostException("NetPilot mock \"${rule.name}\": simulated no internet connection")
            MockOutcome.TIMEOUT ->
                throw SocketTimeoutException("NetPilot mock \"${rule.name}\": simulated timeout")
            MockOutcome.RESPOND -> buildResponse(chain.request(), rule)
        }
    }

    /** Sleeps in small steps so a cancelled call stops waiting promptly. */
    private fun waitFor(chain: Interceptor.Chain, delayMillis: Long) {
        var remaining = delayMillis
        while (remaining > 0) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            val step = minOf(remaining, SLEEP_STEP_MS)
            try {
                Thread.sleep(step)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("NetPilot mock delay interrupted")
            }
            remaining -= step
        }
        if (chain.call().isCanceled()) throw IOException("Canceled")
    }

    private fun buildResponse(request: Request, rule: MockRule): Response {
        // Unsafe variants: a non-ASCII rule name or header must not crash the app's call.
        val headers = Headers.Builder().apply {
            rule.headers.forEach { addUnsafeNonAscii(it.name, it.value) }
            rule.contentType?.let { removeAll(CONTENT_TYPE).addUnsafeNonAscii(CONTENT_TYPE, it) }
            addUnsafeNonAscii(MOCK_HEADER, rule.name)
        }.build()
        // Encode the bytes ourselves: String.toResponseBody() would append "; charset=utf-8"
        // and the app would see a different Content-Type than the rule specifies.
        val mediaType = rule.contentType?.toMediaTypeOrNull()
        val body = rule.body.toByteArray(mediaType?.charset() ?: Charsets.UTF_8).toResponseBody(mediaType)
        val now = System.currentTimeMillis()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(rule.statusCode)
            .message(HttpStatus.reasonPhrase(rule.statusCode))
            .headers(headers)
            .body(body)
            .sentRequestAtMillis(now)
            .receivedResponseAtMillis(now)
            .build()
    }
}

internal object HttpStatus {
    private val REASON_PHRASES = mapOf(
        200 to "OK",
        201 to "Created",
        202 to "Accepted",
        204 to "No Content",
        301 to "Moved Permanently",
        302 to "Found",
        304 to "Not Modified",
        400 to "Bad Request",
        401 to "Unauthorized",
        403 to "Forbidden",
        404 to "Not Found",
        409 to "Conflict",
        422 to "Unprocessable Entity",
        429 to "Too Many Requests",
        500 to "Internal Server Error",
        502 to "Bad Gateway",
        503 to "Service Unavailable",
        504 to "Gateway Timeout",
    )

    fun reasonPhrase(code: Int): String = REASON_PHRASES[code].orEmpty()
}
