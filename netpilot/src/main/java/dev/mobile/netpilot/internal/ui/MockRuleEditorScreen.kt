@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mobile.netpilot.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.mock.DraftField
import dev.mobile.netpilot.internal.mock.DraftResult
import dev.mobile.netpilot.internal.mock.MockOutcome
import dev.mobile.netpilot.internal.mock.MockRule
import dev.mobile.netpilot.internal.mock.MockRuleDraft
import dev.mobile.netpilot.internal.mock.MockRuleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val URL_PATTERN_HINT = "Glob: * matches anything, e.g. */posts/* or /users/1"
private const val BODY_MIN_LINES = 6
private const val MULTILINE_MIN_LINES = 3

/** Loads the starting draft (existing rule, captured response or empty) off the main thread. */
@Composable
internal fun MockRuleEditorRoute(
    store: MockRuleStore,
    repository: TransactionRepository,
    ruleId: Long?,
    fromTransactionId: Long?,
    onDone: () -> Unit,
) {
    val initialDraft by produceState<MockRuleDraft?>(initialValue = null, ruleId, fromTransactionId) {
        value = withContext(Dispatchers.IO) { loadDraft(store, repository, ruleId, fromTransactionId) }
    }
    val scope = rememberCoroutineScope()
    val draft = initialDraft ?: return

    MockRuleEditorScreen(
        initial = draft,
        isExisting = ruleId != null,
        onBack = onDone,
        onSave = { rule ->
            scope.launch {
                withContext(Dispatchers.IO) { store.save(rule) }
                onDone()
            }
        },
        onDelete = {
            scope.launch {
                withContext(Dispatchers.IO) { store.delete(draft.id) }
                onDone()
            }
        },
    )
}

private fun loadDraft(
    store: MockRuleStore,
    repository: TransactionRepository,
    ruleId: Long?,
    fromTransactionId: Long?,
): MockRuleDraft {
    val loaded = when {
        ruleId != null -> store.rules.value.find { it.id == ruleId }?.let { MockRuleDraft.fromRule(it) }
        fromTransactionId != null -> repository.get(fromTransactionId)?.let { MockRuleDraft.fromTransaction(it) }
        else -> null
    }
    return loaded ?: MockRuleDraft()
}

@Composable
private fun MockRuleEditorScreen(
    initial: MockRuleDraft,
    isExisting: Boolean,
    onBack: () -> Unit,
    onSave: (MockRule) -> Unit,
    onDelete: () -> Unit,
) {
    // The activity handles configuration changes itself, so plain remember survives rotation
    // without pushing a possibly large body through saved instance state.
    var draft by remember(initial) { mutableStateOf(initial) }
    var errors by remember { mutableStateOf<Map<DraftField, String>>(emptyMap()) }
    val save: () -> Unit = {
        when (val result = draft.validate()) {
            is DraftResult.Valid -> onSave(result.rule)
            is DraftResult.Invalid -> errors = result.errors
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isExisting) R.string.netpilot_edit_mock else R.string.netpilot_new_mock)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.netpilot_ic_back), stringResource(R.string.netpilot_back))
                    }
                },
                actions = {
                    if (isExisting) {
                        IconButton(onClick = onDelete) {
                            Icon(painterResource(R.drawable.netpilot_ic_delete), stringResource(R.string.netpilot_delete))
                        }
                    }
                    TextButton(onClick = save) { Text(stringResource(R.string.netpilot_save)) }
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
            FormField("Name", draft.name, hint = "Optional, defaults to method + URL pattern") {
                draft = draft.copy(name = it)
            }
            ChoiceRow(
                title = "Method",
                options = listOf<String?>(null) + MockRuleDraft.METHODS,
                selected = draft.method,
                label = { it ?: MockRuleDraft.ANY_METHOD },
            ) { draft = draft.copy(method = it) }
            FormField(
                label = "URL pattern",
                value = draft.urlPattern,
                error = errors[DraftField.URL_PATTERN],
                hint = URL_PATTERN_HINT,
                keyboardType = KeyboardType.Uri,
            ) { draft = draft.copy(urlPattern = it) }
            LabeledSwitch("Regular expression", draft.isRegex) { draft = draft.copy(isRegex = it) }
            ChoiceRow(
                title = "Result",
                options = MockOutcome.entries,
                selected = draft.outcome,
                label = ::outcomeTitle,
            ) { draft = draft.copy(outcome = it) }
            if (draft.outcome == MockOutcome.RESPOND) {
                ResponseFields(draft, errors) { draft = it }
            }
            FormField(
                label = "Delay (ms)",
                value = draft.delayMillis,
                error = errors[DraftField.DELAY],
                keyboardType = KeyboardType.Number,
            ) { draft = draft.copy(delayMillis = it) }
            FormField(
                label = "Priority",
                value = draft.priority,
                error = errors[DraftField.PRIORITY],
                hint = "Higher priority rules are checked first",
                keyboardType = KeyboardType.Number,
            ) { draft = draft.copy(priority = it) }
            LabeledSwitch("Enabled", draft.isEnabled) { draft = draft.copy(isEnabled = it) }
        }
    }
}

@Composable
private fun ResponseFields(
    draft: MockRuleDraft,
    errors: Map<DraftField, String>,
    onChange: (MockRuleDraft) -> Unit,
) {
    FormField(
        label = "Status code",
        value = draft.statusCode,
        error = errors[DraftField.STATUS_CODE],
        keyboardType = KeyboardType.Number,
    ) { onChange(draft.copy(statusCode = it)) }
    FormField("Content-Type", draft.contentType) { onChange(draft.copy(contentType = it)) }
    FormField(
        label = "Headers",
        value = draft.headers,
        error = errors[DraftField.HEADERS],
        hint = "One per line, e.g. X-Request-Id: 42",
        minLines = MULTILINE_MIN_LINES,
    ) { onChange(draft.copy(headers = it)) }
    FormField(
        label = "Body",
        value = draft.body,
        minLines = BODY_MIN_LINES,
        isMonospace = true,
    ) { onChange(draft.copy(body = it)) }
}
