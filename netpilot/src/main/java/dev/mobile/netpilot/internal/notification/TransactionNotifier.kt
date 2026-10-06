package dev.mobile.netpilot.internal.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.Format
import dev.mobile.netpilot.internal.TransactionListener
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.ui.NetPilotActivity

/**
 * Shows one notification listing the latest requests. Updates are coalesced because
 * Android drops notifications that are posted too often.
 */
internal class TransactionNotifier(private val context: Context) : TransactionListener {

    private data class Line(val id: Long, val text: String)

    private val manager = NotificationManagerCompat.from(context)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var lines: List<Line> = emptyList()
    private var requestCount = 0
    private var isUpdateScheduled = false

    override fun onTransactionUpdated(transaction: HttpTransaction) {
        val line = Line(transaction.id, lineFor(transaction))
        synchronized(lock) {
            lines = (listOf(line) + lines.filterNot { it.id == line.id }).take(MAX_LINES)
            if (transaction.status == TransactionStatus.IN_PROGRESS) requestCount++
            if (isUpdateScheduled) return
            isUpdateScheduled = true
        }
        handler.postDelayed(::show, UPDATE_DELAY_MS)
    }

    fun clear() {
        synchronized(lock) {
            lines = emptyList()
            requestCount = 0
        }
        manager.cancel(NOTIFICATION_ID)
    }

    // areNotificationsEnabled() is false when POST_NOTIFICATIONS is not granted on API 33+.
    @SuppressLint("MissingPermission")
    private fun show() {
        val (snapshot, count) = synchronized(lock) {
            isUpdateScheduled = false
            lines to requestCount
        }
        if (snapshot.isEmpty() || !manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.netpilot_notification_channel))
                .build(),
        )
        val style = NotificationCompat.InboxStyle().also { inbox -> snapshot.forEach { inbox.addLine(it.text) } }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.netpilot_ic_notification)
            .setContentTitle(context.getString(R.string.netpilot_notification_title, count))
            .setContentText(snapshot.first().text)
            .setStyle(style)
            .setContentIntent(openInspectorIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked; skipping NetPilot notification", e)
        }
    }

    private fun openInspectorIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        NetPilotActivity.intent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun lineFor(transaction: HttpTransaction): String =
        "${Format.statusLabel(transaction.responseCode, transaction.status)}  ${transaction.method} ${transaction.path}"

    private companion object {
        const val TAG = "NetPilot"
        const val CHANNEL_ID = "netpilot_transactions"
        const val NOTIFICATION_ID = 0x4E50 // "NP"
        const val MAX_LINES = 5
        const val UPDATE_DELAY_MS = 500L
    }
}
