package dev.mobile.netpilot.internal.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.mobile.netpilot.internal.data.TransactionStatus

private const val HTTP_REDIRECT = 300
private const val HTTP_CLIENT_ERROR = 400
private const val HTTP_SERVER_ERROR = 500

private val SuccessColor = Color(0xFF2E7D32)
private val RedirectColor = Color(0xFF1565C0)
private val ClientErrorColor = Color(0xFFEF6C00)
private val ServerErrorColor = Color(0xFFC62828)
private val PendingColor = Color(0xFF757575)

@Composable
internal fun NetPilotTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

internal fun statusColor(responseCode: Int?, status: TransactionStatus): Color = when {
    status == TransactionStatus.FAILED -> ServerErrorColor
    status == TransactionStatus.IN_PROGRESS || responseCode == null -> PendingColor
    responseCode >= HTTP_SERVER_ERROR -> ServerErrorColor
    responseCode >= HTTP_CLIENT_ERROR -> ClientErrorColor
    responseCode >= HTTP_REDIRECT -> RedirectColor
    else -> SuccessColor
}
