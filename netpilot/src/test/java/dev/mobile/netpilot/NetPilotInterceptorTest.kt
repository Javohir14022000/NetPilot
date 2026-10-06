package dev.mobile.netpilot

import dev.mobile.netpilot.internal.TransactionListener
import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.data.TransactionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class NetPilotInterceptorTest {

    private val server = MockWebServer()
    private val repository = FakeRepository()
    private val updates = mutableListOf<HttpTransaction>()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `records request and response of a successful call`() {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("""{"id":1}"""))
        val request = Request.Builder()
            .url(server.url("/posts?page=2"))
            .post("""{"title":"foo"}""".toRequestBody("application/json".toMediaType()))
            .build()

        // Act
        client().newCall(request).execute().use { it.body!!.string() }

        // Assert
        val stored = repository.single()
        assertEquals("POST", stored.method)
        assertEquals("/posts?page=2", stored.path)
        assertEquals("""{"title":"foo"}""", stored.requestBody)
        assertEquals(201, stored.responseCode)
        assertEquals("""{"id":1}""", stored.responseBody)
        assertEquals(8L, stored.responseSize)
        assertEquals(TransactionStatus.COMPLETE, stored.status)
        assertNotNull(stored.tookMs)
    }

    @Test
    fun `app still reads the full response body after capture`() {
        server.enqueue(MockResponse().setBody("hello world"))

        val body = client().newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.body!!.string() }

        assertEquals("hello world", body)
    }

    @Test
    fun `notifies listener on insert and on completion`() {
        server.enqueue(MockResponse().setBody("ok"))

        client().newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals(listOf(TransactionStatus.IN_PROGRESS, TransactionStatus.COMPLETE), updates.map { it.status })
    }

    @Test
    fun `redacts sensitive headers in storage but sends the real value`() {
        server.enqueue(MockResponse().setHeader("Set-Cookie", "session=abc").setBody("ok"))
        val request = Request.Builder()
            .url(server.url("/"))
            .header("Authorization", "Bearer secret-token")
            .header("Accept", "text/plain")
            .build()

        client().newCall(request).execute().close()

        assertEquals("Bearer secret-token", server.takeRequest().getHeader("Authorization"))
        val stored = repository.single()
        assertTrue(HttpHeader("Authorization", NetPilotConfig.REDACTED_VALUE) in stored.requestHeaders)
        assertTrue(HttpHeader("Accept", "text/plain") in stored.requestHeaders)
        assertTrue(HttpHeader("Set-Cookie", NetPilotConfig.REDACTED_VALUE) in stored.responseHeaders)
    }

    @Test
    fun `truncates bodies longer than maxContentLength`() {
        server.enqueue(MockResponse().setBody("a".repeat(100)))
        val config = NetPilotConfig(maxContentLength = 10)

        client(config).newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.body!!.string() }

        val body = repository.single().responseBody!!
        assertTrue(body.startsWith("a".repeat(10) + "\n\n[truncated"))
    }

    @Test
    fun `marks binary bodies instead of storing them`() {
        val binary = Buffer().write(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(binary))

        client().newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.body!!.bytes() }

        assertEquals("[binary body not shown]", repository.single().responseBody)
    }

    @Test
    fun `decodes gzip bodies the app asked for explicitly`() {
        val gzipped = Buffer().also { sink -> GzipSink(sink).buffer().use { it.writeUtf8("zipped text") } }
        server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(gzipped))
        val request = Request.Builder().url(server.url("/")).header("Accept-Encoding", "gzip").build()

        client().newCall(request).execute().use { it.body!!.bytes() }

        assertEquals("zipped text", repository.single().responseBody)
    }

    @Test
    fun `stores no body for HEAD requests`() {
        server.enqueue(MockResponse().setHeader("Content-Length", "42"))

        client().newCall(Request.Builder().url(server.url("/")).head().build()).execute().close()

        assertNull(repository.single().responseBody)
    }

    @Test
    fun `records the error and rethrows when the call fails`() {
        val url = server.url("/")
        server.shutdown()

        val thrown = runCatching { client().newCall(Request.Builder().url(url).build()).execute() }.exceptionOrNull()

        assertTrue(thrown is IOException)
        val stored = repository.single()
        assertEquals(TransactionStatus.FAILED, stored.status)
        assertNotNull(stored.error)
    }

    @Test
    fun `network call still succeeds when storage fails`() {
        server.enqueue(MockResponse().setBody("ok"))
        val failingRepository = object : TransactionRepository by repository {
            override fun insert(transaction: HttpTransaction): Long = throw IllegalStateException("disk full")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(NetPilotInterceptor(NetPilotConfig(), failingRepository, { }))
            .build()

        val body = client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.body!!.string() }

        assertEquals("ok", body)
    }

    private fun client(config: NetPilotConfig = NetPilotConfig()): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(NetPilotInterceptor(config, repository, TransactionListener { updates += it }))
            .build()

    private class FakeRepository : TransactionRepository {
        private val stored = linkedMapOf<Long, HttpTransaction>()
        private var nextId = 1L
        override val changes: StateFlow<Long> = MutableStateFlow(0L)

        override fun insert(transaction: HttpTransaction): Long {
            val id = nextId++
            stored[id] = transaction.copy(id = id)
            return id
        }

        override fun update(transaction: HttpTransaction) {
            stored[transaction.id] = transaction
        }

        override fun getSummaries(query: String): List<TransactionSummary> = emptyList()

        override fun get(id: Long): HttpTransaction? = stored[id]

        override fun clear() = stored.clear()

        fun single(): HttpTransaction = stored.values.single()
    }
}
