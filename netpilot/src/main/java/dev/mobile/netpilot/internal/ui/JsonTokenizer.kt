package dev.mobile.netpilot.internal.ui

internal enum class JsonToken { KEY, STRING, NUMBER, LITERAL, PUNCTUATION }

internal data class JsonSpan(val start: Int, val end: Int, val token: JsonToken)

/**
 * Display-only JSON lexer for syntax highlighting. It never throws and tolerates truncated or
 * invalid input, since bodies may be cut at the capture limit.
 */
internal object JsonTokenizer {
    private const val PUNCTUATION = "{}[],:"
    private const val NUMBER_CHARS = ".eE+-"
    private val LITERALS = listOf("true", "false", "null")

    fun tokenize(text: String): List<JsonSpan> {
        val spans = mutableListOf<JsonSpan>()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val literal = LITERALS.firstOrNull { text.startsWith(it, index) }
            index = when {
                char == '"' -> {
                    val end = stringEnd(text, index)
                    val token = if (nextNonSpace(text, end) == ':') JsonToken.KEY else JsonToken.STRING
                    spans += JsonSpan(index, end, token)
                    end
                }
                char == '-' || char.isDigit() -> {
                    var end = index + 1
                    while (end < text.length && (text[end].isDigit() || text[end] in NUMBER_CHARS)) end++
                    spans += JsonSpan(index, end, JsonToken.NUMBER)
                    end
                }
                literal != null -> {
                    spans += JsonSpan(index, index + literal.length, JsonToken.LITERAL)
                    index + literal.length
                }
                char in PUNCTUATION -> {
                    spans += JsonSpan(index, index + 1, JsonToken.PUNCTUATION)
                    index + 1
                }
                else -> index + 1
            }
        }
        return spans
    }

    /** End (exclusive) of the string starting at [start]; stops at a line break if unterminated. */
    private fun stringEnd(text: String, start: Int): Int {
        var index = start + 1
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index += 2
                '"' -> return index + 1
                '\n' -> return index
                else -> index++
            }
        }
        return text.length
    }

    private fun nextNonSpace(text: String, from: Int): Char? {
        var index = from
        while (index < text.length && text[index].isWhitespace()) index++
        return text.getOrNull(index)
    }
}
