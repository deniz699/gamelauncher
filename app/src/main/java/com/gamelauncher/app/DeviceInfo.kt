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
import androidx.compose.foundation.layout.Arrangement
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

private fun gb(b: Long): String = String.format(Locale.US, "%.1f GB", b / 1073741824.0)

/** İşlemci sıcaklığı: Android çoğu telefonda bu dosyaları uygulamalara kapatır; okunamazsa null. */
private fun cpuTempC(): Float? {
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

private fun rows(ctx: Context): List<Pair<String, List<Pair<String, String>>>> {
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
            "Model" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        ),
        "İşlemci" to listOf(
            "İşlemci (çip)" to soc.ifBlank { Build.HARDWARE },
            "CPU" to "${Runtime.getRuntime().availableProcessors()} çekirdek • ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}" +
                (mhz?.let { " • $it MHz" } ?: ""),
            "İşlemci sıcaklığı" to (ct?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Okunamadı (Android izin vermiyor)")
        ),
        "Bellek" to listOf(
            "RAM" to "${gb(mi.totalMem)} (boş: ${gb(mi.availMem)})",
            "Depolama" to "${gb(total)} (boş: ${gb(free)})"
        ),
        "Sistem" to listOf(
            "Android sürümü" to Build.VERSION.RELEASE,
            "SDK sürümü" to Build.VERSION.SDK_INT.toString()
        ),
        "Batarya" to listOf(
            "Sıcaklık" to (if (bt != Int.MIN_VALUE) String.format(Locale.US, "%.1f °C", bt / 10f) else "Okunamadı"),
            "Şarj" to (if (lv >= 0) "%${lv * 100 / sc}" else "?")
        )
    )
}

@Composable
fun DevicePage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }
    val data = remember(tick) { rows(ctx) }

    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Text("Cihaz bilgileri", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp).navigationBarsPadding()
            ) {
                data.forEach { (title, items) ->
                    Text(
                        title, Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
                    )
                    items.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
