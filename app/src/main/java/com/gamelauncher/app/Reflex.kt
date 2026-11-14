package com.gamelauncher.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// Refleks Testi: 30 saniyelik tur. Yeşil hedefe hızlı dokun, kırmızı ✕ tuzağa dokunma.
// Tepki süresi hedefin belirdiği andan dokunuşa kadar ölçülür (ms). Ekran dokunma gecikmesini kabaca ölçmek için de kullanılır.

private enum class RPhase { IDLE, PLAY, DONE }

private class Orb(val id: Int, val fx: Float, val fy: Float, val bad: Boolean, val born: Long, val life: Long, val sizeDp: Float)

private class Pop(val fx: Float, val fy: Float, val text: String, val good: Boolean, val born: Long, val ms: Int)

private const val ROUND_MS = 30_000L
private val GoodCol = Color(0xFF3DDC84)
private val BadCol = Color(0xFFFF4D6A)

private fun rating(avg: Int): String = when {
    avg <= 0 -> "-"
    avg < 250 -> "Şimşek"
    avg < 320 -> "Çok hızlı"
    avg < 400 -> "Hızlı"
    avg < 500 -> "Normal"
    else -> "Yavaş"
}

@Composable
fun ReflexGame(feedback: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val fb by rememberUpdatedState(feedback)

    var phase by remember { mutableStateOf(RPhase.IDLE) }
    var now by remember { mutableLongStateOf(0L) }
    var startAt by remember { mutableLongStateOf(0L) }
    var gapUntil by remember { mutableLongStateOf(0L) }
    var flash by remember { mutableLongStateOf(0L) }
    var orb by remember { mutableStateOf<Orb?>(null) }
    var pop by remember { mutableStateOf<Pop?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var hits by remember { mutableIntStateOf(0) }
    var errors by remember { mutableIntStateOf(0) }
    var sumMs by remember { mutableLongStateOf(0L) }
    var bestMs by remember { mutableIntStateOf(0) }
    var record by remember { mutableStateOf(false) }
    var bestScore by remember { mutableIntStateOf(prefs.getInt("reflex_best", 0)) }
    var bestAvg by remember { mutableIntStateOf(prefs.getInt("reflex_avg", 0)) }

    fun start() {
        score = 0; streak = 0; bestStreak = 0; hits = 0; errors = 0
        sumMs = 0L; bestMs = 0; record = false; pop = null; orb = null; flash = 0L
        val t = SystemClock.uptimeMillis()
        startAt = t; now = t; gapUntil = t + 700
        phase = RPhase.PLAY
    }

    fun tap(pos: Offset, w: Float, h: Float, dens: Float) {
        if (phase != RPhase.PLAY) return
        val t = SystemClock.uptimeMillis()
        val o = orb
        var hit = false
        if (o != null) {
            val dx = pos.x - o.fx * w
            val dy = pos.y - o.fy * h
            val r = o.sizeDp * dens / 2f * 1.25f
            hit = dx * dx + dy * dy <= r * r
        }
        if (o != null && hit) {
            if (!o.bad) {
                val ms = (t - o.born).toInt()
                val base = (900 - ms * 0.8f).toInt().coerceIn(100, 900)
                val mult = (1 + streak / 3).coerceAtMost(5)
                val pts = base * mult
                score += pts
                streak += 1
                if (streak > bestStreak) bestStreak = streak
                hits += 1
                sumMs += ms
                if (bestMs == 0 || ms < bestMs) bestMs = ms
                pop = Pop(o.fx, o.fy, "+$pts", true, t, ms)
                fb()
            } else {
                score = (score - 250).coerceAtLeast(0)
                streak = 0
                errors += 1
                flash = t
                pop = Pop(o.fx, o.fy, "-250", false, t, 0)
            }
            orb = null
            gapUntil = t + Random.nextLong(200, 500)
        } else {
            // Boşa dokunma
            streak = 0
            errors += 1
            flash = t
        }
    }

    LaunchedEffect(phase) {
        if (phase != RPhase.PLAY) return@LaunchedEffect
        var id = 0
        while (true) {
            withFrameNanos { }
            val t = SystemClock.uptimeMillis()
            now = t
            val el = t - startAt
            if (el >= ROUND_MS) break
            val p = el.toFloat() / ROUND_MS
            val o = orb
            if (o != null && t - o.born > o.life) {
                if (!o.bad) { errors += 1; streak = 0; flash = t }
                orb = null
                gapUntil = t + Random.nextLong(180, 450)
            } else if (o == null && t >= gapUntil) {
                val bad = Random.nextFloat() < 0.25f
                id += 1
                orb = Orb(
                    id, Random.nextFloat() * 0.76f + 0.12f, Random.nextFloat() * 0.62f + 0.22f,
                    bad, t, (1300 - 600 * p).toLong(), 84f - 24f * p
                )
            }
        }
        orb = null
        val avg = if (hits > 0) (sumMs / hits).toInt() else 0
        record = score > bestScore
        if (record) {
            bestScore = score
            prefs.edit().putInt("reflex_best", score).apply()
        }
        if (hits >= 5 && (bestAvg == 0 || avg < bestAvg)) {
            bestAvg = avg
            prefs.edit().putInt("reflex_avg", avg).apply()
        }
        phase = RPhase.DONE
    }

    val acc = if (hits + errors == 0) 100 else hits * 100 / (hits + errors)
    val mult = (1 + streak / 3).coerceAtMost(5)
    val leftMs = if (phase == RPhase.PLAY) (ROUND_MS - (now - startAt)).coerceAtLeast(0L) else ROUND_MS
    val leftSec = ((leftMs + 999) / 1000).toInt()

    BoxWithConstraints(
        Modifier.fillMaxSize().glassSurface(RoundedCornerShape(20.dp), cs.surfaceContainer)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown()
                    d.consume()
                    tap(d.position, size.width.toFloat(), size.height.toFloat(), density)
                }
            }
    ) {
        val bw = maxWidth
        val bh = maxHeight

        // Hata anında hafif kırmızı çakma
        if (phase == RPhase.PLAY) {
            val fl = (1f - (now - flash) / 260f).coerceIn(0f, 1f)
            if (fl > 0f) Box(Modifier.fillMaxSize().background(BadCol.copy(alpha = fl * 0.2f)))
        }

        // Hedef / tuzak
        val o = orb
        if (o != null && phase == RPhase.PLAY) {
            val age = (now - o.born).coerceAtLeast(0L)
            val left = (1f - age.toFloat() / o.life).coerceIn(0f, 1f)
            val appear = (age / 140f).coerceIn(0f, 1f)
            val col = if (o.bad) BadCol else GoodCol
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(o.fx * size.width, o.fy * size.height)
                val r = o.sizeDp.dp.toPx() / 2f * (0.6f + 0.4f * appear)
                drawCircle(
                    Brush.radialGradient(listOf(col, col.copy(alpha = 0.55f)), center = c, radius = r),
                    radius = r, center = c
                )
                drawCircle(Color.White.copy(alpha = 0.30f), radius = r * 0.5f, center = c - Offset(r * 0.22f, r * 0.22f))
                drawArc(
                    color = col, startAngle = -90f, sweepAngle = 360f * left, useCenter = false,
                    topLeft = Offset(c.x - r * 1.3f, c.y - r * 1.3f), size = Size(r * 2.6f, r * 2.6f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                if (o.bad) {
                    val k = r * 0.38f
                    val sw = 4.dp.toPx()
                    drawLine(Color.White, Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), sw, StrokeCap.Round)
                    drawLine(Color.White, Offset(c.x - k, c.y + k), Offset(c.x + k, c.y - k), sw, StrokeCap.Round)
                }
            }
        }

        // Puan balonu
        val pp = pop
        if (pp != null && phase == RPhase.PLAY) {
            val a = (now - pp.born) / 700f
            if (a in 0f..1f) {
                Column(
                    Modifier.offset(x = bw * pp.fx - 40.dp, y = bh * pp.fy - 80.dp - (30f * a).dp)
                        .width(80.dp).graphicsLayer { alpha = 1f - a },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        pp.text, color = if (pp.good) GoodCol else BadCol,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    if (pp.good) Text("${pp.ms} ms", color = cs.onSurface, fontSize = 12.sp)
                }
            }
        }

        // Üst bilgi
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text("0:${leftSec.toString().padStart(2, '0')}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Süre", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$score", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary)
                Text("Puan", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    "x$mult seri", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (mult > 1) GoodCol else cs.onSurfaceVariant
                )
                Text("Doğruluk %$acc", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }

        if (phase == RPhase.IDLE) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Refleks Testi", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                Text(
                    "30 saniye boyunca ekranda beliren yeşil hedeflere olabildiğince hızlı dokun. " +
                        "Kırmızı ✕ tuzaklara dokunma. Art arda doğru vurdukça çarpan artar; kaçırmak ya da boşa dokunmak seriyi bozar.",
                    textAlign = TextAlign.Center, color = cs.onSurfaceVariant, fontSize = 14.sp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "En iyi puan: $bestScore" + if (bestAvg > 0) "   ·   En iyi ort. tepki: $bestAvg ms" else "",
                    fontSize = 13.sp, color = cs.onSurface
                )
                Spacer(Modifier.height(18.dp))
                PlayButton({ start() }, Modifier.width(200.dp), "Başla")
            }
        }

        if (phase == RPhase.DONE) {
            val avg = if (hits > 0) (sumMs / hits).toInt() else 0
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (record) "Yeni rekor!" else "Tur bitti", style = MaterialTheme.typography.headlineSmall)
                Text("$score puan", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RStat("Ort. tepki", if (avg > 0) "$avg ms" else "-", Modifier.weight(1f))
                    RStat("En hızlı", if (bestMs > 0) "$bestMs ms" else "-", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RStat("Doğruluk", "%$acc", Modifier.weight(1f))
                    RStat("En uzun seri", "$bestStreak", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("Seviye: ${rating(avg)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                PlayButton({ start() }, Modifier.width(200.dp), "Tekrar oyna")
            }
        }
    }
}

@Composable
private fun RStat(label: String, value: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.glassSurface(RoundedCornerShape(18.dp), cs.surfaceContainerHigh).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}
