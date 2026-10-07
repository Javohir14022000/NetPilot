package dev.mobile.netpilot

import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.mock.MockOutcome
import dev.mobile.netpilot.internal.mock.MockResponder
import dev.mobile.netpilot.internal.mock.MockRule
import dev.mobile.netpilot.internal.mock.MockRuleMatcher
import dev.mobile.netpilot.internal.mock.firstMatch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetPilotInterceptorMockTest {

    private val server = MockWebServer()
    private val repository = FakeTransactionRepository()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `returns the mocked response without reaching the server`() {
        // Arrange
        val rule = MockRule(name = "post 42", urlPattern = "*/posts/*", statusCode = 201, body = """{"id":42}""")

        // Act
        val (code, body, header) = client(rule).newCall(get("/posts/1")).execute().use {
            Triple(it.code, it.body!!.string(), it.header(MockResponder.MOCK_HEADER))
        }

        // Assert
        assertEquals(201, code)
        assertEquals("""{"id":42}""", body)
        assertEquals("post 42", header)
        assertEquals(0, server.requestCount)
        val stored = repository.single()
        assertEquals("post 42", stored.mockRuleName)
        assertEquals("""{"id":42}""", stored.responseBody)
        assertEquals(TransactionStatus.COMPLETE, stored.status)
    }

    @Test
    fun `mocked response carries the rule content type and headers`() {
        val rule = MockRule(
            name = "xml",
            urlPattern = "/feed",
            contentType = "application/xml",
            headers = listOf(HttpHeader("X-Demo", "1")),
            body = "<feed/>",
        )

        val (contentType, demoHeader) = client(rule).newCall(get("/feed")).execute().use {
            it.body!!.contentType().toString() to it.header("X-Demo")
        }

        assertEquals("application/xml", contentType)
        assertEquals("1", demoHeader)
    }

    @Test
    fun `requests that match no rule go to the network`() {
        server.enqueue(MockResponse().setBody("real"))

        val body = client(MockRule(name = "other", urlPattern = "*/other")).newCall(get("/posts/1")).execute()
            .use { it.body!!.string() }

        assertEquals("real", body)
        assertEquals(1, server.requestCount)
        assertNull(repository.single().mockRuleName)
    }

    @Test
    fun `method filter only mocks the configured method`() {
        server.enqueue(MockResponse().setBody("real"))
        val rule = MockRule(name = "post only", method = "POST", urlPattern = "*/posts")

        val body = client(rule).newCall(get("/posts")).execute().use { it.body!!.string() }

        assertEquals("real", body)
    }

    @Test
    fun `applies the configured delay`() {
        val rule = MockRule(name = "slow", urlPattern = "/slow", delayMillis = 200)

        val start = System.nanoTime()
        client(rule).newCall(get("/slow")).execute().close()
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        assertTrue("elapsed $elapsedMillis ms", elapsedMillis >= 200)
    }

    @Test
    fun `simulates no internet and records the failure`() {
        val rule = MockRule(name = "offline", urlPattern = "/posts", outcome = MockOutcome.NO_INTERNET)

        val thrown = runCatching { client(rule).newCall(get("/posts")).execute() }.exceptionOrNull()

        assertTrue(thrown is UnknownHostException)
        val stored = repository.single()
        assertEquals(TransactionStatus.FAILED, stored.status)
        assertEquals("offline", stored.mockRuleName)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `simulates a timeout`() {
        val rule = MockRule(name = "timeout", urlPattern = "/posts", outcome = MockOutcome.TIMEOUT)

        val thrown = runCatching { client(rule).newCall(get("/posts")).execute() }.exceptionOrNull()

        assertTrue(thrown is SocketTimeoutException)
    }

    private fun get(path: String): Request = Request.Builder().url(server.url(path)).build()

    private fun client(vararg rules: MockRule): OkHttpClient {
        val matcher = MockRuleMatcher { request -> rules.toList().firstMatch(request) }
        return OkHttpClient.Builder()
            .addInterceptor(NetPilotInterceptor(NetPilotConfig(), repository, { }, matcher))
            .build()
    }
}
