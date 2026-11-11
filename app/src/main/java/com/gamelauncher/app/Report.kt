package com.gamelauncher.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Bir oyun oturumunun özeti. -1 olan alanlar "ölçülemedi" demektir
 * (FPS için root gerekebilir, CPU ısısını Android çoğu telefonda uygulamalara kapatır).
 */
data class GameReport(
    val pkg: String,
    val start: Long,
    val end: Long,
    val avgFps: Float,
    val minFps: Float,
    val batStart: Int,
    val batEnd: Int,
    val charging: Boolean,
    val maxBatTemp: Float,
    val maxCpuTemp: Float
)

class ReportStore(ctx: Context) {
    private val file = File(ctx.filesDir, "reports.json")

    fun all(): List<GameReport> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            GameReport(
                pkg = o.getString("p"), start = o.getLong("s"), end = o.getLong("e"),
                avgFps = o.optDouble("af", -1.0).toFloat(), minFps = o.optDouble("mf", -1.0).toFloat(),
                batStart = o.optInt("bs", -1), batEnd = o.optInt("be", -1), charging = o.optBoolean("c"),
                maxBatTemp = o.optDouble("bt", -1.0).toFloat(), maxCpuTemp = o.optDouble("ct", -1.0).toFloat()
            )
        }
    }.getOrDefault(emptyList())

    /** En yeni üstte. */
    fun forGame(pkg: String): List<GameReport> = all().filter { it.pkg == pkg }.sortedByDescending { it.end }

    fun add(r: GameReport) {
        val list = (all() + r).takeLast(300)
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("p", it.pkg).put("s", it.start).put("e", it.end)
                    .put("af", it.avgFps.toDouble()).put("mf", it.minFps.toDouble())
                    .put("bs", it.batStart).put("be", it.batEnd).put("c", it.charging)
                    .put("bt", it.maxBatTemp.toDouble()).put("ct", it.maxCpuTemp.toDouble())
            )
        }
        runCatching { file.writeText(a.toString()) }
    }
}

fun GameReport.fpsText(): String =
    if (avgFps < 0f) "FPS ölçülemedi (root gerekir)"
    else "FPS: ort. ${avgFps.roundToInt()} • en düşük ${minFps.roundToInt()}"

fun GameReport.batText(): String = when {
    batStart < 0 || batEnd < 0 -> "Pil: bilinmiyor"
    charging -> "Pil: şarjdaydı (%$batEnd)"
    else -> "Pil: %$batStart → %$batEnd (−${(batStart - batEnd).coerceAtLeast(0)})"
}

fun GameReport.tempText(): String {
    val parts = listOfNotNull(
        if (maxBatTemp > 0f) String.format(Locale.US, "pil %.1f°C", maxBatTemp) else null,
        if (maxCpuTemp > 0f) String.format(Locale.US, "CPU %.0f°C", maxCpuTemp) else null
    )
    return "En yüksek ısı: " + if (parts.isEmpty()) "okunamadı" else parts.joinToString(" • ")
}

/** Aynı oyunun önceki oturumuyla kısa karşılaştırma; karşılaştıracak veri yoksa null. */
fun compareText(r: GameReport, prev: GameReport?): String? {
    if (prev == null) return null
    val parts = ArrayList<String>()
    if (r.maxBatTemp > 0f && prev.maxBatTemp > 0f) {
        val d = r.maxBatTemp - prev.maxBatTemp
        parts += String.format(Locale.US, "ısı %+.1f°C", d)
    }
    if (r.avgFps >= 0f && prev.avgFps >= 0f) {
        val d = (r.avgFps - prev.avgFps).roundToInt()
        parts += "FPS " + (if (d > 0) "+$d" else "$d")
    }
    return if (parts.isEmpty()) null else "Önceki oturuma göre: " + parts.joinToString(" • ")
}

/** Oyunun istatistik sayfasındaki "Oyun raporları" kartı. */
@Composable
fun ReportsCard(pkg: String) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val list = remember(pkg) { ReportStore(ctx).forGame(pkg) }
    FlatCard(pad = 16.dp) {
        Text("Oyun raporları", style = MaterialTheme.typography.titleMedium)
        if (list.isEmpty()) {
            Text(
                "Henüz rapor yok. Ayarlar > Kenar paneli > Oyun raporu açıkken oynadığın oturumlar burada listelenir.",
                Modifier.padding(top = 6.dp), fontSize = 12.sp, color = cs.onSurfaceVariant
            )
            return@FlatCard
        }
        val temps = list.take(10).map { it.maxBatTemp }.filter { it > 0f }
        if (temps.isNotEmpty()) {
            Text(
                String.format(Locale.US, "Son %d oturumda ortalama en yüksek pil ısısı: %.1f°C", list.take(10).size, temps.average()),
                Modifier.padding(top = 4.dp), fontSize = 12.sp, color = cs.onSurfaceVariant
            )
        }
        list.take(8).forEachIndexed { i, r ->
            Column(Modifier.padding(top = 12.dp)) {
                Text("${fmtDate(r.start)} • ${fmtDur(r.end - r.start)}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(r.fpsText(), fontSize = 12.sp, color = cs.onSurfaceVariant)
                Text(r.batText(), fontSize = 12.sp, color = cs.onSurfaceVariant)
                Text(r.tempText(), fontSize = 12.sp, color = cs.onSurfaceVariant)
                compareText(r, list.getOrNull(i + 1))?.let { Text(it, fontSize = 12.sp, color = cs.primary) }
            }
        }
    }
}
