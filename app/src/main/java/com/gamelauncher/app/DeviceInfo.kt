package com.gamelauncher.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private fun readFile(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()

internal fun gb(b: Long): String = String.format(Locale.US, "%.1f GB", b / 1073741824.0)

/** İşlemci sıcaklığı: Android çoğu telefonda bu dosyaları uygulamalara kapatır; okunamazsa null. */
internal fun cpuTempC(): Float? {
    val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
    var best: Float? = null
    for (z in zones) {
        val type = readFile("${z.path}/type")?.lowercase() ?: continue
        val raw = readFile("${z.path}/temp")?.toFloatOrNull() ?: continue
        val c = if (raw > 1000f) raw / 1000f else raw
        if (c <= 0f || c > 150f) continue
        val isCpu = listOf("cpu", "soc", "tsens", "big", "little", "mtktscpu").any { type.contains(it) }
        if (isCpu && (best == null || c > best)) best = c
    }
    return best
}

private fun maxFreqMhz(): Int? {
    val cores = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) } ?: return null
    val m = cores.mapNotNull { readFile("${it.path}/cpufreq/cpuinfo_max_freq")?.toLongOrNull() }.maxOrNull() ?: return null
    return (m / 1000).toInt()
}

/** Bir bilgi satırı. [frac] doluysa çubuk olarak çizilir (0..1); [temp] true ise renk sıcaklığa göre değişir. */
private class Item(val k: String, val v: String, val frac: Float? = null, val temp: Boolean = false)

private fun tempFrac(c: Float) = (c / 100f).coerceIn(0f, 1f)

private fun rows(ctx: Context): List<Pair<String, List<Item>>> {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val st = StatFs(Environment.getDataDirectory().path)
    val total = st.blockCountLong * st.blockSizeLong
    val free = st.availableBlocksLong * st.blockSizeLong

    val bat: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val bt = bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    val lv = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val sc = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100

    val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim() else Build.HARDWARE
    val mhz = maxFreqMhz()
    val ct = cpuTempC()

    return listOf(
        "Cihaz" to listOf(
            Item("Model", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
        ),
        "İşlemci" to listOf(
            Item("İşlemci (çip)", soc.ifBlank { Build.HARDWARE }),
            Item(
                "CPU",
                "${Runtime.getRuntime().availableProcessors()} çekirdek • ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}" +
                    (mhz?.let { " • $it MHz" } ?: "")
            ),
            if (ct != null) Item("İşlemci sıcaklığı", String.format(Locale.US, "%.1f °C", ct), tempFrac(ct), true)
            else Item("İşlemci sıcaklığı", "Okunamadı (Android izin vermiyor)")
        ),
        "Bellek" to listOf(
            Item(
                "RAM", "${gb(mi.totalMem - mi.availMem)} / ${gb(mi.totalMem)}",
                ((mi.totalMem - mi.availMem).toFloat() / mi.totalMem).coerceIn(0f, 1f)
            ),
            Item(
                "Depolama", "${gb(total - free)} / ${gb(total)}",
                ((total - free).toFloat() / total).coerceIn(0f, 1f)
            )
        ),
        "Sistem" to listOf(
            Item("Android sürümü", Build.VERSION.RELEASE),
            Item("SDK sürümü", Build.VERSION.SDK_INT.toString())
        ),
        "Batarya" to listOf(
            if (bt != Int.MIN_VALUE) Item("Sıcaklık", String.format(Locale.US, "%.1f °C", bt / 10f), tempFrac(bt / 10f), true)
            else Item("Sıcaklık", "Okunamadı"),
            if (lv >= 0) Item("Şarj", "%${lv * 100 / sc}", (lv.toFloat() / sc).coerceIn(0f, 1f))
            else Item("Şarj", "?")
        )
    )
}

/** Çubuk: yazılar çubuğun üstünde durur (solda ad, sağda değer). */
@Composable
private fun StatBar(label: String, value: String, frac: Float, temp: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val c = frac * 100f
    val fill = if (temp) {
        when {
            c < 40f -> Color(0xFF4CAF50)
            c < 50f -> Color(0xFFFFB300)
            else -> Color(0xFFE53935)
        }
    } else cs.primary
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxWidth().height(40.dp).clip(shape).background(cs.surfaceContainerHighest)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceAtLeast(0.02f)).background(fill.copy(alpha = 0.55f)))
        Row(
            Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = cs.onSurface, fontWeight = FontWeight.Medium)
            Text(value, color = cs.onSurface, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
        }
    }
}

@Composable
fun DevicePage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }
    val data = remember(tick) { rows(ctx) }

    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTopBar("Cihaz bilgileri", onClose)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                data.forEach { (title, items) ->
                    FlatCard {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        items.forEach { it ->
                            if (it.frac != null) {
                                StatBar(it.k, it.v, it.frac, it.temp, Modifier.padding(top = 12.dp))
                            } else {
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(it.k, Modifier.padding(end = 12.dp), color = cs.onSurfaceVariant)
                                    Text(it.v, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
