package app.hn2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Close to Materialistic's dark theme: near-black cards, a salmon accent.
private val Salmon = Color(0xFFFF8A80)
private val Red = Color(0xFFD32F2F)

private val DarkColors = darkColorScheme(
    primary = Salmon,
    onPrimary = Color(0xFF3B0907),
    secondary = Salmon,
    background = Color(0xFF1A1A1A),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceContainer = Color(0xFF242424),
    outlineVariant = Color(0xFF2E2E2E),
)

private val LightColors = lightColorScheme(
    primary = Red,
    secondary = Red,
    background = Color(0xFFF2F2F2),
    surface = Color.White,
    surfaceVariant = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFFAFAFA),
    onSurfaceVariant = Color(0xFF616161),
    outlineVariant = Color(0xFFE0E0E0),
)

/** Points/comments over the hot threshold, as in the widget. */
val HotOrange = Color(0xFFFFA726)

/** Thread-depth bar colors, cycled. */
val DepthColors = listOf(
    Color(0xFFF44336),
    Color(0xFFD500F9),
    Color(0xFF304FFE),
    Color(0xFF0091EA),
    Color(0xFF009688),
    Color(0xFF00C853),
    Color(0xFFFFA726),
    Color(0xFF8D6E63),
)

@Composable
fun Hn2Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
