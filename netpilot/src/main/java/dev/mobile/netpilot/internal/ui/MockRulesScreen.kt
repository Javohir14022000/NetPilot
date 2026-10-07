package dev.mobile.netpilot.internal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.mock.HttpStatus
import dev.mobile.netpilot.internal.mock.MockOutcome
import dev.mobile.netpilot.internal.mock.MockRule
import dev.mobile.netpilot.internal.mock.MockRuleDraft
import dev.mobile.netpilot.internal.mock.MockRuleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MockRulesScreen(
    store: MockRuleStore,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val rules by store.rules.collectAsState()
    val isMockingEnabled by store.isMockingEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    val write: (MockRuleStore.() -> Unit) -> Unit = { action -> scope.launch(Dispatchers.IO) { store.action() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.netpilot_mocks)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.netpilot_ic_back), stringResource(R.string.netpilot_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.netpilot_ic_add), stringResource(R.string.netpilot_add_mock))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.netpilot_mocking_enabled)) },
                    supportingContent = { Text(stringResource(R.string.netpilot_mocking_enabled_hint)) },
                    trailingContent = {
                        Switch(checked = isMockingEnabled, onCheckedChange = { checked -> write { setMockingEnabled(checked) } })
                    },
                )
                HorizontalDivider()
            }
            if (rules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.netpilot_mocks_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(rules, key = { it.id }) { rule ->
                MockRuleRow(
                    rule = rule,
                    onClick = { onEdit(rule.id) },
                    onToggle = { isEnabled -> write { save(rule.copy(isEnabled = isEnabled)) } },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MockRuleRow(rule: MockRule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(rule.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = "${rule.method ?: MockRuleDraft.ANY_METHOD} ${rule.urlPattern}\n→ ${rule.outcomeSummary()}",
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Switch(checked = rule.isEnabled, onCheckedChange = onToggle) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

internal fun outcomeTitle(outcome: MockOutcome): String = when (outcome) {
    MockOutcome.RESPOND -> "Respond"
    MockOutcome.NO_INTERNET -> "No internet"
    MockOutcome.TIMEOUT -> "Timeout"
}

private fun MockRule.outcomeSummary(): String {
    val result = when (outcome) {
        MockOutcome.RESPOND -> "$statusCode ${HttpStatus.reasonPhrase(statusCode)}".trim()
        else -> outcomeTitle(outcome)
    }
    return if (delayMillis > 0) "$result · $delayMillis ms" else result
}
