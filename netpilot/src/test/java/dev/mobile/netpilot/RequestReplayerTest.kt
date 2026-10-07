package dev.mobile.netpilot

import dev.mobile.netpilot.internal.capture.RedactedHeaderStore
import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.mock.MockRuleMatcher
import dev.mobile.netpilot.internal.replay.ReplayResult
import dev.mobile.netpilot.internal.replay.RequestReplayer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestReplayerTest {

    private val server = MockWebServer()
    private val repository = FakeTransactionRepository()
    private val secrets = RedactedHeaderStore()
    private val client = OkHttpClient.Builder()
        .addInterceptor(NetPilotInterceptor(NetPilotConfig(), repository, { }, MockRuleMatcher.NONE, secrets))
        .build()
    private val replayer = RequestReplayer(client, repository, secrets)

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `retry resends the original request with the real secret values`() {
        // Arrange
        server.enqueue(MockResponse().setBody("first"))
        server.enqueue(MockResponse().setBody("second"))
        val original = Request.Builder()
            .url(server.url("/me"))
            .header("Authorization", "Bearer real-token")
            .post("""{"a":1}""".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(original).execute().close()
        val originalId = repository.getRecent(1).single().id

        // Act
        val result = replayer.retry(originalId)

        // Assert
        server.takeRequest()
        val replayed = server.takeRequest()
        assertEquals("POST", replayed.method)
        assertEquals("Bearer real-token", replayed.getHeader("Authorization"))
        assertEquals("""{"a":1}""", replayed.body.readUtf8())
        val recordedId = (result as ReplayResult.Recorded).transactionId
        assertNotEquals(originalId, recordedId)
        val stored = repository.get(recordedId)!!
        assertTrue(HttpHeader("Authorization", NetPilotConfig.REDACTED_VALUE) in stored.requestHeaders)
    }

    @Test
    fun `retry explains when secrets are no longer in memory`() {
        val id = repository.insert(
            transaction(headers = listOf(HttpHeader("Authorization", NetPilotConfig.REDACTED_VALUE))),
        )

        val result = replayer.retry(id)

        assertTrue((result as ReplayResult.NotRecorded).message.contains("no longer in memory"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `retry refuses when the original body was not captured`() {
        val id = repository.insert(transaction(method = "POST", body = "[binary body not shown]"))

        val result = replayer.retry(id)

        assertTrue((result as ReplayResult.NotRecorded).message.contains("Edit & retry"))
    }

    @Test
    fun `send returns the recorded id even when the call fails`() {
        val url = server.url("/down")
        server.shutdown()

        val result = replayer.send(Request.Builder().url(url).build())

        val stored = repository.get((result as ReplayResult.Recorded).transactionId)!!
        assertEquals(TransactionStatus.FAILED, stored.status)
    }

    private fun transaction(
        method: String = "GET",
        headers: List<HttpHeader> = emptyList(),
        body: String? = null,
    ) = HttpTransaction(
        requestDate = 0,
        method = method,
        url = server.url("/items").toString(),
        host = "localhost",
        path = "/items",
        scheme = "http",
        requestHeaders = headers,
        requestBody = body,
    )
}
