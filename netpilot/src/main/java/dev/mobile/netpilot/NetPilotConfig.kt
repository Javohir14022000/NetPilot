package dev.mobile.netpilot

import java.util.concurrent.TimeUnit

/**
 * Settings for [NetPilotInterceptor].
 *
 * Keep this class in sync with the copy in the `netpilot-no-op` module so that
 * debug and release builds compile against the same API.
 */
data class NetPilotConfig(
    /** Bodies longer than this many bytes are truncated before they are stored. */
    val maxContentLength: Long = DEFAULT_MAX_CONTENT_LENGTH,
    /** Header names (case-insensitive) whose values are replaced before storage. */
    val redactHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
    /** Maximum number of requests kept on disk; older ones are pruned. */
    val maxRecords: Int = DEFAULT_MAX_RECORDS,
    /** Requests older than this are pruned. */
    val retentionPeriodMillis: Long = DEFAULT_RETENTION_PERIOD_MILLIS,
    /** Show a notification summarising recent requests. */
    val isNotificationEnabled: Boolean = true,
    /** Add a "NetPilot" dynamic shortcut to the host app's launcher icon. */
    val isLauncherShortcutEnabled: Boolean = true,
) {
    init {
        require(maxContentLength in 0..MAX_CONTENT_LENGTH_LIMIT) {
            "maxContentLength must be in 0..$MAX_CONTENT_LENGTH_LIMIT, was $maxContentLength"
        }
        require(maxRecords > 0) { "maxRecords must be positive, was $maxRecords" }
        require(retentionPeriodMillis > 0) {
            "retentionPeriodMillis must be positive, was $retentionPeriodMillis"
        }
    }

    companion object {
        const val DEFAULT_MAX_CONTENT_LENGTH: Long = 250_000L

        /**
         * Upper bound for [maxContentLength]. Both bodies of a request are stored in one
         * SQLite row, and Android's CursorWindow cannot read rows larger than 2 MB.
         */
        const val MAX_CONTENT_LENGTH_LIMIT: Long = 500_000L

        const val DEFAULT_MAX_RECORDS: Int = 500
        val DEFAULT_RETENTION_PERIOD_MILLIS: Long = TimeUnit.DAYS.toMillis(7)
        const val REDACTED_VALUE: String = "██"

        val DEFAULT_REDACTED_HEADERS: Set<String> = setOf(
            "Authorization",
            "Proxy-Authorization",
            "Cookie",
            "Set-Cookie",
        )
    }
}
