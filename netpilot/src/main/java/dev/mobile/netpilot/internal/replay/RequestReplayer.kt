package dev.mobile.netpilot.internal.replay

import dev.mobile.netpilot.internal.capture.RedactedHeaderStore
import dev.mobile.netpilot.internal.data.TransactionRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Request tag that receives the id NetPilot stored the request under. */
internal fun interface TransactionIdListener {
    fun onRecorded(transactionId: Long)
}

internal sealed interface ReplayResult {
    /** The request was captured (even if it then failed); open this transaction. */
    data class Recorded(val transactionId: Long) : ReplayResult
    data class NotRecorded(val message: String) : ReplayResult
}

/**
 * Re-sends requests through a client that has NetPilot installed, so each retry shows up
 * in the inspector. The host app's own interceptors (token refresh, pinning) are not
 * applied, but redacted headers are restored from memory. All calls are blocking.
 */
internal class RequestReplayer(
    private val client: OkHttpClient,
    private val repository: TransactionRepository,
    private val redactedHeaders: RedactedHeaderStore,
) {

    /** Re-sends a captured request unchanged. */
    fun retry(transactionId: Long): ReplayResult {
        val transaction = repository.get(transactionId)
            ?: return ReplayResult.NotRecorded("The request no longer exists")
        val draft = RequestDraft.fromTransaction(transaction)
        if (draft.isOriginalBodyMissing) {
            return ReplayResult.NotRecorded("The original body was not captured; use Edit & retry")
        }
        return when (val result = draft.toRequest(redactedHeaders.get(transactionId))) {
            is RequestDraftResult.Valid -> send(result.request)
            is RequestDraftResult.Invalid -> ReplayResult.NotRecorded(result.errors.values.first())
        }
    }

    fun send(request: Request): ReplayResult {
        val recordedId = AtomicLong(NOT_RECORDED)
        val tagged = request.newBuilder()
            .tag(TransactionIdListener::class.java, TransactionIdListener { recordedId.set(it) })
            .build()
        val failure = try {
            client.newCall(tagged).execute().close()
            null
        } catch (e: IOException) {
            e
        }
        val id = recordedId.get()
        return if (id != NOT_RECORDED) {
            ReplayResult.Recorded(id)
        } else {
            ReplayResult.NotRecorded(failure?.message ?: "NetPilot could not record the request")
        }
    }

    private companion object {
        const val NOT_RECORDED = -1L
    }
}
