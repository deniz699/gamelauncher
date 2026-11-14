package com.gamelauncher.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Vurgu renkleri: canlı, düz tonlar (gradyan yok). */
val accents = listOf(
    Color(0xFF7C5CFF), // mor
    Color(0xFFFFA11A), // amber
    Color(0xFFFF5C4D), // mercan
    Color(0xFF2FD6A3), // nane
    Color(0xFF3D9BFF), // gök
    Color(0xFFFF4F9A), // pembe
    Color(0xFFB6E836)  // lime
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Düz (flat) renk şeması: tüm yüzeyler vurgu renginin hafif tonlarıdır.
 * Gölge, gradyan, saydamlık ve bulanıklık kullanılmaz; katmanlar sadece ton farkıyla ayrılır.
 */
private fun flatScheme(a: Color, dark: Boolean, amoled: Boolean): ColorScheme {
    return if (dark) {
        val bg = if (amoled) Color.Black else lerp(Color(0xFF0A0812), a, 0.06f)
        fun tone(t: Float) = lerp(bg, a, t)
        val onA = if (a.luminance() > 0.45f) Color(0xFF111111) else Color.White
        darkColorScheme(
            primary = a, onPrimary = onA,
            primaryContainer = tone(0.30f), onPrimaryContainer = lerp(a, Color.White, 0.65f),
            secondary = a, onSecondary = onA,
            secondaryContainer = tone(0.20f), onSecondaryContainer = lerp(Color.White, a, 0.12f),
            tertiary = a, onTertiary = onA,
            background = bg, onBackground = Color(0xFFF2F0FA),
            surface = bg, onSurface = Color(0xFFF2F0FA),
            surfaceVariant = tone(0.12f), onSurfaceVariant = lerp(Color(0xFFA9A5BD), a, 0.08f),
            surfaceDim = bg, surfaceBright = tone(0.22f),
            surfaceContainerLowest = bg, surfaceContainerLow = tone(0.05f),
            surfaceContainer = tone(0.09f), surfaceContainerHigh = tone(0.14f), surfaceContainerHighest = tone(0.20f),
            outline = lerp(Color(0xFF8A869C), a, 0.10f), outlineVariant = tone(0.20f)
        )
    } else {
        val p = lerp(a, Color.Black, 0.12f)
        val bg = lerp(Color.White, a, 0.05f)
        fun tone(t: Float) = lerp(bg, a, t)
        val onP = if (p.luminance() > 0.45f) Color(0xFF111111) else Color.White
        lightColorScheme(
            primary = p, onPrimary = onP,
            primaryContainer = tone(0.22f), onPrimaryContainer = lerp(p, Color.Black, 0.45f),
            secondary = p, onSecondary = onP,
            secondaryContainer = tone(0.14f), onSecondaryContainer = Color(0xFF1B1830),
            tertiary = p, onTertiary = onP,
            background = bg, onBackground = Color(0xFF14111F),
            surface = bg, onSurface = Color(0xFF14111F),
            surfaceVariant = tone(0.10f), onSurfaceVariant = Color(0xFF5E5A70),
            surfaceDim = tone(0.12f), surfaceBright = Color.White,
            surfaceContainerLowest = Color.White, surfaceContainerLow = tone(0.03f),
            surfaceContainer = tone(0.07f), surfaceContainerHigh = tone(0.11f), surfaceContainerHighest = tone(0.16f),
            outline = Color(0xFF7D798F), outlineVariant = tone(0.18f)
        )
    }
}

/**
 * Liquid Glass şeması: her zaman koyu. Yüzeyler opak değil, yarı saydam beyaz katmanlardır;
 * arkadaki renkli ışık (GlassBackdrop) içlerinden görünür ve buzlu cam hissi verir.
 */
private fun glassScheme(a: Color): ColorScheme {
    val bg = Color(0xFF0B0A1E)
    fun w(x: Float) = Color.White.copy(alpha = x)
    val onA = if (a.luminance() > 0.45f) Color(0xFF111111) else Color.White
    return darkColorScheme(
        primary = a, onPrimary = onA,
        primaryContainer = a.copy(alpha = 0.32f), onPrimaryContainer = lerp(a, Color.White, 0.75f),
        secondary = a, onSecondary = onA,
        secondaryContainer = w(0.16f), onSecondaryContainer = Color.White,
        tertiary = a, onTertiary = onA,
        background = bg, onBackground = Color(0xFFF6F4FF),
        surface = bg, onSurface = Color(0xFFF6F4FF),
        surfaceVariant = w(0.10f), onSurfaceVariant = Color(0xFFB9B5D0),
        surfaceDim = bg, surfaceBright = w(0.20f),
        surfaceContainerLowest = w(0.04f), surfaceContainerLow = w(0.06f),
        surfaceContainer = w(0.09f), surfaceContainerHigh = w(0.13f), surfaceContainerHighest = w(0.19f),
        outline = w(0.35f), outlineVariant = w(0.16f)
    )
}

@Composable
fun LauncherTheme(
    mode: Int, accent: Int, dynamic: Boolean, fontStep: Int = 1, glass: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = if (glass) true else when (mode) { 1 -> false; 2, 3 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = if (glass) {
        glassScheme(accents[accent.coerceIn(accents.indices)])
    } else if (dynamic && Build.VERSION.SDK_INT >= 31) {
        var sc = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        if (mode == 3) sc = sc.copy(
            background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF0E0E0E)
        )
        sc
    } else {
        flatScheme(accents[accent.coerceIn(accents.indices)], dark, mode == 3)
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
        // Surface/Scaffold dışındaki yazı ve ikonlar da temaya göre açık/koyu olsun
        val d = LocalDensity.current
        val mult = when (fontStep) { 0 -> 0.9f; 2 -> 1.15f; else -> 1f }
        CompositionLocalProvider(
            LocalGlass provides glass,
            LocalContentColor provides scheme.onBackground,
            LocalDensity provides Density(d.density, d.fontScale * mult),
            content = content
        )
    }
}
