package dev.mobile.netpilot.internal

import android.content.Context
import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.NetPilotInterceptor
import dev.mobile.netpilot.internal.capture.RedactedHeaderStore
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.SqliteTransactionRepository
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.mock.MockRuleStore
import dev.mobile.netpilot.internal.mock.SqliteMockRuleStore
import dev.mobile.netpilot.internal.notification.LauncherShortcut
import dev.mobile.netpilot.internal.notification.TransactionNotifier
import dev.mobile.netpilot.internal.replay.RequestReplayer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Receives every insert and update of a captured transaction. */
internal fun interface TransactionListener {
    fun onTransactionUpdated(transaction: HttpTransaction)
}

/**
 * Process-wide singletons shared by all interceptors and the UI. The first interceptor
 * created decides storage limits; later configs only affect their own capture settings.
 */
internal object NetPilotComponents {
    private const val REPLAY_TIMEOUT_SECONDS = 30L

    private val lock = Any()
    private val isShortcutPublished = AtomicBoolean(false)

    /** Real values of redacted headers, in memory only. */
    val redactedHeaders = RedactedHeaderStore()

    @Volatile
    private var primaryConfig: NetPilotConfig? = null

    @Volatile
    private var repository: TransactionRepository? = null

    @Volatile
    private var notifier: TransactionNotifier? = null

    @Volatile
    private var mockStore: MockRuleStore? = null

    @Volatile
    private var replayer: RequestReplayer? = null

    fun mockStore(context: Context): MockRuleStore =
        mockStore ?: synchronized(lock) {
            mockStore ?: SqliteMockRuleStore(context.applicationContext).also { mockStore = it }
        }

    fun repository(context: Context, config: NetPilotConfig = NetPilotConfig()): TransactionRepository =
        repository ?: synchronized(lock) {
            repository ?: SqliteTransactionRepository(context.applicationContext, config).also { repository = it }
        }

    fun listener(context: Context, config: NetPilotConfig): TransactionListener {
        if (primaryConfig == null) primaryConfig = config
        if (config.isLauncherShortcutEnabled && isShortcutPublished.compareAndSet(false, true)) {
            LauncherShortcut.publish(context.applicationContext)
        }
        return if (config.isNotificationEnabled) notifier(context) else TransactionListener { }
    }

    /**
     * Client used by "Retry" and "Edit & retry". It reuses the app's NetPilot config so
     * replayed requests are redacted the same way as the original traffic.
     */
    fun replayer(context: Context): RequestReplayer =
        replayer ?: synchronized(lock) {
            replayer ?: run {
                val appContext = context.applicationContext
                val client = OkHttpClient.Builder()
                    .addInterceptor(NetPilotInterceptor(appContext, primaryConfig ?: NetPilotConfig()))
                    .readTimeout(REPLAY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
                RequestReplayer(client, repository(appContext), redactedHeaders)
            }.also { replayer = it }
        }

    fun clear(context: Context) {
        repository(context).clear()
        redactedHeaders.clear()
        notifier?.clear()
    }

    private fun notifier(context: Context): TransactionNotifier =
        notifier ?: synchronized(lock) {
            notifier ?: TransactionNotifier(context.applicationContext).also { notifier = it }
        }
}
