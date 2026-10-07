package dev.mobile.netpilot.internal.capture

import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.internal.data.HttpHeader
import okhttp3.Headers
import java.util.Locale

internal object HeaderRedactor {

    /** Copies [headers], replacing values of any header in [names] (case-insensitive). */
    fun redact(headers: Headers, names: Set<String>): List<HttpHeader> {
        val redacted = lowercase(names)
        return headers.map { (name, value) ->
            val isSensitive = name.lowercase(Locale.ROOT) in redacted
            HttpHeader(name, if (isSensitive) NetPilotConfig.REDACTED_VALUE else value)
        }
    }

    /** The real values of the headers [redact] hides, kept in memory for "Edit & retry". */
    fun originals(headers: Headers, names: Set<String>): List<HttpHeader> {
        val redacted = lowercase(names)
        return headers.filter { (name, _) -> name.lowercase(Locale.ROOT) in redacted }
            .map { (name, value) -> HttpHeader(name, value) }
    }

    private fun lowercase(names: Set<String>): Set<String> = names.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
}
