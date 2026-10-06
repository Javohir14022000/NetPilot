package dev.mobile.netpilot.internal

import dev.mobile.netpilot.internal.data.TransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Human-readable formatting shared by the UI, notification and share text. */
internal object Format {
    private const val KILO = 1_000.0
    private const val MEGA = 1_000_000.0
    private const val MILLIS_PER_SECOND = 1_000.0

    fun statusLabel(responseCode: Int?, status: TransactionStatus): String = when (status) {
        TransactionStatus.FAILED -> "ERR"
        TransactionStatus.IN_PROGRESS -> "…"
        TransactionStatus.COMPLETE -> responseCode.toString()
    }

    fun size(bytes: Long?): String? = when {
        bytes == null -> null
        bytes < KILO -> "$bytes B"
        bytes < MEGA -> String.format(Locale.US, "%.1f KB", bytes / KILO)
        else -> String.format(Locale.US, "%.1f MB", bytes / MEGA)
    }

    fun duration(millis: Long?): String? = when {
        millis == null -> null
        millis < MILLIS_PER_SECOND -> "$millis ms"
        else -> String.format(Locale.US, "%.2f s", millis / MILLIS_PER_SECOND)
    }

    // SimpleDateFormat is not thread-safe, so a new instance is created per call.
    fun time(epochMillis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMillis))

    fun dateTime(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(epochMillis))
}
