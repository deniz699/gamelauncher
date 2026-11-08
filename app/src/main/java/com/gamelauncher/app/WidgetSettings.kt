package com.gamelauncher.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val swatches = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF1B1A22, 0xFF0F172A, 0xFF1E293B, 0xFF6750A4, 0xFFBFA8FF,
    0xFF1E88E5, 0xFF00ACC1, 0xFF22C55E, 0xFFFBBF24, 0xFFFB8C00, 0xFFEF4444, 0xFFEC4899
).map { it.toInt() }

@Composable
internal fun ColorPickRow(title: String, value: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var hex by remember(value) { mutableStateOf(String.format("#%06X", value and 0xFFFFFF)) }
    Text(title, Modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.bodyMedium)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatches.forEach { c ->
            val sel = (c and 0xFFFFFF) == (value and 0xFFFFFF)
            Spacer(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                    .border(
                        BorderStroke(if (sel) 3.dp else 1.dp, if (sel) cs.onSurface else cs.outline.copy(alpha = 0.5f)),
                        CircleShape
                    )
                    .clickable { onChange(c or (0xFF shl 24)) }
            )
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { s ->
                hex = s
                val m = Regex("^#?([0-9A-Fa-f]{6})$").find(s.trim())
                if (m != null) onChange((m.groupValues[1].toLong(16).toInt()) or (0xFF shl 24))
            },
            singleLine = true,
            label = { Text("Özel #RRGGBB") },
            modifier = Modifier.width(150.dp)
        )
    }
}

/** Renk / saydamlık düzenleyici: hem varsayılan ayarlarda hem tek widget ayarında kullanılır. */
@Composable
internal fun StyleEditor(st: WStyle, showTempColors: Boolean, onChange: (WStyle) -> Unit) {
    ColorPickRow("Arka plan rengi", st.bg) { onChange(st.copy(bg = it)) }
    ColorPickRow("Yazı rengi", st.text) { onChange(st.copy(text = it)) }
    ColorPickRow("Vurgu rengi (yaylar, çubuklar, süreler)", st.accent) { onChange(st.copy(accent = it)) }

    Text(
        "Arka plan saydamlığı: %${(st.alpha * 100f / 255f).roundToInt()}",
        Modifier.padding(top = 12.dp)
    )
    Slider(
        value = st.alpha / 255f,
        onValueChange = { onChange(st.copy(alpha = (it * 255f).roundToInt().coerceIn(40, 255))) },
        valueRange = 0.15f..1f
    )
    if (showTempColors) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sıcaklığa göre renk")
                Text(
                    "Sıcaklık widget'ında yaylar sıcaklığa göre yeşil / sarı / kırmızı olur; kapalıyken vurgu rengi kullanılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            FlatSwitch(checked = st.tempColors, onCheckedChange = { onChange(st.copy(tempColors = it)) })
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** Ayarlar sayfasındaki "Varsayılan widget renkleri" bölümü. Değişiklik anında widget'lara uygulanır. */
@Composable
fun WidgetColorSettings() {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf(WidgetStyle.loadDefault(ctx)) }
    fun change(n: WStyle) {
        st = n
        WidgetStyle.saveDefault(ctx, n)
        WidgetUi.updateAll(ctx)
    }

    Text("Varsayılan widget renkleri", Modifier.padding(top = 20.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmall)
    Text(
        "Ayrı renk verilmemiş tüm widget'lar bunu kullanır. Tek bir widget'ın rengini değiştirmek için ana ekranda " +
            "o widget'a basılı tut ve çıkan \"Ayarlar\" düğmesine dokun.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    StyleEditor(st, showTempColors = true) { change(it) }
    SoftButton(
        "Hepsini eski haline sıfırla",
        {
            WidgetStyle.resetAll(ctx)
            st = WidgetStyle.FACTORY
            WidgetUi.updateAll(ctx)
        },
        Modifier.padding(top = 4.dp)
    )
    Text(
        "Varsayılan renkleri ve widget'lara tek tek verdiğin renklerin hepsini fabrika ayarına döndürür.",
        Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
