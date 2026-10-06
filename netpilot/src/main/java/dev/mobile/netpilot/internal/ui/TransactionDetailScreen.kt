package dev.mobile.netpilot.internal.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.Format
import dev.mobile.netpilot.internal.data.HttpHeader
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.data.observe

/** Long bodies are rendered in chunks so the lazy list does not lay out one giant Text. */
private const val BODY_LINES_PER_ITEM = 100

private sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val transaction: HttpTransaction?) : DetailState
}

private enum class DetailTab(@StringRes val title: Int) {
    OVERVIEW(R.string.netpilot_tab_overview),
    REQUEST(R.string.netpilot_tab_request),
    RESPONSE(R.string.netpilot_tab_response),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailScreen(
    repository: TransactionRepository,
    id: Long,
    onBack: () -> Unit,
    onShare: (HttpTransaction) -> Unit,
) {
    val state by produceState<DetailState>(DetailState.Loading, id) {
        repository.observe(id).collect { value = DetailState.Loaded(it) }
    }
    val transaction = (state as? DetailState.Loaded)?.transaction
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = transaction?.let { "${it.method} ${it.path}" }.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.netpilot_ic_back), stringResource(R.string.netpilot_back))
                    }
                },
                actions = {
                    if (transaction != null) {
                        IconButton(onClick = { onShare(transaction) }) {
                            Icon(painterResource(R.drawable.netpilot_ic_share), stringResource(R.string.netpilot_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                DetailTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(tab.title)) },
                    )
                }
            }
            when {
                state is DetailState.Loading -> Unit
                transaction == null -> CenteredMessage(stringResource(R.string.netpilot_not_found))
                else -> DetailTabContent(transaction, DetailTab.entries[selectedTab])
            }
        }
    }
}

@Composable
private fun DetailTabContent(transaction: HttpTransaction, tab: DetailTab) {
    when (tab) {
        DetailTab.OVERVIEW -> OverviewTab(transaction)
        DetailTab.REQUEST -> MessageTab(
            headers = transaction.requestHeaders,
            body = transaction.requestBody,
            contentType = transaction.requestContentType,
        )
        DetailTab.RESPONSE -> if (transaction.status == TransactionStatus.IN_PROGRESS) {
            CenteredMessage(stringResource(R.string.netpilot_in_progress))
        } else {
            MessageTab(
                headers = transaction.responseHeaders,
                body = transaction.responseBody,
                contentType = transaction.responseContentType,
            )
        }
    }
}

@Composable
private fun OverviewTab(transaction: HttpTransaction) {
    val rows = with(transaction) {
        listOf(
            "URL" to url,
            "Method" to method,
            "Protocol" to protocol,
            "Status" to responseCode?.let { "$it ${responseMessage.orEmpty()}".trimEnd() },
            "Error" to error,
            "Request time" to Format.dateTime(requestDate),
            "Response time" to responseDate?.let(Format::dateTime),
            "Duration" to Format.duration(tookMs),
            "Request size" to Format.size(requestSize),
            "Response size" to Format.size(responseSize),
        ).mapNotNull { (label, value) -> value?.let { label to it } }
    }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(rows) { (label, value) ->
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MessageTab(headers: List<HttpHeader>, body: String?, contentType: String?) {
    val bodyChunks = remember(body, contentType) {
        BodyFormatter.format(body, contentType)
            ?.lines()
            ?.chunked(BODY_LINES_PER_ITEM) { it.joinToString("\n") }
            .orEmpty()
    }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            sectionTitle(R.string.netpilot_headers)
            items(headers) { header -> HeaderLine(header) }
            sectionTitle(R.string.netpilot_body)
            if (bodyChunks.isEmpty()) {
                item { Text(stringResource(R.string.netpilot_no_body), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(bodyChunks) { chunk ->
                    Text(chunk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun LazyListScope.sectionTitle(@StringRes title: Int) {
    item {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun HeaderLine(header: HttpHeader) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(header.name) }
            append(": ")
            append(header.value)
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
