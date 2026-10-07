package dev.mobile.netpilot.internal.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.Format
import dev.mobile.netpilot.internal.data.TransactionStatus
import kotlinx.coroutines.delay

private const val COPIED_FEEDBACK_MS = 1_500L
private const val SECTION_LABEL_TRACKING = 0.8
private const val KEY_COLUMN_WEIGHT = 0.36f
private const val VALUE_COLUMN_WEIGHT = 0.64f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetPilotTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.netpilot_ic_back), stringResource(R.string.netpilot_back))
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** Flat card with a 1dp outline; when clickable the outline deepens while pressed. */
@Composable
internal fun OutlineCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    pressedBorderColor: Color = MaterialTheme.colorScheme.outline,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
    if (onClick == null) {
        OutlinedCard(modifier = modifier, shape = shape, colors = colors, border = BorderStroke(1.dp, borderColor), content = content)
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedBorder by animateColorAsState(if (isPressed) pressedBorderColor else borderColor, label = "cardBorder")
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = colors,
        border = BorderStroke(1.dp, animatedBorder),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
internal fun Pill(text: String, tone: Tone, modifier: Modifier = Modifier, isMonospace: Boolean = false) {
    val base = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    Text(
        text = text,
        style = if (isMonospace) base.copy(fontFamily = FontFamily.Monospace) else base,
        color = tone.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(tone.container, CircleShape)
            .border(1.dp, tone.border, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun StatusPill(responseCode: Int?, status: TransactionStatus, modifier: Modifier = Modifier) {
    val tone = LocalStatusPalette.current.forStatus(responseCode, status)
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .widthIn(min = 52.dp)
            .background(tone.container, shape)
            .border(1.dp, tone.border, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Format.statusLabel(responseCode, status),
            style = NetPilotMono.medium.copy(fontWeight = FontWeight.SemiBold),
            color = tone.content,
        )
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = SECTION_LABEL_TRACKING.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlineCard(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(title, Modifier.weight(1f))
            action?.invoke()
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), content = content)
    }
}

/** Label and value side by side, separated from the next row by a hairline. */
@Composable
internal fun KeyValueRow(label: String, value: String, isMonospace: Boolean = false, showDivider: Boolean = true) {
    val style = if (isMonospace) NetPilotMono.small else MaterialTheme.typography.bodySmall
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(KEY_COLUMN_WEIGHT))
        SelectionContainer(Modifier.weight(VALUE_COLUMN_WEIGHT)) {
            Text(value, style = style, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = NetPilotMono.medium.copy(fontWeight = FontWeight.Medium),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            maxLines = 1,
        )
    }
}

@Composable
internal fun EmptyState(@DrawableRes icon: Int, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MenuItem(@StringRes label: Int, isEnabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick, enabled = isEnabled)
}

/** Copies [text] and briefly turns into a check mark. */
@Composable
internal fun CopyButton(text: String, label: String) {
    val context = LocalContext.current
    val successColor = LocalStatusPalette.current.success.content
    var isCopied by remember { mutableStateOf(false) }
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(COPIED_FEEDBACK_MS)
            isCopied = false
        }
    }
    IconButton(onClick = {
        ShareActions.copyToClipboard(context, label, text)
        isCopied = true
    }) {
        AnimatedContent(targetState = isCopied, label = "copyFeedback") { copied ->
            Icon(
                painter = painterResource(if (copied) R.drawable.netpilot_ic_check else R.drawable.netpilot_ic_copy),
                contentDescription = stringResource(R.string.netpilot_copy_body),
                tint = if (copied) successColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
