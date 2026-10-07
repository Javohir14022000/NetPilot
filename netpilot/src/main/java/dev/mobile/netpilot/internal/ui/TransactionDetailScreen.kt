package dev.mobile.netpilot.internal.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.Format
import dev.mobile.netpilot.internal.TransactionText
import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.data.observe
import dev.mobile.netpilot.internal.export.CurlFormatter
import dev.mobile.netpilot.internal.mock.HttpStatus
import kotlinx.coroutines.launch

/** Long bodies are rendered in chunks so the lazy list does not lay out one giant Text. */
private const val BODY_LINES_PER_ITEM = 100

/** Highlighting is skipped for very large chunks to keep scrolling smooth. */
private const val MAX_HIGHLIGHT_CHARS = 60_000
private const val NO_VALUE = "—"

private sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val transaction: HttpTransaction?) : DetailState
}

private enum class DetailTab(@StringRes val title: Int) {
    OVERVIEW(R.string.netpilot_tab_overview),
    REQUEST(R.string.netpilot_tab_request),
    RESPONSE(R.string.netpilot_tab_response),
}

/** Headers and display-ready body of the request or response tab. */
private class MessageContent(
    val headers: List<HttpHeader>,
    val rawBody: String?,
    val bodyChunks: List<String>,
    val isJson: Boolean,
    val contentType: String?,
    val size: Long?,
)

@Composable
internal fun TransactionDetailScreen(
    repository: TransactionRepository,
    id: Long,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEditRetry: () -> Unit,
    onCreateMock: () -> Unit,
) {
    val state by produceState<DetailState>(DetailState.Loading, id) {
        repository.observe(id).collect { value = DetailState.Loaded(it) }
    }
    val transaction = (state as? DetailState.Loaded)?.transaction
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            NetPilotTopBar(
                title = transaction?.path.orEmpty(),
                subtitle = transaction?.host,
                onBack = onBack,
            ) {
                if (transaction != null) DetailActionsMenu(transaction, onRetry, onEditRetry, onCreateMock)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state is DetailState.Loading -> Unit
                transaction == null -> CenteredMessage(stringResource(R.string.netpilot_not_found))
                else -> DetailContent(transaction, DetailTab.entries[selectedTab]) { selectedTab = it.ordinal }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailContent(transaction: HttpTransaction, tab: DetailTab, onSelectTab: (DetailTab) -> Unit) {
    val message = remember(transaction, tab) { messageContent(transaction, tab) }
    val isWaiting = tab == DetailTab.RESPONSE && transaction.status == TransactionStatus.IN_PROGRESS
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)) {
        item(key = "summary") { SummaryCard(transaction, Modifier.padding(bottom = 12.dp)) }
        stickyHeader(key = "tabs") { TabSelector(tab, onSelectTab) }
        when {
            tab == DetailTab.OVERVIEW -> overviewItems(transaction)
            isWaiting -> item(key = "waiting") {
                EmptyState(
                    icon = R.drawable.netpilot_ic_notification,
                    title = stringResource(R.string.netpilot_in_progress),
                    message = transaction.url,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            message != null -> messageItems(message)
        }
    }
}

private fun messageContent(transaction: HttpTransaction, tab: DetailTab): MessageContent? = when (tab) {
    DetailTab.OVERVIEW -> null
    DetailTab.REQUEST -> buildMessage(
        transaction.requestHeaders, transaction.requestBody, transaction.requestContentType, transaction.requestSize,
    )
    DetailTab.RESPONSE -> buildMessage(
        transaction.responseHeaders, transaction.responseBody, transaction.responseContentType, transaction.responseSize,
    )
}

private fun buildMessage(headers: List<HttpHeader>, body: String?, contentType: String?, size: Long?) = MessageContent(
    headers = headers,
    rawBody = body,
    bodyChunks = BodyFormatter.format(body, contentType)
        ?.lines()
        ?.chunked(BODY_LINES_PER_ITEM) { it.joinToString("\n") }
        .orEmpty(),
    isJson = BodyFormatter.isJson(body, contentType),
    contentType = contentType,
    size = size,
)

@Composable
private fun SummaryCard(transaction: HttpTransaction, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    val tone = palette.forStatus(transaction.responseCode, transaction.status)
    OutlineCard(modifier.fillMaxWidth(), borderColor = tone.border, containerColor = tone.container) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Format.statusLabel(transaction.responseCode, transaction.status), style = NetPilotMono.display, color = tone.content)
                Text(
                    text = statusText(transaction),
                    style = MaterialTheme.typography.titleSmall,
                    color = tone.content,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Pill(transaction.method, tone, isMonospace = true)
            }
            SelectionContainer {
                Text(transaction.url, style = NetPilotMono.small, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Duration", Format.duration(transaction.tookMs) ?: NO_VALUE, Modifier.weight(1f))
                StatTile("Size", Format.size(transaction.responseSize) ?: NO_VALUE, Modifier.weight(1f))
                StatTile("Time", Format.time(transaction.requestDate), Modifier.weight(1f))
            }
            transaction.mockRuleName?.let { Pill("Mocked by $it", palette.mock) }
            transaction.error?.let { Text(it, style = NetPilotMono.small, color = palette.serverError.content) }
        }
    }
}

@Composable
private fun statusText(transaction: HttpTransaction): String = when (transaction.status) {
    TransactionStatus.IN_PROGRESS -> stringResource(R.string.netpilot_in_progress)
    TransactionStatus.FAILED -> "Failed"
    TransactionStatus.COMPLETE -> transaction.responseMessage?.takeIf { it.isNotBlank() }
        ?: HttpStatus.reasonPhrase(transaction.responseCode ?: 0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabSelector(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().background(colors.surface).padding(top = 4.dp, bottom = 12.dp),
    ) {
        DetailTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, DetailTab.entries.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primaryContainer,
                    activeContentColor = colors.onPrimaryContainer,
                    activeBorderColor = colors.outlineVariant,
                    inactiveContainerColor = colors.surfaceContainerLowest,
                    inactiveBorderColor = colors.outlineVariant,
                ),
                label = { Text(stringResource(tab.title)) },
            )
        }
    }
}

private fun LazyListScope.overviewItems(transaction: HttpTransaction) = with(transaction) {
    item(key = "overview-request") {
        SectionCard("Request", Modifier.animateItem().padding(bottom = 12.dp)) {
            KeyValueRow("Method", method, isMonospace = true)
            KeyValueRow("Host", host, isMonospace = true)
            KeyValueRow("Path", path, isMonospace = true)
            KeyValueRow("Protocol", protocol ?: NO_VALUE, showDivider = false)
        }
    }
    item(key = "overview-timing") {
        SectionCard("Timing", Modifier.animateItem().padding(bottom = 12.dp)) {
            KeyValueRow("Started", Format.dateTime(requestDate))
            KeyValueRow("Finished", responseDate?.let(Format::dateTime) ?: NO_VALUE)
            KeyValueRow("Duration", Format.duration(tookMs) ?: NO_VALUE, showDivider = false)
        }
    }
    item(key = "overview-size") {
        SectionCard("Size", Modifier.animateItem()) {
            KeyValueRow("Request", Format.size(requestSize) ?: NO_VALUE)
            KeyValueRow("Response", Format.size(responseSize) ?: NO_VALUE, showDivider = false)
        }
    }
}

private fun LazyListScope.messageItems(message: MessageContent) {
    item(key = "headers") {
        SectionCard("Headers · ${message.headers.size}", Modifier.animateItem().padding(bottom = 12.dp)) {
            if (message.headers.isEmpty()) {
                Text("No headers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message.headers.forEachIndexed { index, header ->
                KeyValueRow(header.name, header.value, isMonospace = true, showDivider = index != message.headers.lastIndex)
            }
        }
    }
    item(key = "body-header") { BodyHeader(message, Modifier.animateItem()) }
    if (message.bodyChunks.isEmpty()) {
        item(key = "body-empty") { BodyBlock(AnnotatedString(stringResource(R.string.netpilot_no_body)), isLast = true, isMuted = true) }
    }
    itemsIndexed(message.bodyChunks, key = { index, _ -> "body-$index" }) { index, chunk ->
        val colors = jsonColors()
        val text = remember(chunk, message.isJson, colors) { highlight(chunk, message.isJson, colors) }
        BodyBlock(text, isLast = index == message.bodyChunks.lastIndex)
    }
}

@Composable
private fun BodyHeader(message: MessageContent, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.background(colors.surfaceContainer, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel(stringResource(R.string.netpilot_body))
            message.contentType?.let { Pill(it.substringBefore(';').trim(), LocalStatusPalette.current.neutral) }
            Spacer(Modifier.weight(1f))
            Format.size(message.size)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
            val body = message.rawBody
            if (body.isNullOrEmpty()) Spacer(Modifier.height(48.dp)) else CopyButton(body, label = "Body")
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

@Composable
private fun BodyBlock(text: AnnotatedString, isLast: Boolean, isMuted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val shape = if (isLast) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp) else RectangleShape
    SelectionContainer {
        Text(
            text = text,
            style = NetPilotMono.small,
            color = if (isMuted) colors.onSurfaceVariant else colors.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainer, shape)
                .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = if (isLast) 14.dp else 0.dp),
        )
    }
}

@Composable
private fun jsonColors(): Map<JsonToken, Color> {
    val palette = LocalStatusPalette.current
    val scheme = MaterialTheme.colorScheme
    return remember(palette, scheme) {
        mapOf(
            JsonToken.KEY to scheme.primary,
            JsonToken.STRING to palette.success.content,
            JsonToken.NUMBER to palette.clientError.content,
            JsonToken.LITERAL to palette.mock.content,
            JsonToken.PUNCTUATION to scheme.onSurfaceVariant,
        )
    }
}

private fun highlight(chunk: String, isJson: Boolean, colors: Map<JsonToken, Color>): AnnotatedString {
    if (!isJson || chunk.length > MAX_HIGHLIGHT_CHARS) return AnnotatedString(chunk)
    return buildAnnotatedString {
        append(chunk)
        JsonTokenizer.tokenize(chunk).forEach { span ->
            addStyle(SpanStyle(color = colors.getValue(span.token)), span.start, span.end)
        }
    }
}

@Composable
private fun DetailActionsMenu(
    transaction: HttpTransaction,
    onRetry: () -> Unit,
    onEditRetry: () -> Unit,
    onCreateMock: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    val subject = "${transaction.method} ${transaction.url}"
    val runAndClose: (() -> Unit) -> Unit = { action ->
        isExpanded = false
        action()
    }

    IconButton(onClick = { isExpanded = true }) {
        Icon(painterResource(R.drawable.netpilot_ic_more), stringResource(R.string.netpilot_more))
    }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
        MenuItem(R.string.netpilot_retry, isEnabled = transaction.status != TransactionStatus.IN_PROGRESS) {
            runAndClose(onRetry)
        }
        MenuItem(R.string.netpilot_edit_retry) { runAndClose(onEditRetry) }
        if (transaction.responseCode != null) {
            MenuItem(R.string.netpilot_create_mock) { runAndClose(onCreateMock) }
        }
        HorizontalDivider()
        MenuItem(R.string.netpilot_copy_curl) {
            runAndClose { ShareActions.copyToClipboard(context, "cURL", CurlFormatter.format(transaction)) }
        }
        MenuItem(R.string.netpilot_share_text) {
            runAndClose {
                scope.launch {
                    ShareActions.shareText(context, subject, TransactionText.format(transaction), "netpilot-request.txt")
                }
            }
        }
        MenuItem(R.string.netpilot_share_curl) {
            runAndClose {
                scope.launch {
                    ShareActions.shareText(context, subject, CurlFormatter.format(transaction), "netpilot-request.sh")
                }
            }
        }
        MenuItem(R.string.netpilot_share_har) {
            runAndClose { scope.launch { ShareActions.shareHar(context, listOf(transaction)) } }
        }
    }
}
