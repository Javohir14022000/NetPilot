package dev.mobile.netpilot.internal.capture

import dev.mobile.netpilot.internal.data.HttpHeader

/**
 * Keeps the real values of redacted headers in memory only, so "Edit & retry" can resend an
 * authenticated request while the secrets never reach the disk. Lost when the process dies.
 */
internal class RedactedHeaderStore(private val capacity: Int = DEFAULT_CAPACITY) {

    private val entries = object : LinkedHashMap<Long, List<HttpHeader>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<HttpHeader>>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun put(transactionId: Long, headers: List<HttpHeader>) {
        if (headers.isNotEmpty()) entries[transactionId] = headers
    }

    @Synchronized
    fun get(transactionId: Long): List<HttpHeader> = entries[transactionId].orEmpty()

    @Synchronized
    fun clear() = entries.clear()

    private companion object {
        const val DEFAULT_CAPACITY = 200
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
