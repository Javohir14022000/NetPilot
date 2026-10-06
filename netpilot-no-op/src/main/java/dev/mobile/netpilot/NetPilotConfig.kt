package dev.mobile.netpilot

import java.util.concurrent.TimeUnit

/**
 * No-op copy of the real `NetPilotConfig`. Keep the constructor identical to the
 * `netpilot` module so release builds compile against the same API.
 */
data class NetPilotConfig(
    val maxContentLength: Long = DEFAULT_MAX_CONTENT_LENGTH,
    val redactHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
    val maxRecords: Int = DEFAULT_MAX_RECORDS,
    val retentionPeriodMillis: Long = DEFAULT_RETENTION_PERIOD_MILLIS,
    val isNotificationEnabled: Boolean = true,
    val isLauncherShortcutEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_MAX_CONTENT_LENGTH: Long = 250_000L
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
