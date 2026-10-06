package dev.mobile.netpilot.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mobile.netpilot.NetPilot
import dev.mobile.netpilot.sample.ui.theme.SampleTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* NetPilot works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotificationPermission()
        val api = SampleApi(applicationContext)
        setContent {
            SampleTheme {
                SampleScreen(
                    api = api,
                    isNetPilotAvailable = NetPilot.isOp,
                    onOpenNetPilot = { startActivity(NetPilot.getLaunchIntent(this)) },
                )
            }
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(permission)
        }
    }
}

private data class DemoCall(val label: String, val run: suspend SampleApi.() -> String)

private val demoCalls = listOf(
    DemoCall("Retrofit · GET post") { retrofitGet() },
    DemoCall("Retrofit · POST JSON") { retrofitPost() },
    DemoCall("Retrofit · 404") { retrofitNotFound() },
    DemoCall("Retrofit · Authorization header") { retrofitWithAuthHeader() },
    DemoCall("Ktor · GET") { ktorGet() },
    DemoCall("Ktor · 500") { ktorServerError() },
    DemoCall("Ktor · unknown host") { ktorUnknownHost() },
)

@Composable
private fun SampleScreen(api: SampleApi, isNetPilotAvailable: Boolean, onOpenNetPilot: () -> Unit) {
    val scope = rememberCoroutineScope()
    var lastResult by remember { mutableStateOf("Tap a request, then open NetPilot") }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("NetPilot sample", style = MaterialTheme.typography.headlineSmall)
            Text(lastResult, style = MaterialTheme.typography.bodyMedium)
            demoCalls.forEach { call ->
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            lastResult = "${call.label}: running…"
                            lastResult = "${call.label}: ${runDemo(api, call)}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(call.label) }
            }
            Button(
                onClick = onOpenNetPilot,
                enabled = isNetPilotAvailable,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isNetPilotAvailable) "Open NetPilot" else "NetPilot disabled (release build)") }
        }
    }
}

private suspend fun runDemo(api: SampleApi, call: DemoCall): String = try {
    call.run(api)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Failures are part of the demo: NetPilot should show them as failed requests.
    "failed: ${e.javaClass.simpleName}: ${e.message}"
}
