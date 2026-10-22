package com.budgetsphere.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue

val Emerald = Color(0xFF047857)
val Navy = Color(0xFF14213D)
private val LightColors = lightColorScheme(primary=Emerald, onPrimary=Color.White, secondary=Navy, background=Color(0xFFF8FAFC), surface=Color.White, surfaceVariant=Color(0xFFE8EEF2), error=Color(0xFFB91C1C))
private val DarkColors = darkColorScheme(primary=Color(0xFF34D399), onPrimary=Color(0xFF003827), secondary=Color(0xFFB8C8EB), background=Color(0xFF0D1526), surface=Color(0xFF14213D), surfaceVariant=Color(0xFF24334C), error=Color(0xFFFFB4AB))
@Composable fun BudgetSphereTheme(mode: String = "System", accent: Color?=null, content: @Composable () -> Unit) {
    val dark = when(mode) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val primary by animateColorAsState(if(accent==null){if(dark)DarkColors.primary else LightColors.primary}else if(dark)lerp(accent,Color.White,0.45f)else accent,label="Workspace accent")
    MaterialTheme(colorScheme=if(dark)DarkColors.copy(primary=primary)else LightColors.copy(primary=primary), content=content)
}
