package dev.mobile.netpilot

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

/** No-op variant for release builds: passes every request through untouched. */
class NetPilotInterceptor @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") config: NetPilotConfig = NetPilotConfig(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
