package dev.mobile.netpilot

import android.content.Context
import android.util.Log
import dev.mobile.netpilot.internal.NetPilotComponents
import dev.mobile.netpilot.internal.TransactionListener
import dev.mobile.netpilot.internal.capture.TransactionFactory
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.mock.MockResponder
import dev.mobile.netpilot.internal.mock.MockRuleMatcher
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * OkHttp interceptor that records every request and response so they can be inspected
 * in the NetPilot UI. Add it as an application interceptor:
 *
 * ```
 * OkHttpClient.Builder().addInterceptor(NetPilotInterceptor(context))
 * ```
 *
 * Recording failures never affect the app's network call: they are logged and skipped.
 */
class NetPilotInterceptor internal constructor(
    private val config: NetPilotConfig,
    private val repository: TransactionRepository,
    private val listener: TransactionListener,
    private val mocks: MockRuleMatcher = MockRuleMatcher.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
) : Interceptor {

    @JvmOverloads
    constructor(context: Context, config: NetPilotConfig = NetPilotConfig()) : this(
        config = config,
        repository = NetPilotComponents.repository(context, config),
        listener = NetPilotComponents.listener(context, config),
        mocks = NetPilotComponents.mockStore(context),
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val mock = safely("match mock rules") { mocks.findMatch(request) }
        val pending = safely("capture request") {
            val started = TransactionFactory.fromRequest(request, config, clock()).copy(mockRuleName = mock?.name)
            val saved = started.copy(id = repository.insert(started))
            listener.onTransactionUpdated(saved)
            saved
        }
        val startNanos = System.nanoTime()

        val response = try {
            if (mock != null) MockResponder.respond(chain, mock) else chain.proceed(request)
        } catch (e: Exception) {
            pending?.let { record(it.copy(tookMs = elapsedMillis(startNanos), error = e.toString())) }
            throw e
        }

        pending?.let { started ->
            val tookMs = elapsedMillis(startNanos)
            val completed = safely("capture response") {
                TransactionFactory.withResponse(started, response, config, clock(), tookMs)
            } ?: started.copy(responseCode = response.code, responseMessage = response.message, tookMs = tookMs)
            record(completed)
        }
        return response
    }

    private fun record(transaction: HttpTransaction) {
        safely("store transaction") {
            repository.update(transaction)
            listener.onTransactionUpdated(transaction)
        }
    }

    private fun elapsedMillis(startNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

    private inline fun <T> safely(action: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to $action; the request itself is unaffected", e)
        null
    }

    private companion object {
        const val TAG = "NetPilot"
    }
}
