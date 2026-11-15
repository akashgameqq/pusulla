package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.data.local.ThemeAccent

private fun accentPrimaryColor(accent: ThemeAccent, dark: Boolean): Color = when (accent) {
    ThemeAccent.EMERALD -> if (dark) EmeraldPrimary else EmeraldDeep
    ThemeAccent.CYAN -> if (dark) CyanTertiary else Color(0xFF0284C7)
    ThemeAccent.AMBER -> if (dark) AmberSecondary else Color(0xFFD97706)
    ThemeAccent.VIOLET -> if (dark) VioletAccent else Color(0xFF7C3AED)
    ThemeAccent.CORAL -> if (dark) Color(0xFFFB7185) else Color(0xFFE11D48)
}

private fun accentContainerColor(accent: ThemeAccent, dark: Boolean): Color = when (accent) {
    ThemeAccent.EMERALD -> if (dark) EmeraldContainerDark else EmeraldContainerLight
    ThemeAccent.CYAN -> if (dark) CyanContainerDark else CyanContainerLight
    ThemeAccent.AMBER -> if (dark) AmberContainerDark else AmberContainerLight
    ThemeAccent.VIOLET -> if (dark) Color(0xFF2E1065) else Color(0xFFEDE9FE)
    ThemeAccent.CORAL -> if (dark) CoralContainerDark else CoralContainerLight
}

private fun accentOnContainerColor(accent: ThemeAccent, dark: Boolean): Color = when (accent) {
    ThemeAccent.EMERALD -> if (dark) EmeraldLight else Color(0xFF064E3B)
    ThemeAccent.CYAN -> if (dark) Color(0xFFBAE6FD) else Color(0xFF0C4A6E)
    ThemeAccent.AMBER -> if (dark) AmberLight else Color(0xFF78350F)
    ThemeAccent.VIOLET -> if (dark) Color(0xFFDDD6FE) else Color(0xFF4C1D95)
    ThemeAccent.CORAL -> if (dark) Color(0xFFFECDD3) else Color(0xFF881337)
}

val PusulaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun TextStyle.scaled(factor: Float): TextStyle = copy(
    fontSize = fontSize * factor,
    lineHeight = lineHeight * factor,
)

private fun scaledTypography(factor: Float): Typography {
    if (factor in 0.99f..1.01f) return PusulaTypography
    return PusulaTypography.copy(
        displaySmall = PusulaTypography.displaySmall.scaled(factor),
        headlineMedium = PusulaTypography.headlineMedium.scaled(factor),
        headlineSmall = PusulaTypography.headlineSmall.scaled(factor),
        titleLarge = PusulaTypography.titleLarge.scaled(factor),
        titleMedium = PusulaTypography.titleMedium.scaled(factor),
        titleSmall = PusulaTypography.titleSmall.scaled(factor),
        bodyLarge = PusulaTypography.bodyLarge.scaled(factor),
        bodyMedium = PusulaTypography.bodyMedium.scaled(factor),
        bodySmall = PusulaTypography.bodySmall.scaled(factor),
        labelLarge = PusulaTypography.labelLarge.scaled(factor),
        labelMedium = PusulaTypography.labelMedium.scaled(factor),
        labelSmall = PusulaTypography.labelSmall.scaled(factor),
    )
}

@Composable
fun PusulaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeAccent: ThemeAccent = ThemeAccent.EMERALD,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val primary = accentPrimaryColor(themeAccent, darkTheme)
    val primaryContainer = accentContainerColor(themeAccent, darkTheme)
    val onPrimaryContainer = accentOnContainerColor(themeAccent, darkTheme)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF061612),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = AmberSecondary,
            onSecondary = Color(0xFF271300),
            secondaryContainer = AmberContainerDark,
            onSecondaryContainer = AmberLight,
            tertiary = CyanTertiary,
            onTertiary = Color(0xFF042437),
            tertiaryContainer = CyanContainerDark,
            onTertiaryContainer = Color(0xFFBAE6FD),
            background = SlateBgDark,
            onBackground = TextPrimaryDark,
            surface = SlateSurfaceDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = SlateSurfaceVariantDark,
            onSurfaceVariant = TextSecondaryDark,
            surfaceContainerHigh = SlateSurfaceElevatedDark,
            outline = SlateOutlineDark,
            error = CoralError,
            onError = Color.White,
            errorContainer = CoralContainerDark,
            onErrorContainer = Color(0xFFFDA4AF),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = AmberSecondary,
            onSecondary = Color.White,
            secondaryContainer = AmberContainerLight,
            onSecondaryContainer = Color(0xFF78350F),
            tertiary = Color(0xFF0284C7),
            onTertiary = Color.White,
            tertiaryContainer = CyanContainerLight,
            onTertiaryContainer = Color(0xFF0C4A6E),
            background = LinenBgLight,
            onBackground = TextPrimaryLight,
            surface = LinenSurfaceLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = LinenSurfaceVariantLight,
            onSurfaceVariant = TextSecondaryLight,
            surfaceContainerHigh = Color(0xFFF0F5F2),
            outline = LinenOutlineLight,
            error = Color(0xFFE11D48),
            onError = Color.White,
            errorContainer = CoralContainerLight,
            onErrorContainer = Color(0xFF881337),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(fontScale),
        shapes = PusulaShapes,
        content = content,
    )
}
