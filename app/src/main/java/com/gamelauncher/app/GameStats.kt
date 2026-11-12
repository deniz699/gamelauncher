package com.gamelauncher.app

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val tr = Locale("tr")

/** bounds: n+1 sınır (ms); labels/tips: n tane */
private class StatPeriod(
    val title: String,
    val bounds: List<Long>,
    val labels: List<String>,
    val tips: List<String>,
    val every: Int
)

private fun buildPeriod(mode: Int, offset: Int): StatPeriod {
    val z = ZoneId.systemDefault()
    val today = LocalDate.now(z)
    fun ms(d: LocalDate) = d.atStartOfDay(z).toInstant().toEpochMilli()
    return when (mode) {
        0 -> {
            val d = today.plusDays(offset.toLong())
            val start = ms(d)
            StatPeriod(
                title = d.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", tr)),
                bounds = (0..24).map { start + it * 3_600_000L },
                labels = (0 until 24).map { it.toString() },
                tips = (0 until 24).map { String.format(Locale.US, "%02d:00 – %02d:00", it, (it + 1) % 24) },
                every = 3
            )
        }
        1 -> {
            val mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
            val f = DateTimeFormatter.ofPattern("d MMM", tr)
            val sun = mon.plusDays(6)
            StatPeriod(
                title = "${mon.format(f)} – ${sun.format(f)} ${sun.year}",
                bounds = (0..7).map { ms(mon.plusDays(it.toLong())) },
                labels = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"),
                tips = (0 until 7).map { mon.plusDays(it.toLong()).format(DateTimeFormatter.ofPattern("d MMMM, EEEE", tr)) },
                every = 1
            )
        }
        else -> {
            val first = today.withDayOfMonth(1).plusMonths(offset.toLong())
            val len = first.lengthOfMonth()
            StatPeriod(
                title = first.format(DateTimeFormatter.ofPattern("MMMM yyyy", tr)),
                bounds = (0..len).map { ms(first.plusDays(it.toLong())) },
                labels = (1..len).map { it.toString() },
                tips = (0 until len).map { first.plusDays(it.toLong()).format(DateTimeFormatter.ofPattern("d MMMM, EEEE", tr)) },
                every = 5
            )
        }
    }
}

private fun bucketize(sessions: List<Session>, pkg: String, bounds: List<Long>): List<Long> {
    val out = LongArray(bounds.size - 1)
    for (s in sessions) {
        if (s.pkg != pkg) continue
        for (i in out.indices) {
            val a = maxOf(s.start, bounds[i])
            val b = minOf(s.end, bounds[i + 1])
            if (b > a) out[i] += b - a
        }
    }
    return out.toList()
}

@Composable
private fun BarChart(values: List<Long>, labels: List<String>, every: Int, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(60_000L)
    Column {
        Text("En yüksek: ${fmtDur(maxV)}", fontSize = 11.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).pointerInput(values.size) {
                detectTapGestures { off ->
                    val i = (off.x / size.width * values.size).toInt().coerceIn(0, values.size - 1)
                    onSelect(i)
                }
            }
        ) {
            val n = values.size
            val slot = size.width / n
            val barW = slot * 0.62f
            for (k in 0..2) {
                val y = size.height * k / 2f
                drawLine(cs.onSurface.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            values.forEachIndexed { i, v ->
                if (v <= 0L) return@forEachIndexed
                val h = (size.height * (v.toFloat() / maxV)).coerceAtLeast(3.dp.toPx())
                val x = i * slot + (slot - barW) / 2f
                val col = when {
                    i == selected -> cs.primary
                    selected >= 0 -> cs.primary.copy(alpha = 0.40f)
                    else -> cs.primary.copy(alpha = 0.85f)
                }
                drawRoundRect(
                    col, Offset(x, size.height - h), Size(barW, h),
                    CornerRadius(minOf(barW / 2f, 6.dp.toPx()))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % every == 0) l else "", Modifier.weight(1f),
                    fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1,
                    softWrap = false, overflow = TextOverflow.Visible, color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GameStatsPage(s: AppState, pkg: String, onClose: () -> Unit) {
    val g = s.games.find { it.pkg == pkg }
    if (g == null) { LaunchedEffect(Unit) { onClose() }; return }
    BackHandler(onBack = onClose)

    var mode by remember { mutableIntStateOf(0) }
    var offset by remember { mutableIntStateOf(0) }
    val period = remember(mode, offset) { buildPeriod(mode, offset) }
    val values = remember(period, s.sessions) { bucketize(s.sessions, pkg, period.bounds) }
    var sel by remember(period) { mutableIntStateOf(-1) }
    val total = values.sum()
    val cs = MaterialTheme.colorScheme

    var showWrapped by remember { mutableStateOf(false) }
    var coverOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTopBar(g.name, onClose)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp).navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(g, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)), 0.85f)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Toplam süre", color = cs.onSurfaceVariant, fontSize = 12.sp)
                        Text(fmtDur(playedMs(s.sessions, pkg, Range.ALL)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        val sz = rememberGameSize(pkg)
                        Text(
                            "Oyun boyutu: ${sz?.let { fmtSize(it.total) } ?: "…"}",
                            Modifier.padding(top = 4.dp), color = cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Wrapped", { showWrapped = true }, Modifier.weight(1f), icon = Icons.Default.AutoAwesome, accentOnPress = true)
                    SoftButton("Kapak", { coverOpen = !coverOpen }, Modifier.weight(1f), icon = Icons.Default.ImageIcon)
                }
                if (coverOpen) {
                    Spacer(Modifier.height(12.dp))
                    CoverPickerCard(s, g)
                }
                Spacer(Modifier.height(16.dp))
                SegmentedChoice(listOf("Günlük", "Haftalık", "Aylık"), mode) { mode = it; offset = 0 }

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset-- }) { Icon(Icons.Default.ChevronLeft, "Önceki") }
                    Text(
                        period.title, Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { offset++ }, enabled = offset < 0) { Icon(Icons.Default.ChevronRight, "Sonraki") }
                }

                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(24.dp))
                        .background(cs.surfaceContainer).padding(18.dp)
                ) {
                    Text(
                        if (sel >= 0) "${period.tips[sel]}  •  ${fmtDur(values[sel])}" else "Çubuğa dokunarak ayrıntıyı gör",
                        color = if (sel >= 0) cs.primary else cs.onSurfaceVariant,
                        fontWeight = FontWeight.Medium, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    BarChart(values, period.labels, period.every, sel) { sel = if (sel == it) -1 else it }
                }

                Spacer(Modifier.height(12.dp))
                val mx = values.indices.maxByOrNull { values[it] }
                FlatCard(pad = 16.dp) {
                    SummaryRow("Dönem toplamı", fmtDur(total))
                    if (mx != null && values[mx] > 0) SummaryRow("En yoğun", "${period.tips[mx]} • ${fmtDur(values[mx])}")
                }
                Spacer(Modifier.height(12.dp))
                ReportsCard(pkg)
                Text(
                    "Geçmiş veriler, uygulamanın kayıt tutmaya başladığı günden itibaren görünür.",
                    Modifier.padding(top = 12.dp), fontSize = 11.sp, color = cs.onSurfaceVariant
                )
            }
        }
    }
    if (showWrapped) WrappedPage(s, g) { showWrapped = false }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}


// ---------------------------------------------------------------- Oyun boyutu

private class GameSize(val app: Long, val data: Long, val cache: Long, val obb: Long, val exact: Boolean) {
    val total get() = app + data + cache + obb
}

private fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0)
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

/** Kullanım erişimi varsa uygulama + veri + önbellek; yoksa yalnızca APK dosyalarının boyutu. */
private fun loadGameSize(ctx: Context, pkg: String): GameSize? {
    val ai = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
    val apk = (listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())).sumOf { File(it).length() }
    // Büyük oyunlar ek verisini OBB klasöründe tutar; Android erişimi kısıtlıyorsa 0 kalır
    val obb = runCatching {
        File("/storage/emulated/0/Android/obb/$pkg").walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)
    val st = runCatching {
        ctx.getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
    }.getOrNull()
    return if (st != null) GameSize(st.appBytes, st.dataBytes - st.cacheBytes, st.cacheBytes, obb, true)
    else GameSize(apk, 0L, 0L, obb, false)
}

@Composable
private fun rememberGameSize(pkg: String): GameSize? {
    val ctx = LocalContext.current
    val size by produceState<GameSize?>(null, pkg) {
        value = withContext(Dispatchers.IO) { loadGameSize(ctx, pkg) }
    }
    return size
}

@Composable
fun GameSizeCard(pkg: String) {
    val cs = MaterialTheme.colorScheme
    val size = rememberGameSize(pkg)
    FlatCard(pad = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Oyun boyutu", Modifier.weight(1f), color = cs.onSurfaceVariant)
            Text(size?.let { fmtSize(it.total) } ?: "…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        size?.let { z ->
            Spacer(Modifier.height(4.dp))
            SummaryRow(if (z.exact) "Uygulama" else "APK dosyaları", fmtSize(z.app))
            if (z.exact) {
                SummaryRow("Oyun verisi", fmtSize(z.data))
                SummaryRow("Önbellek", fmtSize(z.cache))
            }
            if (z.obb > 0) SummaryRow("Ek veri (OBB)", fmtSize(z.obb))
            if (!z.exact) {
                Text(
                    "Veri ve önbellek boyutu için \"Kullanım erişimi\" izni gerekli.",
                    Modifier.padding(top = 6.dp), fontSize = 11.sp, color = cs.onSurfaceVariant
                )
            }
        }
    }
}
