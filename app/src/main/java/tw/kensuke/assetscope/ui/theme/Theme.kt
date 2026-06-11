package tw.kensuke.assetscope.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
    primary = Color(0xFF5E6655),
    onPrimary = Color(0xFFFCFAF4),
    primaryContainer = Color(0xFFE8E8DC),
    onPrimaryContainer = Color(0xFF34382E),
    secondary = Color(0xFF9A6D55),
    onSecondary = Color(0xFFFFFBF6),
    secondaryContainer = Color(0xFFF0E1D5),
    onSecondaryContainer = Color(0xFF4B352A),
    tertiary = Color(0xFF7B8581),
    background = Color(0xFFF5F1E8),
    onBackground = Color(0xFF292824),
    surface = Color(0xFFFBF8F1),
    onSurface = Color(0xFF292824),
    surfaceVariant = Color(0xFFECE7DD),
    onSurfaceVariant = Color(0xFF706C64),
    outline = Color(0xFFC9C2B6),
    outlineVariant = Color(0xFFDED8CD),
    error = Color(0xFF9A4F43),
)

private val AssetScopeTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = 1.5.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        letterSpacing = 0.8.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val AssetScopeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AssetScopeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AssetScopeTypography,
        shapes = AssetScopeShapes,
        content = content,
    )
}
