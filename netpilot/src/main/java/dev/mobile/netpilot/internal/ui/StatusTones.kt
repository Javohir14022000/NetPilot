package dev.mobile.netpilot.internal.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.mobile.netpilot.internal.data.TransactionStatus
import dev.mobile.netpilot.internal.mock.MockOutcome
import dev.mobile.netpilot.internal.mock.MockRule

private const val HTTP_REDIRECT = 300
private const val HTTP_CLIENT_ERROR = 400
private const val HTTP_SERVER_ERROR = 500

/** A designed trio: text/icon color, a faint fill and a pastel outline. */
@Immutable
internal data class Tone(val content: Color, val container: Color, val border: Color)

@Immutable
internal data class StatusPalette(
    val success: Tone,
    val redirect: Tone,
    val clientError: Tone,
    val serverError: Tone,
    val neutral: Tone,
    val mock: Tone,
)

internal val LightStatusPalette = StatusPalette(
    success = Tone(Color(0xFF15803D), Color(0xFFF0FDF4), Color(0xFFBBF7D0)),
    redirect = Tone(Color(0xFF1D4ED8), Color(0xFFEFF6FF), Color(0xFFBFDBFE)),
    clientError = Tone(Color(0xFFB45309), Color(0xFFFFFBEB), Color(0xFFFDE68A)),
    serverError = Tone(Color(0xFFB91C1C), Color(0xFFFEF2F2), Color(0xFFFECACA)),
    neutral = Tone(Color(0xFF52525B), Color(0xFFF4F4F5), Color(0xFFE4E4E7)),
    mock = Tone(Color(0xFF6D28D9), Color(0xFFF5F3FF), Color(0xFFDDD6FE)),
)

internal val DarkStatusPalette = StatusPalette(
    success = Tone(Color(0xFF4ADE80), Color(0xFF0C1F14), Color(0xFF14532D)),
    redirect = Tone(Color(0xFF60A5FA), Color(0xFF0B1A33), Color(0xFF1E3A8A)),
    clientError = Tone(Color(0xFFFBBF24), Color(0xFF22190A), Color(0xFF78350F)),
    serverError = Tone(Color(0xFFF87171), Color(0xFF240E0E), Color(0xFF7F1D1D)),
    neutral = Tone(Color(0xFFA1A1AA), Color(0xFF141416), Color(0xFF3F3F46)),
    mock = Tone(Color(0xFFC4B5FD), Color(0xFF1A1330), Color(0xFF4C1D95)),
)

internal val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }

internal fun StatusPalette.forStatus(responseCode: Int?, status: TransactionStatus): Tone = when {
    status == TransactionStatus.FAILED -> serverError
    status == TransactionStatus.IN_PROGRESS || responseCode == null -> neutral
    responseCode >= HTTP_SERVER_ERROR -> serverError
    responseCode >= HTTP_CLIENT_ERROR -> clientError
    responseCode >= HTTP_REDIRECT -> redirect
    else -> success
}

/** A rule is colored by what it makes the app see. */
internal fun StatusPalette.forRule(rule: MockRule): Tone = when (rule.outcome) {
    MockOutcome.RESPOND -> forStatus(rule.statusCode, TransactionStatus.COMPLETE)
    MockOutcome.NO_INTERNET, MockOutcome.TIMEOUT -> clientError
}
