package dev.mobile.netpilot.internal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.mock.MockRuleStore

internal sealed interface Screen {
    data object Transactions : Screen
    data class TransactionDetail(val id: Long) : Screen
    data object MockRules : Screen
    data class MockEditor(val ruleId: Long? = null, val fromTransactionId: Long? = null) : Screen
}

/** Screens are saved as plain strings so the back stack survives process death without Parcelable. */
private object ScreenRoutes {
    private const val SEPARATOR = ":"

    fun encode(screen: Screen): String = when (screen) {
        Screen.Transactions -> "transactions"
        is Screen.TransactionDetail -> "detail$SEPARATOR${screen.id}"
        Screen.MockRules -> "mocks"
        is Screen.MockEditor -> "editor$SEPARATOR${screen.ruleId ?: ""}$SEPARATOR${screen.fromTransactionId ?: ""}"
    }

    fun decode(route: String): Screen {
        val parts = route.split(SEPARATOR)
        return when (parts.first()) {
            "detail" -> parts.getOrNull(1)?.toLongOrNull()?.let { Screen.TransactionDetail(it) } ?: Screen.Transactions
            "mocks" -> Screen.MockRules
            "editor" -> Screen.MockEditor(parts.getOrNull(1)?.toLongOrNull(), parts.getOrNull(2)?.toLongOrNull())
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
    onClose: () -> Unit,
    onClear: () -> Unit,
    onShare: (HttpTransaction) -> Unit,
) {
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>(Screen.Transactions) }
    val navigateTo: (Screen) -> Unit = { backStack.add(it) }
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onClose() }
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
            onShare = onShare,
            onCreateMock = { navigateTo(Screen.MockEditor(fromTransactionId = screen.id)) },
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
