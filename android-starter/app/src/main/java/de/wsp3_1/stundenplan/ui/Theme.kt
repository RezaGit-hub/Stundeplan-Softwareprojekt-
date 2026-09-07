package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Bedeutungsfarben aus backbone/ui.md. Farbe ist nie alleiniger Traeger einer
 * Bedeutung - jede Karte bekommt zusaetzlich ein Textlabel.
 */
object PlanColors {
    val SubstitutionLight = Color(0xFFEF6C00)
    val SubstitutionFgLight = Color(0xFFA64B00)
    val ConflictLight = Color(0xFFD32F2F)
    val PinnedLight = Color(0xFF424242)
    val CancelledLight = Color(0xFF9E9E9E)
    val GivenAwayLight = Color(0xFF757575)
    val OfflineBgLight = Color(0xFFECEFF1)
    val OfflineFgLight = Color(0xFF37474F)

    val SubstitutionDark = Color(0xFFFF9800)
    val SubstitutionFgDark = Color(0xFFFFB74D)
    val ConflictDark = Color(0xFFEF5350)
    val PinnedDark = Color(0xFFA1A1AA)
    val CancelledDark = Color(0xFF9E9E9E)
    val GivenAwayDark = Color(0xFF757575)
    val OfflineBgDark = Color(0xFF1B2126)
    val OfflineFgDark = Color(0xFFCFD8DC)
}

private val Primary = Color(0xFF6E56CF)
private val PrimaryDark = Color(0xFFB3A0F5)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    error = PlanColors.ConflictLight,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFF1A1226),
    error = PlanColors.ConflictDark,
)

@Composable
fun StundenplanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** Rahmen- und Badge-Farbe je Bedeutung; null bei REGULAR. */
@Composable
fun toneColor(tone: Tone): Color? {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        Tone.NONE -> null
        Tone.SUBSTITUTION -> if (dark) PlanColors.SubstitutionDark else PlanColors.SubstitutionLight
        Tone.CANCELLED -> if (dark) PlanColors.CancelledDark else PlanColors.CancelledLight
        Tone.GIVEN_AWAY -> if (dark) PlanColors.GivenAwayDark else PlanColors.GivenAwayLight
    }
}

/** Textfarbe fuer das Label unter der Karte. */
@Composable
fun toneTextColor(tone: Tone): Color {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        Tone.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        Tone.SUBSTITUTION -> if (dark) PlanColors.SubstitutionFgDark else PlanColors.SubstitutionFgLight
        Tone.CANCELLED -> if (dark) PlanColors.CancelledDark else PlanColors.CancelledLight
        Tone.GIVEN_AWAY -> if (dark) PlanColors.GivenAwayDark else PlanColors.GivenAwayLight
    }
}

@Composable
fun offlineBannerColors(): Pair<Color, Color> =
    if (isSystemInDarkTheme()) PlanColors.OfflineBgDark to PlanColors.OfflineFgDark
    else PlanColors.OfflineBgLight to PlanColors.OfflineFgLight

/** Fachfarbe "#1976D2" -> Color; bei unlesbarem Wert die Primaerfarbe. */
fun parseHexColor(hex: String?, fallback: Color): Color {
    val digits = hex?.trim()?.removePrefix("#") ?: return fallback
    if (digits.length != 6) return fallback
    return try {
        Color(("FF$digits").toLong(16))
    } catch (e: NumberFormatException) {
        fallback
    }
}
