package com.kevinconnors.scorestand.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Navy = Color(0xFF1E3A5F)
private val Brass = Color(0xFFC9A227)

private val LightColors = lightColorScheme(primary = Navy, secondary = Brass, tertiary = Color(0xFF7A4E2D))
private val DarkColors = darkColorScheme(primary = Color(0xFF9FC3EE), secondary = Brass, tertiary = Color(0xFFE0B48F))

/** Neutral grey around pages so white paper stands out in both themes. */
val ReaderBackground = Color(0xFF2B2B2E)

@Composable
fun ScoreStandTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
