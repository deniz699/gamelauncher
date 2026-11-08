package com.gamelauncher.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

val accents = listOf(
    Color(0xFF6750A4), Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
    Color(0xFFFB8C00), Color(0xFFD81B60), Color(0xFF00ACC1)
)

@Composable
fun LauncherTheme(mode: Int, accent: Int, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2, 3 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    var scheme = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val a = accents[accent.coerceIn(accents.indices)]
        if (dark) {
            val l = lerp(a, Color.White, 0.35f)
            darkColorScheme(primary = l, secondary = l, tertiary = l, primaryContainer = lerp(a, Color.Black, 0.5f))
        } else {
            lightColorScheme(primary = a, secondary = a, tertiary = a, primaryContainer = lerp(a, Color.White, 0.8f))
        }
    }
    if (mode == 3) scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF0E0E0E))
    MaterialTheme(colorScheme = scheme, content = content)
}
