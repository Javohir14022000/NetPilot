package dev.mobile.netpilot.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val SELECTED_BORDER_ALPHA = 0.4f

/** Form building blocks shared by the mock rule and request editors. */
@Composable
internal fun FormField(
    label: String,
    value: String,
    error: String? = null,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    isMonospace: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val supporting = error ?: hint
    val textStyle = LocalTextStyle.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = supporting?.let { text -> { Text(text) } },
        singleLine = minLines == 1,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = if (isMonospace) textStyle.copy(fontFamily = FontFamily.Monospace) else textStyle,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = colors.outlineVariant,
            focusedBorderColor = colors.primary,
            unfocusedContainerColor = colors.surfaceContainerLowest,
            focusedContainerColor = colors.surfaceContainerLowest,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
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
                        selectedBorderColor = colors.primary.copy(alpha = SELECTED_BORDER_ALPHA),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun LabeledSwitch(label: String, isChecked: Boolean, description: String? = null, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

/** Tinted callout: informational by default, red for errors. */
@Composable
internal fun Notice(text: String, isError: Boolean = false) {
    val palette = LocalStatusPalette.current
    val tone = if (isError) palette.serverError else palette.redirect
    val shape = MaterialTheme.shapes.medium
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = tone.content,
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.container, shape)
            .border(1.dp, tone.border, shape)
            .padding(12.dp),
    )
}
