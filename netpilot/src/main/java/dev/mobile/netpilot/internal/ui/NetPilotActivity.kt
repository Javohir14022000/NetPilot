package dev.mobile.netpilot.internal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.mobile.netpilot.internal.NetPilotComponents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class NetPilotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = NetPilotComponents.repository(this)
        val mockStore = NetPilotComponents.mockStore(this)
        val replayer = NetPilotComponents.replayer(this)
        setContent {
            NetPilotTheme {
                NetPilotApp(
                    repository = repository,
                    mockStore = mockStore,
                    replayer = replayer,
                    redactedHeaders = NetPilotComponents.redactedHeaders,
                    onClose = ::finish,
                    onClear = ::clearAll,
                )
            }
        }
    }

    private fun clearAll() {
        val appContext = applicationContext
        lifecycleScope.launch(Dispatchers.IO) { NetPilotComponents.clear(appContext) }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, NetPilotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
