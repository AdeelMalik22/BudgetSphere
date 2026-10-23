package com.budgetsphere.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Emerald = Color(0xFF1F9D78)
val Navy = Color(0xFF18233A)
val Ink = Color(0xFF172033)
val Mist = Color(0xFFF4F7F5)
val Cloud = Color(0xFFE8EFEC)
val Coral = Color(0xFFE56B58)
val Lavender = Color(0xFF7668D6)

private val LightColors =
    lightColorScheme(
        primary = Emerald,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD7F3E9),
        onPrimaryContainer = Color(0xFF073D2C),
        secondary = Navy,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0E8F5),
        onSecondaryContainer = Ink,
        tertiary = Lavender,
        onTertiary = Color.White,
        background = Mist,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Cloud,
        onSurfaceVariant = Color(0xFF617069),
        outline = Color(0xFFD4DED9),
        error = Color(0xFFB6473B),
        errorContainer = Color(0xFFFFE2DD),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF61D6AD),
        onPrimary = Color(0xFF073D2C),
        primaryContainer = Color(0xFF155E49),
        onPrimaryContainer = Color(0xFFB9F6DE),
        secondary = Color(0xFFAFC3E8),
        onSecondary = Color(0xFF1D2A43),
        secondaryContainer = Color(0xFF2A3A5B),
        onSecondaryContainer = Color(0xFFDCE7FF),
        tertiary = Color(0xFFBBB1FF),
        onTertiary = Color(0xFF29215E),
        background = Color(0xFF101722),
        onBackground = Color(0xFFE8F0EC),
        surface = Color(0xFF172130),
        onSurface = Color(0xFFE8F0EC),
        surfaceVariant = Color(0xFF253448),
        onSurfaceVariant = Color(0xFFB2C1BB),
        outline = Color(0xFF42564F),
        error = Color(0xFFFFB4A9),
        errorContainer = Color(0xFF6A2C25),
    )

@Composable
fun BudgetSphereTheme(
    mode: String = "System",
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, dark) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
    val primary by animateColorAsState(
        if (accent == null) {
            if (dark) DarkColors.primary else LightColors.primary
        } else if (dark) lerp(accent, Color.White, 0.45f) else accent,
        label = "Workspace accent",
    )
    MaterialTheme(
        colorScheme = if (dark) DarkColors.copy(primary = primary) else LightColors.copy(primary = primary),
        typography = Typography().withBudgetSphereScale(),
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10),
            small = androidx.compose.foundation.shape.RoundedCornerShape(14),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(20),
            large = androidx.compose.foundation.shape.RoundedCornerShape(26),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32),
        ),
        content = content,
    )
}

private fun Typography.withBudgetSphereScale() = copy(
    displayLarge = displayLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    headlineLarge = headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    headlineMedium = headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    titleLarge = titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    bodyLarge = bodyLarge.copy(lineHeight = bodyLarge.lineHeight * 1.15f),
    labelLarge = labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
)
