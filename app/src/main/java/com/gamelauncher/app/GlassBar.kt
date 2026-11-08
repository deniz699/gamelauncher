package com.gamelauncher.app

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPosition
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ------------------------------------------------------------------
//  Arka plan yakalama: çubuğun altında kalan içeriği bir katmana kaydeder,
//  çubuk bu katmanı bulanıklaştırıp / büyüterek kendi içine çizer.
// ------------------------------------------------------------------

@Stable
class GlassBackdrop(val layer: GraphicsLayer) {
    /** Kaynak içeriğin ekrandaki sol-üst köşesi */
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Çubuğun "arkasında" görünecek içeriğe uygulanır. */
fun Modifier.glassSource(b: GlassBackdrop, bg: Color): Modifier =
    this
        .onGloballyPosition { b.origin = it.positionInRoot() }
        .drawWithContent {
            b.layer.record {
                drawRect(bg)
                this@drawWithContent.drawContent()
            }
            drawLayer(b.layer)
        }

// ------------------------------------------------------------------
//  Android 13+ için mercek (kırılma) shader'ı
// ------------------------------------------------------------------

private const val LENS_AGSL = """
uniform shader img;
uniform float2 center;
uniform float2 halfSize;
uniform float strength;

half4 main(float2 coord) {
    float2 p = (coord - center) / halfSize;
    float r = length(p);
    if (r >= 1.0) {
        return img.eval(coord);
    }
    float edge = smoothstep(0.3, 1.0, r);
    float mag = 1.0 - (0.12 + 0.28 * strength);
    float s = mix(mag, 1.0, edge * edge);
    float ca = 0.03 * edge * (0.4 + strength);
    half4 c = img.eval(center + p * halfSize * s);
    half rr = img.eval(center + p * halfSize * min(s + ca, 1.0)).r;
    half bb = img.eval(center + p * halfSize * (s - ca)).b;
    return half4(rr, c.g, bb, c.a);
}
"""

@RequiresApi(33)
private class Lens33 {
    private val shader = RuntimeShader(LENS_AGSL)

    fun effect(r: Rect, strength: Float): RenderEffect {
        shader.setFloatUniform("center", r.center.x, r.center.y)
        shader.setFloatUniform("halfSize", r.width / 2f, r.height / 2f)
        shader.setFloatUniform("strength", strength)
        return android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "img")
            .asComposeRenderEffect()
    }
}

// ------------------------------------------------------------------
//  Damla (balon) geometrisi: basınca şişer, hareket ederken esner
// ------------------------------------------------------------------

private fun bubbleRect(w: Float, h: Float, n: Int, pos: Float, vel: Float, press: Float, d: Float): Rect {
    val itemW = w / n
    val inset = 6f * d
    val stretch = (abs(vel) * 0.05f).coerceIn(0f, 0.35f)
    val iw = (itemW - inset * 2 + 18f * d * press) * (1f + stretch)
    val ih = (h - 12f * d + 10f * d * press) * (1f - stretch * 0.4f)
    val cx = itemW * (pos + 0.5f)
    val cy = h / 2f
    return Rect(cx - iw / 2f, cy - ih / 2f, cx + iw / 2f, cy + ih / 2f)
}

private val pill = RoundedCornerShape(50)

/**
 * Yüzen hap şeklinde alt çubuk.
 * glass = true  -> liquid glass: arkası bulanık şeffaf cam, seçili balon mercek gibi büyütür,
 *                  basınca su damlası gibi şişer, kayarken esner.
 * glass = false -> aynı şekil, düz renkli (efektsiz)
 */
@Composable
fun GlassBottomBar(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    glass: Boolean,
    onSelect: (Int) -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier
) {
    val n = items.size
    var pressed by remember { mutableStateOf(false) }
    val pos = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        pos.animateTo(selected.toFloat(), spring(dampingRatio = 0.6f, stiffness = 260f))
    }
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 330f), label = "press")
    var barRoot by remember { mutableStateOf(Offset.Zero) }

    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val surface = cs.surface
    val container = cs.surfaceContainerHigh
    val secContainer = cs.secondaryContainer
    val activeColor = if (glass) cs.primary else cs.onSecondaryContainer
    val idleColor = cs.onSurfaceVariant
    val blurOk = Build.VERSION.SDK_INT >= 31
    val lens = remember { if (Build.VERSION.SDK_INT >= 33) Lens33() else null }

    Box(
        modifier
            .shadow(if (glass) 14.dp else 8.dp, pill, clip = false)
            .onGloballyPosition { barRoot = it.positionInRoot() }
            .pointerInput(n) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    fun idx(x: Float) = ((x / size.width) * n).toInt().coerceIn(0, n - 1)
                    onSelect(idx(down.position.x))
                    drag(down.id) { ch ->
                        onSelect(idx(ch.position.x))
                        ch.consume()
                    }
                    pressed = false
                }
            }
    ) {
        if (glass) {
            // 1) Arkadaki içerik, bulanık
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        shape = pill
                        clip = true
                        if (blurOk) {
                            val r = 22.dp.toPx()
                            renderEffect = BlurEffect(r, r, TileMode.Mirror)
                        }
                    }
                    .drawBehind {
                        val o = barRoot - backdrop.origin
                        translate(-o.x, -o.y) { drawLayer(backdrop.layer) }
                    }
            )

            // 2) Cam rengi, parlama ve kenar çizgisi
            Box(
                Modifier.matchParentSize().drawBehind {
                    val w = size.width
                    val h = size.height
                    val corner = CornerRadius(h / 2f)
                    val tint = if (blurOk) (if (dark) 0.30f else 0.28f) else 0.62f
                    drawRoundRect(surface.copy(alpha = tint), cornerRadius = corner)
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.16f else 0.50f),
                                Color.White.copy(alpha = if (dark) 0.02f else 0.08f)
                            )
                        ),
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.5f)),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        cornerRadius = corner,
                        style = Stroke(1.5.dp.toPx())
                    )
                }
            )

            // 3) Seçili damla: arkadaki içeriği mercek gibi büyütür (keskin, bulanık değil)
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val r = bubbleRect(size.width, size.height, n, pos.value, pos.velocity, press, density)
                        val rr = RoundRect(r, CornerRadius(r.height / 2f))
                        shape = object : Shape {
                            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                                Outline.Rounded(rr)
                        }
                        clip = true
                        renderEffect = lens?.effect(r, 0.4f + 0.6f * press)
                    }
                    .drawBehind {
                        val o = barRoot - backdrop.origin
                        translate(-o.x, -o.y) {
                            if (lens == null) {
                                // Android 13 altı: basit büyütme
                                val r = bubbleRect(size.width, size.height, n, pos.value, pos.velocity, press, density)
                                scale(1.12f + 0.28f * press, pivot = r.center + o) { drawLayer(backdrop.layer) }
                            } else {
                                drawLayer(backdrop.layer)
                            }
                        }
                    }
            )

            // 4) Damlanın parlaması: dolgu, yansıma ve kenar ışığı
            Box(
                Modifier.matchParentSize().drawBehind {
                    val r = bubbleRect(size.width, size.height, n, pos.value, pos.velocity, press, density)
                    val tl = r.topLeft
                    val sz = r.size
                    val cr = CornerRadius(r.height / 2f)
                    drawRoundRect(
                        (if (dark) Color.White else Color.Black).copy(alpha = (if (dark) 0.10f else 0.05f) + 0.05f * press),
                        tl, sz, cr
                    )
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.34f + 0.20f * press),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.16f)
                            ),
                            startY = r.top,
                            endY = r.bottom
                        ),
                        tl, sz, cr
                    )
                    // küçük yansıma lekesi (su damlası parıltısı)
                    drawOval(
                        Color.White.copy(alpha = 0.40f + 0.2f * press),
                        topLeft = Offset(r.left + sz.width * 0.14f, r.top + sz.height * 0.10f),
                        size = Size(sz.width * 0.34f, sz.height * 0.16f)
                    )
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.7f)),
                            start = tl,
                            end = Offset(r.right, r.bottom)
                        ),
                        tl, sz, cr,
                        style = Stroke((1f + press).dp.toPx())
                    )
                }
            )
        } else {
            // Düz mod
            Box(
                Modifier.matchParentSize().drawBehind {
                    val h = size.height
                    drawRoundRect(container, cornerRadius = CornerRadius(h / 2f))
                    val r = bubbleRect(size.width, h, n, pos.value, 0f, press * 0.3f, density)
                    drawRoundRect(secContainer, r.topLeft, r.size, CornerRadius(r.height / 2f))
                }
            )
        }

        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, (label, icon) ->
                val t = (1f - abs(pos.value - i)).coerceIn(0f, 1f)
                val c = lerp(idleColor, activeColor, t)
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, label, tint = c)
                    Text(label, color = c, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
