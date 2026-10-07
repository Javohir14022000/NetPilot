package dev.mobile.netpilot.internal.data

import okhttp3.Headers

/**
 * Stores headers as `Name: value` lines. OkHttp rejects newlines in header names and
 * values and ':' in names, so this format round-trips without escaping.
 */
internal object HeaderCodec {
    private const val SEPARATOR = ": "

    fun encode(headers: List<HttpHeader>): String =
        headers.joinToString("\n") { "${it.name}$SEPARATOR${it.value}" }

    /**
     * Parses headers typed by a person: one `Name: value` per line, blank lines ignored.
     * Returns `null` when any line is not a header OkHttp would accept.
     */
    fun parseUserInput(text: String): List<HttpHeader>? =
        text.lines().filter { it.isNotBlank() }.map { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return null
            val name = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            try {
                Headers.Builder().add(name, value)
            } catch (e: IllegalArgumentException) {
                return null
            }
            HttpHeader(name, value)
        }

    fun decode(encoded: String?): List<HttpHeader> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return encoded.lines().mapNotNull { line ->
            val index = line.indexOf(SEPARATOR)
            if (index <= 0) null else HttpHeader(line.substring(0, index), line.substring(index + SEPARATOR.length))
        }
    }
}
