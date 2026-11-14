package com.gamelauncher.app

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
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
            GlassBackdrop()
            Surface(Modifier.fillMaxSize(), color = Color.Transparent, content = content)
        }
    } else {
        Surface(modifier, color = color, content = content)
    }
}

/**
 * Ana ekrandaki kaydırılan içeriğin (arka plan + liste) kaydedildiği katman.
 * Alt çubuk gibi cam öğeler bunun bulanık bir kopyasını kendi arkasında gösterir (gerçek buzlu cam).
 */
val LocalGlassSource = compositionLocalOf<GraphicsLayer?> { null }

/**
 * Gerçek arka plan bulanıklığı: öğenin altında kalan içerik (kaydırılan kartlar vb.) bulanıklaştırılıp
 * öğenin şekline kırpılarak çizilir. Android 12 altında ya da kaynak yoksa hiçbir şey yapmaz
 * (yarı saydam dolgu yine de görünür). Bulanıklık sadece öğenin kendi alanında hesaplanır.
 */
fun Modifier.glassBackdropBlur(shape: Shape, radius: Float = 36f): Modifier = composed {
    val src = LocalGlassSource.current
    if (!LocalGlass.current || src == null || Build.VERSION.SDK_INT < 31) {
        Modifier
    } else {
        val layer = androidx.compose.ui.graphics.layer.rememberGraphicsLayer()
        var pos by remember { mutableStateOf(Offset.Zero) }
        Modifier
            .onGloballyPositioned { pos = it.positionInRoot() }
            .drawBehind {
                val pad = 64f
                layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                val sz = IntSize((size.width + 2 * pad).toInt(), (size.height + 2 * pad).toInt())
                layer.record(this, layoutDirection, sz) {
                    translate(pad - pos.x, pad - pos.y) { drawLayer(src) }
                }
                val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
                clipPath(path) {
                    translate(-pad, -pad) { drawLayer(layer) }
                }
            }
    }
}

/**
 * Cam modunun arka planı: koyu lacivert zemin üstünde mercan, mor, mavi ve camgöbeği ışık lekeleri.
 * Küçük bir bitmap'e bir kez çizilip büyütülür; kaydırırken GPU'ya yük bindirmez.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.fillMaxSize().drawWithCache {
            val w = (size.width / 4f).toInt().coerceAtLeast(2)
            val h = (size.height / 4f).toInt().coerceAtLeast(2)
            val bmp = ImageBitmap(w, h)
            CanvasDrawScope().draw(this, layoutDirection, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
                paintGlassBlobs(accent)
            }
            val dst = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
            onDrawBehind { drawImage(bmp, dstSize = dst, filterQuality = FilterQuality.Medium) }
        }
    )
}

private fun DrawScope.paintGlassBlobs(accent: Color) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0D0B26), Color(0xFF0A0818), Color(0xFF130A14))))
    fun blob(c: Color, cx: Float, cy: Float, r: Float) {
        drawRect(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(w * cx, h * cy), radius = r))
    }
    val r = maxOf(w, h)
    blob(Color(0xFF6A3DD8).copy(alpha = 0.55f), 0.50f, 0.30f, r * 0.55f)   // mor (orta-üst)
    blob(Color(0xFF2B4BFF).copy(alpha = 0.40f), 0.05f, 0.12f, r * 0.45f)   // mavi (sol-üst)
    blob(Color(0xFF7A1D2E).copy(alpha = 0.55f), 1.00f, 0.02f, r * 0.40f)   // bordo (sağ-üst)
    blob(Color(0xFFC03A9A).copy(alpha = 0.30f), 0.85f, 0.58f, r * 0.40f)   // pembe (sağ-orta)
    blob(accent.copy(alpha = 0.60f), 0.12f, 0.97f, r * 0.45f)              // vurgu rengi (sol-alt)
    blob(Color(0xFF14A8FF).copy(alpha = 0.55f), 0.92f, 0.95f, r * 0.42f)   // camgöbeği (sağ-alt)
}
