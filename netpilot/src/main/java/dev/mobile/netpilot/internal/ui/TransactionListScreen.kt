package dev.mobile.netpilot.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.Format
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.data.TransactionSummary
import dev.mobile.netpilot.internal.data.observeSummaries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SLOW_REQUEST_MS = 1_000L
private const val META_SEPARATOR = "  ·  "

@Composable
internal fun TransactionListScreen(
    repository: TransactionRepository,
    onOpen: (Long) -> Unit,
    onOpenMocks: () -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TransactionFilter.ALL) }
    val summaries by remember(query) { repository.observeSummaries(query) }.collectAsState(initial = null)
    val all = summaries
    val visible = remember(all, filter) { all?.filter(filter::matches) }

    Scaffold(
        topBar = {
            NetPilotTopBar(
                title = stringResource(R.string.netpilot_name),
                subtitle = all?.let { statsLine(TrafficStats.from(it)) },
                onBack = onBack,
            ) {
                IconButton(onClick = onOpenMocks) {
                    Icon(painterResource(R.drawable.netpilot_ic_mock), stringResource(R.string.netpilot_mocks))
                }
                ListActionsMenu(repository, onClear)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query = query, onQueryChange = { query = it })
            if (!all.isNullOrEmpty()) FilterRow(all, filter) { filter = it }
            when {
                all == null || visible == null -> Unit
                all.isEmpty() && query.isBlank() -> EmptyState(
                    icon = R.drawable.netpilot_ic_notification,
                    title = stringResource(R.string.netpilot_empty_title),
                    message = stringResource(R.string.netpilot_empty_hint),
                    modifier = Modifier.fillMaxSize(),
                )
                visible.isEmpty() -> EmptyState(
                    icon = R.drawable.netpilot_ic_search,
                    title = stringResource(R.string.netpilot_no_matches_title),
                    message = stringResource(R.string.netpilot_no_matches_hint),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { summary ->
                        TransactionCard(summary, Modifier.animateItem()) { onOpen(summary.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun statsLine(stats: TrafficStats): String = listOfNotNull(
    stringResource(R.string.netpilot_stats_requests, stats.total),
    stats.errors.takeIf { it > 0 }?.let { stringResource(R.string.netpilot_stats_errors, it) },
    stats.averageMillis?.let { stringResource(R.string.netpilot_stats_average, Format.duration(it).orEmpty()) },
).joinToString(" · ")

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.netpilot_search_hint)) },
        singleLine = true,
        leadingIcon = {
            Icon(painterResource(R.drawable.netpilot_ic_search), null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painterResource(R.drawable.netpilot_ic_close),
                        stringResource(R.string.netpilot_clear_search),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = colors.outlineVariant,
            focusedBorderColor = colors.primary,
            unfocusedContainerColor = colors.surfaceContainerLowest,
            focusedContainerColor = colors.surfaceContainerLowest,
            unfocusedLeadingIconColor = colors.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Chips with live counts; empty categories are hidden unless selected. */
@Composable
private fun FilterRow(
    summaries: List<TransactionSummary>,
    selected: TransactionFilter,
    onSelect: (TransactionFilter) -> Unit,
) {
    val counts = remember(summaries) { TransactionFilter.entries.associateWith { filter -> summaries.count(filter::matches) } }
    val shown = TransactionFilter.entries.filter { it == TransactionFilter.ALL || it == selected || counts.getValue(it) > 0 }
    val colors = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        items(shown, key = { it.name }) { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(chipLabel(filter.label, counts.getValue(filter), colors.onSurfaceVariant)) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceContainerLowest,
                    selectedContainerColor = colors.primaryContainer,
                    selectedLabelColor = colors.onPrimaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = colors.outlineVariant,
                    selectedBorderColor = colors.primary.copy(alpha = 0.4f),
                ),
            )
        }
    }
}

private fun chipLabel(label: String, count: Int, countColor: Color): AnnotatedString = buildAnnotatedString {
    append(label)
    append("  ")
    withStyle(SpanStyle(color = countColor)) { append(count.toString()) }
}

@Composable
private fun TransactionCard(summary: TransactionSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalStatusPalette.current
    val tone = palette.forStatus(summary.responseCode, summary.status)
    OutlineCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = tone.border,
        pressedBorderColor = tone.content,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusPill(summary.responseCode, summary.status)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = summary.method,
                        style = NetPilotMono.small.copy(fontWeight = FontWeight.SemiBold),
                        color = tone.content,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = summary.path,
                        style = NetPilotMono.medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = metaLine(summary, palette.clientError.content),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (summary.mockRuleName != null) Pill(stringResource(R.string.netpilot_mock_tag), palette.mock)
                }
            }
        }
    }
}

/** Host, time, duration and size; slow calls get their duration highlighted. */
private fun metaLine(summary: TransactionSummary, slowColor: Color): AnnotatedString = buildAnnotatedString {
    append(summary.host)
    append(META_SEPARATOR)
    append(Format.time(summary.requestDate))
    Format.duration(summary.tookMs)?.let { duration ->
        append(META_SEPARATOR)
        if ((summary.tookMs ?: 0) >= SLOW_REQUEST_MS) {
            withStyle(SpanStyle(color = slowColor, fontWeight = FontWeight.Medium)) { append(duration) }
        } else {
            append(duration)
        }
    }
    Format.size(summary.responseSize)?.let {
        append(META_SEPARATOR)
        append(it)
    }
}

@Composable
private fun ListActionsMenu(repository: TransactionRepository, onClear: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { isExpanded = true }) {
        Icon(painterResource(R.drawable.netpilot_ic_more), stringResource(R.string.netpilot_more))
    }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
        MenuItem(R.string.netpilot_export_har) {
            isExpanded = false
            scope.launch {
                val recent = withContext(Dispatchers.IO) { repository.getRecent(ShareActions.HAR_EXPORT_LIMIT) }
                ShareActions.shareHar(context, recent)
            }
        }
        MenuItem(R.string.netpilot_clear) {
            isExpanded = false
            onClear()
        }
    }
}
