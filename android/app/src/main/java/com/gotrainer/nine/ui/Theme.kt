package com.gotrainer.nine.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F6212),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9ED9E),
    onPrimaryContainer = Color(0xFF1A2600),
    secondary = Color(0xFF5A6146),
    secondaryContainer = Color(0xFFE0E7C6),
    tertiary = Color(0xFF386663),
    surfaceContainerLow = Color(0xFFF6F1E5),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFBDD98A),
    onPrimary = Color(0xFF1A2600),
    primaryContainer = Color(0xFF2E3B0A),
    onPrimaryContainer = Color(0xFFD9ED9E),
    secondary = Color(0xFFC4CBA8),
    surfaceContainerLow = Color(0xFF1D2013),
)

/**
 * System-following Material3 theme with dynamic color (minSdk 34: always
 * available on device). Falls back to static schemes when dynamic color is
 * unavailable (e.g. JVM screenshot tests under layoutlib).
 */
@Composable
fun goTrainerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = try {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } catch (_: Exception) {
        if (dark) DarkScheme else LightScheme
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
