package dev.mobile.netpilot.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

private const val DISABLED_RULE_ALPHA = 0.55f
private const val ACTIVE_BORDER_ALPHA = 0.35f

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
    val activeCount = rules.count { it.isEnabled }

    Scaffold(
        topBar = {
            NetPilotTopBar(
                title = stringResource(R.string.netpilot_mocks),
                subtitle = stringResource(R.string.netpilot_mocks_active, activeCount, rules.size),
                onBack = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(painterResource(R.drawable.netpilot_ic_add), null) },
                text = { Text(stringResource(R.string.netpilot_new_rule)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "switch") {
                MockingSwitchCard(isMockingEnabled) { checked -> write { setMockingEnabled(checked) } }
            }
            if (rules.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.netpilot_ic_mock,
                        title = stringResource(R.string.netpilot_mocks_empty_title),
                        message = stringResource(R.string.netpilot_mocks_empty),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(rules, key = { it.id }) { rule ->
                MockRuleCard(
                    rule = rule,
                    modifier = Modifier.animateItem(),
                    onClick = { onEdit(rule.id) },
                    onToggle = { isEnabled -> write { save(rule.copy(isEnabled = isEnabled)) } },
                )
            }
        }
    }
}

@Composable
private fun MockingSwitchCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlineCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isEnabled) colors.primary.copy(alpha = ACTIVE_BORDER_ALPHA) else colors.outlineVariant,
        containerColor = if (isEnabled) colors.primaryContainer else colors.surfaceContainerLowest,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.netpilot_mocking_enabled), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.netpilot_mocking_enabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(checked = isEnabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun MockRuleCard(rule: MockRule, modifier: Modifier, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val palette = LocalStatusPalette.current
    val tone = palette.forRule(rule)
    OutlineCard(
        modifier = modifier.fillMaxWidth().alpha(if (rule.isEnabled) 1f else DISABLED_RULE_ALPHA),
        borderColor = if (rule.isEnabled) tone.border else MaterialTheme.colorScheme.outlineVariant,
        pressedBorderColor = tone.content,
        onClick = onClick,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(rule.method ?: MockRuleDraft.ANY_METHOD, palette.neutral, isMonospace = true)
                    Text(
                        text = rule.urlPattern,
                        style = NetPilotMono.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("→ ${outcomeSummary(rule)}", tone)
                    if (rule.delayMillis > 0) Pill("${rule.delayMillis} ms", palette.neutral)
                    if (rule.isRegex) Pill("regex", palette.neutral)
                    if (rule.priority != 0) Pill("priority ${rule.priority}", palette.neutral)
                }
            }
            Switch(checked = rule.isEnabled, onCheckedChange = onToggle)
        }
    }
}

internal fun outcomeTitle(outcome: MockOutcome): String = when (outcome) {
    MockOutcome.RESPOND -> "Respond"
    MockOutcome.NO_INTERNET -> "No internet"
    MockOutcome.TIMEOUT -> "Timeout"
}

private fun outcomeSummary(rule: MockRule): String = when (rule.outcome) {
    MockOutcome.RESPOND -> "${rule.statusCode} ${HttpStatus.reasonPhrase(rule.statusCode)}".trim()
    else -> outcomeTitle(rule.outcome)
}
