package de.bewerbo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------------------------
// The design system's token layer.
//
// Being able to point at #0E3A53 by name is what separates a considered product from default
// Material blue. Dark theme and the semantic colours live here from the start rather than being
// retrofitted, because retrofitting them means touching every screen.
// ---------------------------------------------------------------------------------------------

object BewerboColors {
    val Primary = Color(0xFF0E3A53)      // petrol
    val PrimaryHi = Color(0xFF14506F)
    val Accent = Color(0xFF1668A6)
    val Success = Color(0xFF146B46)
    val Attention = Color(0xFFD9A227)
    val Error = Color(0xFF9C2530)
    val Ink = Color(0xFF0E1620)
    val Canvas = Color(0xFFE8EBF0)

    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF4F6F9)
    val Outline = Color(0xFFC7D2DA)
    val Muted = Color(0xFF5A6B78)

    // The tinted backgrounds the status pills and callouts sit on. Derived once here so no screen
    // invents its own alpha.
    val SuccessTint = Color(0xFFE6F2EB)
    val AttentionTint = Color(0xFFFDF4E0)
    val ErrorTint = Color(0xFFFAE8EA)
    val AccentTint = Color(0xFFE7F0F8)
}

private val LightScheme = lightColorScheme(
    primary = BewerboColors.Primary,
    onPrimary = Color.White,
    primaryContainer = BewerboColors.AccentTint,
    onPrimaryContainer = BewerboColors.Primary,
    secondary = BewerboColors.Accent,
    onSecondary = Color.White,
    background = BewerboColors.Canvas,
    onBackground = BewerboColors.Ink,
    surface = BewerboColors.Surface,
    onSurface = BewerboColors.Ink,
    surfaceVariant = BewerboColors.SurfaceMuted,
    onSurfaceVariant = BewerboColors.Muted,
    outline = BewerboColors.Outline,
    error = BewerboColors.Error,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FB3D5),
    onPrimary = Color(0xFF06212F),
    primaryContainer = Color(0xFF13384D),
    onPrimaryContainer = Color(0xFFD6E7F2),
    secondary = Color(0xFF6FA8D6),
    onSecondary = Color(0xFF05202F),
    background = Color(0xFF0B1219),
    onBackground = Color(0xFFE4EAEF),
    surface = Color(0xFF111A22),
    onSurface = Color(0xFFE4EAEF),
    surfaceVariant = Color(0xFF17232D),
    onSurfaceVariant = Color(0xFF9DB0BE),
    outline = Color(0xFF32444F),
    error = Color(0xFFE08B93),
    onError = Color(0xFF3A0A0F),
)

/// The five-step UI scale. The document's serif is not here — it lives in the PDF renderer, which
/// is the only place that decides how the document looks.
private val BewerboTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 25.sp, lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
    ),
)

/// The 8 dp grid, named so a screen asks for a step rather than a number.
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

/// One elevation step for cards. More than one and the hierarchy stops meaning anything.
val CardElevation = 1.dp

private val BewerboShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

/// The semantic colours, reachable from any composable without threading them through.
data class SemanticColors(
    val success: Color,
    val successTint: Color,
    val attention: Color,
    val attentionTint: Color,
    val danger: Color,
    val dangerTint: Color,
    val accent: Color,
    val accentTint: Color,
    val muted: Color,
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        BewerboColors.Success, BewerboColors.SuccessTint,
        BewerboColors.Attention, BewerboColors.AttentionTint,
        BewerboColors.Error, BewerboColors.ErrorTint,
        BewerboColors.Accent, BewerboColors.AccentTint,
        BewerboColors.Muted,
    )
}

@Composable
fun BewerboTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val semantic = if (darkTheme) {
        SemanticColors(
            Color(0xFF6FBF95), Color(0xFF10291F),
            Color(0xFFE5BC5C), Color(0xFF2B2413),
            Color(0xFFE08B93), Color(0xFF2E1418),
            Color(0xFF6FA8D6), Color(0xFF122633),
            Color(0xFF9DB0BE),
        )
    } else {
        SemanticColors(
            BewerboColors.Success, BewerboColors.SuccessTint,
            BewerboColors.Attention, BewerboColors.AttentionTint,
            BewerboColors.Error, BewerboColors.ErrorTint,
            BewerboColors.Accent, BewerboColors.AccentTint,
            BewerboColors.Muted,
        )
    }

    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = BewerboTypography,
            shapes = BewerboShapes,
            content = content,
        )
    }
}
