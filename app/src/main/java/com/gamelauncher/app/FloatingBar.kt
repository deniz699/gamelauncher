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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Yüzen hap şeklinde alt çubuk (düz, temaya göre renkli). Dokun veya sürükle. */
@Composable
fun FloatingBottomBar(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val n = items.size
    var pressed by remember { mutableStateOf(false) }
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.7f, stiffness = 280f), label = "pos")
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "press")

    val cs = MaterialTheme.colorScheme
    val container = cs.surfaceContainerHigh
    val secContainer = cs.secondaryContainer
    val activeColor = cs.onSecondaryContainer
    val idleColor = cs.onSurfaceVariant

    Box(
        modifier
            .shadow(8.dp, RoundedCornerShape(50), clip = false)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRoundRect(container.copy(alpha = 0.72f), cornerRadius = CornerRadius(h / 2f))

                val itemW = w / n
                val inset = 6.dp.toPx()
                val grow = press * 0.3f
                val iw = itemW - inset * 2 + 14.dp.toPx() * grow
                val ih = h - 12.dp.toPx() + 16.dp.toPx() * grow
                val left = itemW * (pos + 0.5f) - iw / 2f
                val top = (h - ih) / 2f
                drawRoundRect(secContainer, Offset(left, top), Size(iw, ih), CornerRadius(ih / 2f))
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
