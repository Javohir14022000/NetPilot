package dev.mobile.netpilot.internal

import android.content.Context
import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.SqliteTransactionRepository
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.mock.MockRuleStore
import dev.mobile.netpilot.internal.mock.SqliteMockRuleStore
import dev.mobile.netpilot.internal.notification.LauncherShortcut
import dev.mobile.netpilot.internal.notification.TransactionNotifier
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
    private val lock = Any()
    private val isShortcutPublished = AtomicBoolean(false)

    @Volatile
    private var repository: TransactionRepository? = null

    @Volatile
    private var notifier: TransactionNotifier? = null

    @Volatile
    private var mockStore: MockRuleStore? = null

    fun mockStore(context: Context): MockRuleStore =
        mockStore ?: synchronized(lock) {
            mockStore ?: SqliteMockRuleStore(context.applicationContext).also { mockStore = it }
        }

    fun repository(context: Context, config: NetPilotConfig = NetPilotConfig()): TransactionRepository =
        repository ?: synchronized(lock) {
            repository ?: SqliteTransactionRepository(context.applicationContext, config).also { repository = it }
        }

    fun listener(context: Context, config: NetPilotConfig): TransactionListener {
        if (config.isLauncherShortcutEnabled && isShortcutPublished.compareAndSet(false, true)) {
            LauncherShortcut.publish(context.applicationContext)
        }
        return if (config.isNotificationEnabled) notifier(context) else TransactionListener { }
    }

    fun clear(context: Context) {
        repository(context).clear()
        notifier?.clear()
    }

    private fun notifier(context: Context): TransactionNotifier =
        notifier ?: synchronized(lock) {
            notifier ?: TransactionNotifier(context.applicationContext).also { notifier = it }
        }
}
