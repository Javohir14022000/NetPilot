package dev.mobile.netpilot.internal.replay

import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.internal.HttpMethods
import dev.mobile.netpilot.internal.capture.BodyCapture
import dev.mobile.netpilot.internal.data.HeaderCodec
import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

internal enum class RequestField { METHOD, URL, HEADERS, BODY }

internal sealed interface RequestDraftResult {
    data class Valid(val request: Request) : RequestDraftResult
    data class Invalid(val errors: Map<RequestField, String>) : RequestDraftResult
}

/** Editable copy of a captured request for "Edit & retry". */
internal data class RequestDraft(
    val method: String,
    val url: String,
    val headers: String,
    val body: String,
    /** The original body was binary, streamed or truncated, so it could not be prefilled. */
    val isOriginalBodyMissing: Boolean = false,
) {

    /** Builds the request, putting back real values for headers still shown as redacted. */
    fun toRequest(secrets: List<HttpHeader>): RequestDraftResult {
        val normalizedMethod = method.trim().uppercase(Locale.ROOT)
        val parsedUrl = url.trim().toHttpUrlOrNull()
        val restoredHeaders = restoreSecrets(headers, secrets)
        val parsedHeaders = HeaderCodec.parseUserInput(restoredHeaders)

        val errors = buildMap<RequestField, String> {
            if (normalizedMethod !in HttpMethods.ALL) put(RequestField.METHOD, "Choose one of ${HttpMethods.ALL.joinToString()}")
            if (parsedUrl == null) put(RequestField.URL, "Enter a valid http(s) URL")
            when {
                NetPilotConfig.REDACTED_VALUE in restoredHeaders -> put(
                    RequestField.HEADERS,
                    "Replace ${NetPilotConfig.REDACTED_VALUE} with the real value; it is no longer in memory",
                )
                parsedHeaders == null -> put(RequestField.HEADERS, "Use one \"Name: value\" header per line")
            }
            if (body.isNotEmpty() && normalizedMethod in HttpMethods.WITHOUT_BODY) {
                put(RequestField.BODY, "$normalizedMethod requests cannot have a body")
            }
        }
        if (errors.isNotEmpty() || parsedUrl == null || parsedHeaders == null) {
            return RequestDraftResult.Invalid(errors)
        }

        val contentType = parsedHeaders.lastOrNull { it.name.equals(CONTENT_TYPE, ignoreCase = true) }
            ?.value?.toMediaTypeOrNull()
        val requestBody = when {
            normalizedMethod in HttpMethods.WITHOUT_BODY -> null
            body.isEmpty() && normalizedMethod !in HttpMethods.REQUIRING_BODY -> null
            else -> body.toByteArray(contentType?.charset() ?: Charsets.UTF_8).toRequestBody(contentType)
        }
        val request = Request.Builder()
            .url(parsedUrl)
            .apply { parsedHeaders.forEach { addHeader(it.name, it.value) } }
            .method(normalizedMethod, requestBody)
            .build()
        return RequestDraftResult.Valid(request)
    }

    companion object {
        private const val CONTENT_TYPE = "Content-Type"

        fun fromTransaction(transaction: HttpTransaction): RequestDraft {
            val originalBody = transaction.requestBody
            val isBodyComplete = originalBody == null || BodyCapture.isCompleteBody(originalBody)
            return RequestDraft(
                method = transaction.method,
                url = transaction.url,
                headers = HeaderCodec.encode(transaction.requestHeaders),
                body = if (isBodyComplete) originalBody.orEmpty() else "",
                isOriginalBodyMissing = !isBodyComplete,
            )
        }

        /**
         * Replaces `Name: ██` lines with the in-memory originals, matching repeated headers
         * (several `Cookie` lines, for example) in order.
         */
        private fun restoreSecrets(headers: String, secrets: List<HttpHeader>): String {
            val unused = secrets.toMutableList()
            return headers.lines().joinToString("\n") { line ->
                val separator = line.indexOf(':')
                val isRedacted = separator > 0 && line.substring(separator + 1).trim() == NetPilotConfig.REDACTED_VALUE
                if (!isRedacted) return@joinToString line
                val name = line.substring(0, separator).trim()
                val index = unused.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                if (index < 0) line else "$name: ${unused.removeAt(index).value}"
            }
        }
    }
}
