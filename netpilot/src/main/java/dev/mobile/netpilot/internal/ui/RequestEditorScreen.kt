package dev.mobile.netpilot.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mobile.netpilot.NetPilotConfig
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.HttpMethods
import dev.mobile.netpilot.internal.capture.RedactedHeaderStore
import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.replay.ReplayResult
import dev.mobile.netpilot.internal.replay.RequestDraft
import dev.mobile.netpilot.internal.replay.RequestDraftResult
import dev.mobile.netpilot.internal.replay.RequestField
import dev.mobile.netpilot.internal.replay.RequestReplayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val URL_MIN_LINES = 2
private const val HEADERS_MIN_LINES = 3
private const val BODY_MIN_LINES = 6
private val EMPTY_DRAFT = RequestDraft(method = "GET", url = "", headers = "", body = "")

/** Loads the captured request off the main thread, then shows the editor. */
@Composable
internal fun RequestEditorRoute(
    repository: TransactionRepository,
    redactedHeaders: RedactedHeaderStore,
    replayer: RequestReplayer,
    transactionId: Long,
    onBack: () -> Unit,
    onSent: (Long) -> Unit,
) {
    val initial by produceState<RequestDraft?>(initialValue = null, transactionId) {
        value = withContext(Dispatchers.IO) {
            repository.get(transactionId)?.let { RequestDraft.fromTransaction(it) } ?: EMPTY_DRAFT
        }
    }
    val draft = initial ?: return
    RequestEditorScreen(
        initial = draft,
        secrets = remember(transactionId) { redactedHeaders.get(transactionId) },
        replayer = replayer,
        onBack = onBack,
        onSent = onSent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestEditorScreen(
    initial: RequestDraft,
    secrets: List<HttpHeader>,
    replayer: RequestReplayer,
    onBack: () -> Unit,
    onSent: (Long) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var errors by remember { mutableStateOf<Map<RequestField, String>>(emptyMap()) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val send: () -> Unit = {
        when (val result = draft.toRequest(secrets)) {
            is RequestDraftResult.Invalid -> errors = result.errors
            is RequestDraftResult.Valid -> {
                errors = emptyMap()
                sendError = null
                isSending = true
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) { replayer.send(result.request) }
                    isSending = false
                    when (outcome) {
                        is ReplayResult.Recorded -> onSent(outcome.transactionId)
                        is ReplayResult.NotRecorded -> sendError = outcome.message
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.netpilot_edit_retry)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.netpilot_ic_back), stringResource(R.string.netpilot_back))
                    }
                },
                actions = {
                    if (isSending) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(24.dp))
                    } else {
                        TextButton(onClick = send) { Text(stringResource(R.string.netpilot_send)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (NetPilotConfig.REDACTED_VALUE in draft.headers) {
                val notice = if (secrets.isEmpty()) R.string.netpilot_secrets_missing else R.string.netpilot_secrets_restored
                Notice(stringResource(notice), isError = secrets.isEmpty())
            }
            if (draft.isOriginalBodyMissing) Notice(stringResource(R.string.netpilot_body_missing))
            sendError?.let { Notice(stringResource(R.string.netpilot_retry_failed, it), isError = true) }

            ChoiceRow(
                title = "Method",
                options = HttpMethods.ALL,
                selected = draft.method.uppercase(),
                label = { it },
            ) { draft = draft.copy(method = it) }
            FormField(
                label = "URL",
                value = draft.url,
                error = errors[RequestField.URL],
                keyboardType = KeyboardType.Uri,
                minLines = URL_MIN_LINES,
            ) { draft = draft.copy(url = it) }
            FormField(
                label = "Headers",
                value = draft.headers,
                error = errors[RequestField.HEADERS],
                hint = "One per line, e.g. Accept: application/json",
                minLines = HEADERS_MIN_LINES,
                isMonospace = true,
            ) { draft = draft.copy(headers = it) }
            FormField(
                label = "Body",
                value = draft.body,
                error = errors[RequestField.BODY],
                minLines = BODY_MIN_LINES,
                isMonospace = true,
            ) { draft = draft.copy(body = it) }
        }
    }
}
