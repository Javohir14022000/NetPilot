package dev.mobile.netpilot.internal.capture

import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import java.io.EOFException
import java.io.IOException

/** Body text ready for display plus its size in bytes, when known. */
internal data class CapturedBody(val text: String?, val byteCount: Long?)

/**
 * Reads request and response bodies without consuming them, so the app still receives
 * the full stream. Binary, streaming and oversized bodies are replaced by a short note.
 */
internal object BodyCapture {

    private const val GZIP = "gzip"
    private const val IDENTITY = "identity"
    private const val GZIP_READ_CHUNK = 8_192L
    private const val TEXT_SNIFF_BYTES = 64L
    private const val TEXT_SNIFF_CODE_POINTS = 16
    private const val STREAMING_NOTE = "[streaming body not captured]"
    private const val BINARY_NOTE = "[binary body not shown]"

    /** Placeholder notes written instead of (or appended to) a body that was not fully captured. */
    private val INCOMPLETE_BODY_PATTERNS = listOf(
        Regex("^" + Regex.escape(STREAMING_NOTE) + "$"),
        Regex("^" + Regex.escape(BINARY_NOTE) + "$"),
        Regex("""^\[body of \d+ bytes exceeds capture limit]$"""),
        Regex("""^\[[^\]\n]+-encoded body not shown]$"""),
        Regex("""\n\n\[truncated: showing first \d+ bytes]$"""),
    )

    /** `false` when [text] is one of this object's placeholder notes or a truncated body. */
    fun isCompleteBody(text: String?): Boolean =
        text != null && INCOMPLETE_BODY_PATTERNS.none { it.containsMatchIn(text) }

    fun request(request: Request, maxLength: Long): CapturedBody {
        val body = request.body ?: return CapturedBody(null, 0)
        val contentLength = body.contentLength()
        val knownLength = contentLength.takeIf { it >= 0 }
        return when {
            body.isDuplex() || body.isOneShot() -> CapturedBody(STREAMING_NOTE, knownLength)
            maxLength == 0L -> CapturedBody(null, knownLength)
            // Avoid buffering large uploads (files, images) just to show a prefix.
            contentLength > maxLength -> CapturedBody("[body of $contentLength bytes exceeds capture limit]", contentLength)
            else -> {
                val buffer = Buffer().also(body::writeTo)
                val size = buffer.size
                CapturedBody(decode(buffer, request.header("Content-Encoding"), body.contentType(), maxLength, false), size)
            }
        }
    }

    fun response(response: Response, maxLength: Long): CapturedBody {
        val body = response.body ?: return CapturedBody(null, null)
        val knownLength = body.contentLength().takeIf { it >= 0 }
        if (!response.hasBody()) return CapturedBody(null, 0)
        if (body.contentType().isEventStream()) return CapturedBody(STREAMING_NOTE, knownLength)
        if (maxLength == 0L) return CapturedBody(null, knownLength)

        val raw = Buffer().apply { write(response.peekBody(maxLength + 1).bytes()) }
        val isRawTruncated = raw.size > maxLength
        val byteCount = knownLength ?: raw.size.takeUnless { isRawTruncated }
        val text = decode(raw, response.header("Content-Encoding"), body.contentType(), maxLength, isRawTruncated)
        return CapturedBody(text, byteCount)
    }

    private fun decode(
        raw: Buffer,
        contentEncoding: String?,
        contentType: MediaType?,
        maxLength: Long,
        isRawTruncated: Boolean,
    ): String {
        val decoded = when {
            contentEncoding == null || contentEncoding.equals(IDENTITY, ignoreCase = true) -> raw
            contentEncoding.equals(GZIP, ignoreCase = true) -> gunzip(raw, maxLength + 1)
            else -> return "[$contentEncoding-encoded body not shown]"
        }
        val isTruncated = isRawTruncated || decoded.size > maxLength
        val visible = Buffer().also { decoded.copyTo(it, 0, minOf(decoded.size, maxLength)) }
        if (!visible.isProbablyText()) return BINARY_NOTE

        val charset = contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val text = visible.readString(charset)
        return if (isTruncated) "$text\n\n[truncated: showing first $maxLength bytes]" else text
    }

    private fun gunzip(compressed: Buffer, limit: Long): Buffer {
        val out = Buffer()
        try {
            GzipSource(compressed).use { source ->
                while (out.size < limit && source.read(out, GZIP_READ_CHUNK) != -1L) Unit
            }
        } catch (e: EOFException) {
            // The compressed bytes were cut at the capture limit; keep what was inflated so far.
        } catch (e: IOException) {
            // Corrupt gzip data; show whatever was inflated before the failure.
        }
        return out
    }

    /** Same heuristic as OkHttp's HttpLoggingInterceptor: no control characters near the start. */
    private fun Buffer.isProbablyText(): Boolean {
        val prefix = Buffer().also { copyTo(it, 0, minOf(size, TEXT_SNIFF_BYTES)) }
        return try {
            for (i in 0 until TEXT_SNIFF_CODE_POINTS) {
                if (prefix.exhausted()) break
                val codePoint = prefix.readUtf8CodePoint()
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) return false
            }
            true
        } catch (e: EOFException) {
            false
        }
    }

    private fun MediaType?.isEventStream(): Boolean =
        this != null && type == "text" && subtype == "event-stream"

    private fun Response.hasBody(): Boolean {
        if (request.method == "HEAD") return false
        if ((code < 100 || code >= 200) && code != 204 && code != 304) return true
        val contentLength = header("Content-Length")?.toLongOrNull() ?: -1L
        return contentLength != -1L || header("Transfer-Encoding").equals("chunked", ignoreCase = true)
    }
}
