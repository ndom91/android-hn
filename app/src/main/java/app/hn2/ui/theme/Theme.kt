package app.hn2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette (Android 8–11, no wallpaper colors), from a warm orange seed.
private val LightFallback = lightColorScheme(
    primary = Color(0xFF9A4521),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCE),
    onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFF77574B),
    secondaryContainer = Color(0xFFFFDBCE),
    onSecondaryContainer = Color(0xFF2C160C),
    tertiary = Color(0xFF695E2F),
    tertiaryContainer = Color(0xFFF2E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFF8F6),
    surface = Color(0xFFFFF8F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF6E5DE),
    surfaceContainerHighest = Color(0xFFF1DFD9),
    onSurface = Color(0xFF231A16),
    onSurfaceVariant = Color(0xFF53433E),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BA),
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFFFB599),
    onPrimary = Color(0xFF5C1A00),
    primaryContainer = Color(0xFF7B2E0B),
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFFE7BEAE),
    secondaryContainer = Color(0xFF5D4034),
    onSecondaryContainer = Color(0xFFFFDBCE),
    tertiary = Color(0xFFD5C68E),
    tertiaryContainer = Color(0xFF51461A),
    onTertiaryContainer = Color(0xFFF2E2A7),
    background = Color(0xFF1A110E),
    surface = Color(0xFF1A110E),
    surfaceContainerLowest = Color(0xFF140C09),
    surfaceContainerLow = Color(0xFF231A16),
    surfaceContainer = Color(0xFF271E1A),
    surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D322E),
    onSurface = Color(0xFFF1DFD9),
    onSurfaceVariant = Color(0xFFD8C2BA),
    outline = Color(0xFFA08D86),
    outlineVariant = Color(0xFF53433E),
)

/** Thread line colors for reply depth 1, 2, 3, …, taken from the current scheme. */
fun ColorScheme.threadColors(): List<Color> = listOf(primary, tertiary, secondary, outline)

@Composable
fun Hn2Theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = colors, content = content)
}
