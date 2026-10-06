package dev.mobile.netpilot.internal.notification

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.ui.NetPilotActivity

/** Adds a "NetPilot" entry to the long-press menu of the host app's launcher icon. */
internal object LauncherShortcut {
    private const val TAG = "NetPilot"
    private const val SHORTCUT_ID = "netpilot_inspector"

    fun publish(context: Context) {
        val label = context.getString(R.string.netpilot_shortcut_label)
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.drawable.netpilot_ic_notification))
            .setIntent(NetPilotActivity.intent(context).setAction(Intent.ACTION_VIEW))
            .build()
        try {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (e: RuntimeException) {
            // Some launchers reject or rate-limit shortcuts; NetPilot still works without one.
            Log.w(TAG, "Could not publish NetPilot launcher shortcut", e)
        }
    }
}
