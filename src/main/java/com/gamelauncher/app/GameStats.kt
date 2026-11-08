package com.gamelauncher.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Text(g.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
                    }
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
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceContainerHigh).padding(16.dp)
                ) {
                    Text(
                        if (sel >= 0) "${period.tips[sel]}  •  ${fmtDur(values[sel])}" else "Çubuğa dokunarak ayrıntıyı gör",
                        color = if (sel >= 0) cs.primary else cs.onSurfaceVariant,
                        fontWeight = FontWeight.Medium, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    BarChart(values, period.labels, period.every, sel) { sel = if (sel == it) -1 else it }
                }

                Spacer(Modifier.height(16.dp))
                SummaryRow("Dönem toplamı", fmtDur(total))
                val mx = values.indices.maxByOrNull { values[it] }
                if (mx != null && values[mx] > 0) SummaryRow("En yoğun", "${period.tips[mx]} • ${fmtDur(values[mx])}")
                Text(
                    "Geçmiş veriler, uygulamanın kayıt tutmaya başladığı günden itibaren görünür.",
                    Modifier.padding(top = 12.dp), fontSize = 11.sp, color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
