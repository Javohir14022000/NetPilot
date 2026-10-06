package dev.mobile.netpilot.internal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.mobile.netpilot.internal.NetPilotComponents
import dev.mobile.netpilot.internal.TransactionText
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class NetPilotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = NetPilotComponents.repository(this)
        setContent {
            NetPilotTheme {
                NetPilotApp(
                    repository = repository,
                    onClose = ::finish,
                    onClear = ::clearAll,
                    onShare = ::share,
                )
            }
        }
    }

    private fun clearAll() {
        val appContext = applicationContext
        lifecycleScope.launch(Dispatchers.IO) { NetPilotComponents.clear(appContext) }
    }

    private fun share(transaction: HttpTransaction) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "${transaction.method} ${transaction.url}")
            .putExtra(Intent.EXTRA_TEXT, TransactionText.format(transaction))
        startActivity(Intent.createChooser(send, null))
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, NetPilotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun NetPilotApp(
    repository: TransactionRepository,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onShare: (HttpTransaction) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val id = selectedId
    if (id == null) {
        TransactionListScreen(
            repository = repository,
            onOpen = { selectedId = it },
            onBack = onClose,
            onClear = onClear,
        )
    } else {
        BackHandler { selectedId = null }
        TransactionDetailScreen(
            repository = repository,
            id = id,
            onBack = { selectedId = null },
            onShare = onShare,
        )
    }
}
