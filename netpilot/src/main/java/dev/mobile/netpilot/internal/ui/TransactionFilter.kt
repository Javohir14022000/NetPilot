package dev.mobile.netpilot.internal.ui

import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.data.TransactionSummary
import kotlin.math.roundToLong

private val SUCCESS_CODES = 200..299
private val REDIRECT_CODES = 300..399
private val CLIENT_ERROR_CODES = 400..499
private val SERVER_ERROR_CODES = 500..599
private const val FIRST_ERROR_CODE = 400

/** Quick filters shown as chips above the request list. */
internal enum class TransactionFilter(val label: String) {
    ALL("All"),
    SUCCESS("2xx"),
    REDIRECT("3xx"),
    CLIENT_ERROR("4xx"),
    SERVER_ERROR("5xx"),
    FAILED("Failed"),
    MOCKED("Mocked"),
    ;

    fun matches(summary: TransactionSummary): Boolean = when (this) {
        ALL -> true
        SUCCESS -> summary.responseCode in SUCCESS_CODES
        REDIRECT -> summary.responseCode in REDIRECT_CODES
        CLIENT_ERROR -> summary.responseCode in CLIENT_ERROR_CODES
        SERVER_ERROR -> summary.responseCode in SERVER_ERROR_CODES
        FAILED -> summary.status == TransactionStatus.FAILED
        MOCKED -> summary.mockRuleName != null
    }
}

/** Header numbers for the request list. Errors are 4xx, 5xx and failed calls. */
internal data class TrafficStats(val total: Int, val errors: Int, val averageMillis: Long?) {
    companion object {
        fun from(summaries: List<TransactionSummary>): TrafficStats {
            val errors = summaries.count {
                it.status == TransactionStatus.FAILED || (it.responseCode ?: 0) >= FIRST_ERROR_CODE
            }
            val durations = summaries.mapNotNull { it.tookMs }
            val average = if (durations.isEmpty()) null else durations.average().roundToLong()
            return TrafficStats(total = summaries.size, errors = errors, averageMillis = average)
        }
    }
}
