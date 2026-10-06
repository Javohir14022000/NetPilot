package dev.mobile.netpilot

import android.content.Context
import android.content.Intent

/** No-op variant for release builds. */
object NetPilot {

    /** `true` in the real library, `false` in `netpilot-no-op`. */
    const val isOp: Boolean = false

    /** Returns an empty intent; check [isOp] before starting it. */
    fun getLaunchIntent(@Suppress("UNUSED_PARAMETER") context: Context): Intent = Intent()

    fun clear(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
}
