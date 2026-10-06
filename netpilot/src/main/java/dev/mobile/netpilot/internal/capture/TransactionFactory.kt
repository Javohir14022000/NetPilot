package dev.mobile.netpilot.internal.capture

import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.internal.data.HttpTransaction
import okhttp3.Request
import okhttp3.Response

/** Builds immutable [HttpTransaction] snapshots from OkHttp objects. */
internal object TransactionFactory {

    fun fromRequest(request: Request, config: NetPilotConfig, now: Long): HttpTransaction {
        val url = request.url
        val body = BodyCapture.request(request, config.maxContentLength)
        return HttpTransaction(
            requestDate = now,
            method = request.method,
            url = url.toString(),
            host = url.host,
            path = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: ""),
            scheme = url.scheme,
            requestHeaders = HeaderRedactor.redact(request.headers, config.redactHeaders),
            requestContentType = request.body?.contentType()?.toString() ?: request.header("Content-Type"),
            requestSize = body.byteCount ?: 0,
            requestBody = body.text,
        )
    }

    fun withResponse(
        pending: HttpTransaction,
        response: Response,
        config: NetPilotConfig,
        now: Long,
        tookMs: Long,
    ): HttpTransaction {
        val body = BodyCapture.response(response, config.maxContentLength)
        return pending.copy(
            protocol = response.protocol.toString(),
            responseDate = now,
            responseCode = response.code,
            responseMessage = response.message,
            responseHeaders = HeaderRedactor.redact(response.headers, config.redactHeaders),
            responseContentType = response.body?.contentType()?.toString() ?: response.header("Content-Type"),
            responseSize = body.byteCount,
            responseBody = body.text,
            tookMs = tookMs,
        )
    }
}
