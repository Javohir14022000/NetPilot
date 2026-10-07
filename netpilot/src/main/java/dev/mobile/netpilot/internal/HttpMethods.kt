package dev.mobile.netpilot.internal

/** HTTP methods offered in NetPilot's editors. */
internal object HttpMethods {
    val ALL = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")

    /** OkHttp rejects a body for these. */
    val WITHOUT_BODY = setOf("GET", "HEAD")

    /** OkHttp requires a (possibly empty) body for these. */
    val REQUIRING_BODY = setOf("POST", "PUT", "PATCH")
}
