package dev.mobile.netpilot.internal.ui

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pretty-prints JSON bodies for display; anything else is returned unchanged. */
internal object BodyFormatter {
    private const val JSON_INDENT = 2

    fun format(body: String?, contentType: String?): String? {
        if (body.isNullOrEmpty()) return null
        val trimmed = body.trim()
        val looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        if (!looksLikeJson && contentType?.contains("json", ignoreCase = true) != true) return body
        return try {
            val pretty = if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(JSON_INDENT)
            } else {
                JSONObject(trimmed).toString(JSON_INDENT)
            }
            // org.json escapes '/', which only adds noise to URLs in the output.
            pretty.replace("\\/", "/")
        } catch (e: JSONException) {
            // Truncated or invalid JSON: show it exactly as received.
            body
        }
    }
}
