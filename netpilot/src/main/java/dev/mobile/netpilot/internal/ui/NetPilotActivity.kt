package dev.mobile.netpilot.internal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.mobile.netpilot.internal.NetPilotComponents
import dev.mobile.netpilot.internal.TransactionText
import dev.mobile.netpilot.internal.data.HttpTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class NetPilotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = NetPilotComponents.repository(this)
        val mockStore = NetPilotComponents.mockStore(this)
        setContent {
            NetPilotTheme {
                NetPilotApp(
                    repository = repository,
                    mockStore = mockStore,
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
