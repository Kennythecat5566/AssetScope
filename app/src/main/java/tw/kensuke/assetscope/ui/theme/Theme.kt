package tw.kensuke.assetscope.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF173D2B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCEE9D7),
    secondary = Color(0xFF8B5D12),
    secondaryContainer = Color(0xFFFFDFA8),
    background = Color(0xFFF7F9F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E4DE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADD2B9),
    onPrimary = Color(0xFF123723),
    secondary = Color(0xFFF1C16C),
    background = Color(0xFF101411),
    surface = Color(0xFF181D19),
)

@Composable
fun AssetScopeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

