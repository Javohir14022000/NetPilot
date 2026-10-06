package dev.mobile.netpilot.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.Format
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.data.TransactionSummary
import dev.mobile.netpilot.internal.data.observeSummaries

private const val BADGE_BACKGROUND_ALPHA = 0.15f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionListScreen(
    repository: TransactionRepository,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val summaries by remember(query) { repository.observeSummaries(query) }
        .collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.netpilot_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.netpilot_ic_back), stringResource(R.string.netpilot_back))
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(painterResource(R.drawable.netpilot_ic_delete), stringResource(R.string.netpilot_clear))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.netpilot_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val items = summaries
            when {
                items == null -> Unit
                items.isEmpty() -> CenteredMessage(stringResource(R.string.netpilot_empty))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { summary ->
                        TransactionRow(summary, onClick = { onOpen(summary.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(summary: TransactionSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusBadge(summary)
        Column(Modifier.weight(1f)) {
            Text(
                text = "${summary.method} ${summary.path}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    Format.time(summary.requestDate),
                    Format.duration(summary.tookMs),
                    Format.size(summary.responseSize),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBadge(summary: TransactionSummary) {
    val color = statusColor(summary.responseCode, summary.status)
    Box(
        modifier = Modifier
            .widthIn(min = 48.dp)
            .background(color.copy(alpha = BADGE_BACKGROUND_ALPHA), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Format.statusLabel(summary.responseCode, summary.status),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
