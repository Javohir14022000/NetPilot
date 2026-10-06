package dev.mobile.netpilot.internal.data

/**
 * Stores headers as `Name: value` lines. OkHttp rejects newlines in header names and
 * values and ':' in names, so this format round-trips without escaping.
 */
internal object HeaderCodec {
    private const val SEPARATOR = ": "

    fun encode(headers: List<HttpHeader>): String =
        headers.joinToString("\n") { "${it.name}$SEPARATOR${it.value}" }

    fun decode(encoded: String?): List<HttpHeader> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return encoded.lines().mapNotNull { line ->
            val index = line.indexOf(SEPARATOR)
            if (index <= 0) null else HttpHeader(line.substring(0, index), line.substring(index + SEPARATOR.length))
        }
    }
}
