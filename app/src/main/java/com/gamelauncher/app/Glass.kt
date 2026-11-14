package com.gamelauncher.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// "Liquid Glass" (buzlu cam) modu.
// Cam etkisi: renkli, yumuşak ışıklı bir arka plan + üstünde yarı saydam beyaz katmanlar,
// üst-sol köşeden parlayan ince bir kenar çizgisi ve hafif bir ışık yansıması.
// Mod kapalıyken tüm yardımcılar eski düz (flat) görünümü aynen üretir.

val LocalGlass = compositionLocalOf { false }

/** Cam modunda diyalog / menü için neredeyse opak koyu yüzey (okunabilirlik); değilse eski renk. */
@Composable
fun popupColor(): Color =
    if (LocalGlass.current) Color(0xF2161334) else MaterialTheme.colorScheme.surfaceContainerHigh

/** Cam modunda alt sayfa (bottom sheet) zemini. */
@Composable
fun sheetColor(): Color =
    if (LocalGlass.current) Color(0xF20E0C26) else MaterialTheme.colorScheme.background

/** DropdownMenu gibi Material bileşenlerinin cam modunda şeffaf kalmaması için. */
@Composable
fun PopupTheme(content: @Composable () -> Unit) {
    if (LocalGlass.current) {
        val cs = MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme = cs.copy(surfaceContainer = popupColor()),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    } else {
        content()
    }
}

private val rimBrush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.26f))
)
private val sheenBrush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f), Color.Transparent)
)

/**
 * Düz modda: klip + düz renk (eskisiyle aynı).
 * Cam modunda: aynı renk (şeffaf olduğu için arka plan görünür) + ışık yansıması + parlak kenar.
 */
fun Modifier.glassSurface(shape: Shape, color: Color): Modifier = composed {
    if (!LocalGlass.current) {
        Modifier.clip(shape).background(color)
    } else {
        Modifier.clip(shape).background(color)
            .background(sheenBrush, shape)
            .border(1.dp, rimBrush, shape)
    }
}

/** Renkli cam: [tint] tonunda, yarı saydam gradyan dolgu. Düz modda [flat] rengi (varsayılan [tint]). */
fun Modifier.glassTinted(shape: Shape, tint: Color, flat: Color = tint): Modifier = composed {
    if (!LocalGlass.current) {
        Modifier.clip(shape).background(flat)
    } else {
        Modifier.clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.82f), tint.copy(alpha = 0.40f))), shape)
            .background(sheenBrush, shape)
            .border(1.dp, rimBrush, shape)
    }
}

/** Alt sayfalar (Ayarlar, İstatistik…): cam modunda arka plan renkli ışıklı, değilse düz Surface. */
@Composable
fun PageSurface(modifier: Modifier = Modifier, color: Color, content: @Composable () -> Unit) {
    if (LocalGlass.current) {
        Box(modifier) {
            GlassBackdrop(animate = false)
            Surface(Modifier.fillMaxSize(), color = Color.Transparent, content = content)
        }
    } else {
        Surface(modifier, color = color, content = content)
    }
}

/**
 * Cam modunun arka planı: koyu lacivert zemin üstünde mercan, mor, mavi ve camgöbeği ışık lekeleri.
 * [animate] açıkken lekeler çok yavaş kayar (sıvı hissi).
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, animate: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    val p: Float = if (animate) {
        val t = rememberInfiniteTransition(label = "glassBg")
        val v by t.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
            label = "glassDrift"
        )
        v
    } else 0.5f
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF0D0B26), Color(0xFF0A0818), Color(0xFF130A14)))
        )
        fun blob(c: Color, cx: Float, cy: Float, r: Float) {
            val center = Offset(w * cx, h * cy)
            drawRect(Brush.radialGradient(listOf(c, Color.Transparent), center = center, radius = r))
        }
        val d = (p - 0.5f) * 0.10f
        val r = maxOf(w, h)
        blob(Color(0xFF6A3DD8).copy(alpha = 0.55f), 0.50f + d, 0.30f - d, r * 0.55f)   // mor (orta-üst)
        blob(Color(0xFF2B4BFF).copy(alpha = 0.40f), 0.05f - d, 0.12f + d, r * 0.45f)   // mavi (sol-üst)
        blob(Color(0xFF7A1D2E).copy(alpha = 0.55f), 1.00f + d, 0.02f, r * 0.40f)       // bordo (sağ-üst)
        blob(Color(0xFFC03A9A).copy(alpha = 0.30f), 0.85f - d, 0.58f + d, r * 0.40f)   // pembe (sağ-orta)
        blob(accent.copy(alpha = 0.60f), 0.12f + d, 0.97f - d, r * 0.45f)              // vurgu rengi (sol-alt)
        blob(Color(0xFF14A8FF).copy(alpha = 0.55f), 0.92f - d, 0.95f + d, r * 0.42f)   // camgöbeği (sağ-alt)
    }
}
