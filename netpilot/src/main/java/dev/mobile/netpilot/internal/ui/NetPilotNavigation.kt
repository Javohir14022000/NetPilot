package dev.mobile.netpilot.internal.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.capture.RedactedHeaderStore
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.mock.MockRuleStore
import dev.mobile.netpilot.internal.replay.ReplayResult
import dev.mobile.netpilot.internal.replay.RequestReplayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface Screen {
    data object Transactions : Screen
    data class TransactionDetail(val id: Long) : Screen
    data class RequestEditor(val transactionId: Long) : Screen
    data object MockRules : Screen
    data class MockEditor(val ruleId: Long? = null, val fromTransactionId: Long? = null) : Screen
}

/** Screens are saved as plain strings so the back stack survives process death without Parcelable. */
private object ScreenRoutes {
    private const val SEPARATOR = ":"

    fun encode(screen: Screen): String = when (screen) {
        Screen.Transactions -> "transactions"
        is Screen.TransactionDetail -> "detail$SEPARATOR${screen.id}"
        is Screen.RequestEditor -> "edit$SEPARATOR${screen.transactionId}"
        Screen.MockRules -> "mocks"
        is Screen.MockEditor -> "editor$SEPARATOR${screen.ruleId ?: ""}$SEPARATOR${screen.fromTransactionId ?: ""}"
    }

    fun decode(route: String): Screen {
        val parts = route.split(SEPARATOR)
        val id = parts.getOrNull(1)?.toLongOrNull()
        return when (parts.first()) {
            "detail" -> id?.let { Screen.TransactionDetail(it) } ?: Screen.Transactions
            "edit" -> id?.let { Screen.RequestEditor(it) } ?: Screen.Transactions
            "mocks" -> Screen.MockRules
            "editor" -> Screen.MockEditor(id, parts.getOrNull(2)?.toLongOrNull())
            else -> Screen.Transactions
        }
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Screen>, String>(
    save = { stack -> stack.map(ScreenRoutes::encode) },
    restore = { routes -> routes.map(ScreenRoutes::decode).toMutableStateList() },
)

@Composable
internal fun NetPilotApp(
    repository: TransactionRepository,
    mockStore: MockRuleStore,
    replayer: RequestReplayer,
    redactedHeaders: RedactedHeaderStore,
    onClose: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>(Screen.Transactions) }
    val navigateTo: (Screen) -> Unit = { backStack.add(it) }
    val replaceTop: (Screen) -> Unit = { backStack[backStack.lastIndex] = it }
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onClose() }
    val retry: (Long) -> Unit = { id ->
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { replayer.retry(id) }) {
                is ReplayResult.Recorded -> navigateTo(Screen.TransactionDetail(result.transactionId))
                is ReplayResult.NotRecorded -> Toast.makeText(
                    context,
                    context.getString(R.string.netpilot_retry_failed, result.message),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    BackHandler(enabled = backStack.size > 1, onBack = goBack)

    when (val screen = backStack.last()) {
        Screen.Transactions -> TransactionListScreen(
            repository = repository,
            onOpen = { navigateTo(Screen.TransactionDetail(it)) },
            onOpenMocks = { navigateTo(Screen.MockRules) },
            onBack = onClose,
            onClear = onClear,
        )
        is Screen.TransactionDetail -> TransactionDetailScreen(
            repository = repository,
            id = screen.id,
            onBack = goBack,
            onRetry = { retry(screen.id) },
            onEditRetry = { navigateTo(Screen.RequestEditor(screen.id)) },
            onCreateMock = { navigateTo(Screen.MockEditor(fromTransactionId = screen.id)) },
        )
        is Screen.RequestEditor -> RequestEditorRoute(
            repository = repository,
            redactedHeaders = redactedHeaders,
            replayer = replayer,
            transactionId = screen.transactionId,
            onBack = goBack,
            onSent = { replaceTop(Screen.TransactionDetail(it)) },
        )
        Screen.MockRules -> MockRulesScreen(
            store = mockStore,
            onBack = goBack,
            onAdd = { navigateTo(Screen.MockEditor()) },
            onEdit = { navigateTo(Screen.MockEditor(ruleId = it)) },
        )
        is Screen.MockEditor -> MockRuleEditorRoute(
            store = mockStore,
            repository = repository,
            ruleId = screen.ruleId,
            fromTransactionId = screen.fromTransactionId,
            onDone = goBack,
        )
    }
}
