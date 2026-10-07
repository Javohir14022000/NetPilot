package dev.mobile.netpilot.internal.ui

import dev.mobile.netpilot.internal.data.TransactionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionFilterTest {

    private val ok = summary(id = 1, code = 200, tookMs = 100)
    private val redirect = summary(id = 2, code = 302, tookMs = 50)
    private val notFound = summary(id = 3, code = 404, tookMs = 300)
    private val serverError = summary(id = 4, code = 503, tookMs = null)
    private val failed = summary(id = 5, code = null, error = "UnknownHostException")
    private val pending = summary(id = 6, code = null)
    private val mocked = summary(id = 7, code = 201, mockRuleName = "create")
    private val all = listOf(ok, redirect, notFound, serverError, failed, pending, mocked)

    @Test
    fun `each filter keeps only its category`() {
        assertEquals(all, all.filter(TransactionFilter.ALL::matches))
        assertEquals(listOf(ok, mocked), all.filter(TransactionFilter.SUCCESS::matches))
        assertEquals(listOf(redirect), all.filter(TransactionFilter.REDIRECT::matches))
        assertEquals(listOf(notFound), all.filter(TransactionFilter.CLIENT_ERROR::matches))
        assertEquals(listOf(serverError), all.filter(TransactionFilter.SERVER_ERROR::matches))
        assertEquals(listOf(failed), all.filter(TransactionFilter.FAILED::matches))
        assertEquals(listOf(mocked), all.filter(TransactionFilter.MOCKED::matches))
    }

    @Test
    fun `stats count errors and average known durations`() {
        val stats = TrafficStats.from(all)

        assertEquals(7, stats.total)
        assertEquals(3, stats.errors)
        assertEquals(150L, stats.averageMillis)
    }

    @Test
    fun `stats without durations have no average`() {
        assertNull(TrafficStats.from(listOf(pending)).averageMillis)
        assertEquals(TrafficStats(0, 0, null), TrafficStats.from(emptyList()))
    }

    private fun summary(
        id: Long,
        code: Int?,
        tookMs: Long? = null,
        error: String? = null,
        mockRuleName: String? = null,
    ) = TransactionSummary(
        id = id,
        requestDate = 0,
        method = "GET",
        host = "api.example.com",
        path = "/items/$id",
        responseCode = code,
        responseSize = null,
        tookMs = tookMs,
        error = error,
        mockRuleName = mockRuleName,
    )
}
