package dev.mobile.netpilot.internal.export

import java.util.Locale

/**
 * Minimal JSON writer for exports. Values are passed as already-encoded JSON fragments.
 * org.json is avoided so the exporters run in plain JVM unit tests.
 */
internal object Json {
    private const val FIRST_PRINTABLE = ' '

    fun string(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (char < FIRST_PRINTABLE) append(String.format(Locale.ROOT, "\\u%04x", char.code)) else append(char)
            }
        }
        append('"')
    }

    /** `null` fields are skipped, which keeps optional HAR members out of the output. */
    fun obj(vararg fields: Pair<String, String>?): String =
        fields.filterNotNull().joinToString(",", "{", "}") { (key, value) -> "${string(key)}:$value" }

    fun array(items: List<String>): String = items.joinToString(",", "[", "]")
}
