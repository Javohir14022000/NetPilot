package dev.mobile.netpilot.internal.data

internal data class HttpHeader(val name: String, val value: String)

internal enum class TransactionStatus { IN_PROGRESS, COMPLETE, FAILED }

/** One captured request/response pair. Bodies are already decoded, redacted and truncated. */
internal data class HttpTransaction(
    val id: Long = 0,
    val requestDate: Long,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val scheme: String,
    val requestHeaders: List<HttpHeader> = emptyList(),
    val requestContentType: String? = null,
    val requestSize: Long = 0,
    val requestBody: String? = null,
    val protocol: String? = null,
    val responseDate: Long? = null,
    val responseCode: Int? = null,
    val responseMessage: String? = null,
    val responseHeaders: List<HttpHeader> = emptyList(),
    val responseContentType: String? = null,
    val responseSize: Long? = null,
    val responseBody: String? = null,
    val tookMs: Long? = null,
    val error: String? = null,
) {
    val status: TransactionStatus
        get() = transactionStatus(error, responseCode)
}

/** Lightweight row for the list screen; never carries bodies. */
internal data class TransactionSummary(
    val id: Long,
    val requestDate: Long,
    val method: String,
    val host: String,
    val path: String,
    val responseCode: Int?,
    val responseSize: Long?,
    val tookMs: Long?,
    val error: String?,
) {
    val status: TransactionStatus
        get() = transactionStatus(error, responseCode)
}

private fun transactionStatus(error: String?, responseCode: Int?): TransactionStatus = when {
    error != null -> TransactionStatus.FAILED
    responseCode == null -> TransactionStatus.IN_PROGRESS
    else -> TransactionStatus.COMPLETE
}
