package dev.mobile.netpilot.internal.capture

import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.internal.data.HttpHeader
import okhttp3.Headers
import java.util.Locale

internal object HeaderRedactor {

    /** Copies [headers], replacing values of any header in [names] (case-insensitive). */
    fun redact(headers: Headers, names: Set<String>): List<HttpHeader> {
        val redacted = names.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        return headers.map { (name, value) ->
            val isSensitive = name.lowercase(Locale.ROOT) in redacted
            HttpHeader(name, if (isSensitive) NetPilotConfig.REDACTED_VALUE else value)
        }
    }
}
