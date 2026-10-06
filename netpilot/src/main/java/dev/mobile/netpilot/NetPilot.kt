package dev.mobile.netpilot

import android.content.Context
import android.content.Intent
import dev.mobile.netpilot.internal.NetPilotComponents
import dev.mobile.netpilot.internal.ui.NetPilotActivity

/** Entry points for opening and managing NetPilot from the host app. */
object NetPilot {

    /** `true` in the real library, `false` in `netpilot-no-op`. */
    const val isOp: Boolean = true

    /** Intent that opens the NetPilot inspector. */
    fun getLaunchIntent(context: Context): Intent = NetPilotActivity.intent(context)

    /** Deletes every captured request and dismisses the notification. */
    fun clear(context: Context) {
        NetPilotComponents.clear(context)
    }
}
