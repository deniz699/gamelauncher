package com.gamelauncher.app

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Yüzen hap şeklinde alt çubuk.
 * glass = true  -> liquid glass görünümü (yarı saydam cam, parlak kenar, basınca büyüyen balon)
 * glass = false -> aynı şekil, düz renkli (efektsiz)
 */
@Composable
fun GlassBottomBar(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    glass: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val n = items.size
    var pressed by remember { mutableStateOf(false) }
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.7f, stiffness = 280f), label = "pos")
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "press")

    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val surface = cs.surface
    val container = cs.surfaceContainerHigh
    val secContainer = cs.secondaryContainer
    val activeColor = if (glass) cs.primary else cs.onSecondaryContainer
    val idleColor = cs.onSurfaceVariant

    Box(
        modifier
            .shadow(if (glass) 14.dp else 8.dp, RoundedCornerShape(50), clip = false)
            .drawBehind {
                val w = size.width
                val h = size.height
                val corner = CornerRadius(h / 2f)

                // --- Çubuğun gövdesi ---
                if (glass) {
                    drawRoundRect(surface.copy(alpha = if (dark) 0.50f else 0.45f), cornerRadius = corner)
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.20f else 0.55f),
                                Color.White.copy(alpha = if (dark) 0.03f else 0.10f)
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
                } else {
                    drawRoundRect(container, cornerRadius = corner)
                }

                // --- Seçili öğenin balonu ---
                val itemW = w / n
                val inset = 6.dp.toPx()
                val grow = if (glass) press else press * 0.3f
                val iw = itemW - inset * 2 + 14.dp.toPx() * grow
                val ih = h - 12.dp.toPx() + 16.dp.toPx() * grow
                val left = itemW * (pos + 0.5f) - iw / 2f
                val top = (h - ih) / 2f
                val tl = Offset(left, top)
                val sz = Size(iw, ih)
                val r = CornerRadius(ih / 2f)

                if (glass) {
                    drawRoundRect(
                        (if (dark) Color.White else Color.Black).copy(alpha = (if (dark) 0.14f else 0.07f) + 0.08f * press),
                        tl, sz, r
                    )
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.30f + 0.25f * press), Color.White.copy(alpha = 0.04f)),
                            startY = top,
                            endY = top + ih
                        ),
                        tl, sz, r
                    )
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.7f)),
                            start = tl,
                            end = Offset(left + iw, top + ih)
                        ),
                        tl, sz, r,
                        style = Stroke((1f + press).dp.toPx())
                    )
                } else {
                    drawRoundRect(secContainer, tl, sz, r)
                }
            }
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
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, (label, icon) ->
                val t = (1f - abs(pos - i)).coerceIn(0f, 1f)
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
