package com.gamelauncher.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

// ====================================================================== Veri

/** Bir oyunun seçilen dönemdeki özeti: wrapped görselinin tüm sayıları buradan gelir. */
class WrappedData(
    val name: String,
    val periodLabel: String,
    val total: Long,
    val count: Int,
    val avg: Long,
    val longest: Long,
    val activeDays: Int,
    val bestDay: String?,
    val bestDayMs: Long,
    val peakHour: Int,          // -1: veri yok
    val weekMs: LongArray,      // Pzt..Paz
    val streak: Int,
    val first: Long,
    val last: Long,
    val installed: Long,
    val avgFps: Float,          // -1: ölçülmemiş
    val avgTemp: Float,         // -1: ölçülmemiş
    val reportCount: Int
)

private val trLocale = Locale("tr")

fun buildWrapped(ctx: Context, g: Game, all: List<Session>, r: Range): WrappedData {
    val z = ZoneId.systemDefault()
    val from = rangeStart(r)
    val now = System.currentTimeMillis()

    val mine = all.filter { it.pkg == g.pkg }.mapNotNull {
        val st = maxOf(it.start, from)
        val en = minOf(it.end, now)
        if (en > st) Session(it.pkg, st, en) else null
    }

    // Günlere ve saatlere böl
    val perDay = HashMap<LocalDate, Long>()
    val hours = LongArray(24)
    for (se in mine) {
        var day = Instant.ofEpochMilli(se.start).atZone(z).toLocalDate()
        val lastDay = Instant.ofEpochMilli(se.end).atZone(z).toLocalDate()
        var guard = 0
        while (!day.isAfter(lastDay) && guard++ < 800) {
            val a = maxOf(se.start, day.atStartOfDay(z).toInstant().toEpochMilli())
            val b = minOf(se.end, day.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli())
            if (b > a) perDay[day] = (perDay[day] ?: 0L) + (b - a)
            day = day.plusDays(1)
        }
        var t = se.start
        var g2 = 0
        while (t < se.end && g2++ < 2000) {
            val zdt = Instant.ofEpochMilli(t).atZone(z)
            val nextHour = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val segEnd = minOf(se.end, nextHour)
            hours[zdt.hour] += segEnd - t
            t = segEnd
        }
    }

    val total = mine.sumOf { it.end - it.start }
    val weekMs = LongArray(7)
    perDay.forEach { (d, ms) -> weekMs[d.dayOfWeek.value - 1] += ms }

    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in perDay.keys.sorted()) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        if (run > best) best = run
        prev = d
    }
    val bestEntry = perDay.entries.maxByOrNull { it.value }
    val peak = if (hours.sum() > 0L) hours.indices.maxByOrNull { hours[it] } ?: -1 else -1

    val reports = ReportStore(ctx).forGame(g.pkg).filter { it.end >= from }
    val fps = reports.map { it.avgFps }.filter { it >= 0f }
    val temps = reports.map { it.maxBatTemp }.filter { it > 0f }

    val label = when (r) {
        Range.WEEK -> "Bu hafta"
        Range.MONTH -> LocalDate.now(z).format(DateTimeFormatter.ofPattern("MMMM yyyy", trLocale))
            .replaceFirstChar { it.uppercase(trLocale) }
        Range.YEAR -> LocalDate.now(z).year.toString()
        Range.TODAY -> "Bugün"
        Range.ALL -> "Tüm zamanlar"
    }

    return WrappedData(
        name = g.name,
        periodLabel = label,
        total = total,
        count = mine.size,
        avg = if (mine.isEmpty()) 0L else total / mine.size,
        longest = mine.maxOfOrNull { it.end - it.start } ?: 0L,
        activeDays = perDay.size,
        bestDay = bestEntry?.key?.format(DateTimeFormatter.ofPattern("d MMMM", trLocale)),
        bestDayMs = bestEntry?.value ?: 0L,
        peakHour = peak,
        weekMs = weekMs,
        streak = best,
        first = mine.minOfOrNull { it.start } ?: 0L,
        last = mine.maxOfOrNull { it.end } ?: 0L,
        installed = runCatching { ctx.packageManager.getPackageInfo(g.pkg, 0).firstInstallTime }.getOrDefault(0L),
        avgFps = if (fps.isEmpty()) -1f else fps.average().toFloat(),
        avgTemp = if (temps.isEmpty()) -1f else temps.average().toFloat(),
        reportCount = reports.size
    )
}

// ====================================================================== Çizim yardımcıları

private const val W = 1080
private const val H = 1920

private fun paint(
    color: Int, size: Float, face: String = "sans-serif",
    bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize = size
    textAlign = align
    typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
    letterSpacing = spacing
}

private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun ca(color: Int, a: Int): Int = ColorUtils.setAlphaComponent(color, a)

private fun Paint.fit(text: String, maxW: Float, size: Float, min: Float = 28f) {
    textSize = size
    while (textSize > min && measureText(text) > maxW) textSize -= 2f
}

private fun ell(t: String, p: Paint, maxW: Float): String {
    if (p.measureText(t) <= maxW) return t
    var s = t
    while (s.length > 1 && p.measureText("$s…") > maxW) s = s.dropLast(1)
    return "$s…"
}

private fun wrapLines(text: String, p: Paint, maxW: Float, maxLines: Int): List<String> {
    val lines = ArrayList<String>()
    var cur = ""
    for (w in text.split(" ")) {
        val t = if (cur.isEmpty()) w else "$cur $w"
        if (p.measureText(t) <= maxW) cur = t
        else {
            if (cur.isNotEmpty()) lines += cur
            cur = w
        }
    }
    if (cur.isNotEmpty()) lines += cur
    val out = lines.take(maxLines).toMutableList()
    if (lines.size > maxLines && out.isNotEmpty()) out[out.size - 1] = out[out.size - 1] + "…"
    return out.map { ell(it, p, maxW) }
}

private fun shiftHue(color: Int, deg: Float): Int {
    val hsv = FloatArray(3)
    AColor.colorToHSV(color, hsv)
    hsv[0] = (hsv[0] + deg + 360f) % 360f
    return AColor.HSVToColor(hsv)
}

private fun mix(a: Int, b: Int, t: Float): Int = ColorUtils.blendARGB(a, b, t)

private fun drawCoverRect(c: Canvas, bmp: Bitmap, l: Float, t: Float, size: Float, radius: Float) {
    val dst = RectF(l, t, l + size, t + size)
    val side = minOf(bmp.width, bmp.height)
    val src = Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
    c.save()
    c.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
    c.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    c.restore()
}

private fun loadCoverBmp(ctx: Context, g: Game): Bitmap {
    val path = g.cover ?: g.autoCover
    if (path != null) {
        runCatching {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, b)
            var sample = 1
            while (b.outWidth / sample > 1100) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.let { return it }
    }
    runCatching { ctx.packageManager.getApplicationIcon(g.pkg).toBitmap(512, 512) }.getOrNull()?.let { return it }
    return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF444455.toInt()) }
}

/** Kapağın baskın canlı rengi. */
private fun accentOf(b: Bitmap): Int {
    val small = Bitmap.createScaledBitmap(b, 24, 24, true)
    val hsv = FloatArray(3)
    var r = 0.0
    var gr = 0.0
    var bl = 0.0
    var ws = 0.0
    for (y in 0 until 24) for (x in 0 until 24) {
        val px = small.getPixel(x, y)
        AColor.colorToHSV(px, hsv)
        val w = (hsv[1] * hsv[2]).toDouble().let { it * it }
        r += AColor.red(px) * w
        gr += AColor.green(px) * w
        bl += AColor.blue(px) * w
        ws += w
    }
    if (ws < 0.5) return 0xFF7C5CFF.toInt()
    val avg = AColor.rgb((r / ws).toInt(), (gr / ws).toInt(), (bl / ws).toInt())
    AColor.colorToHSV(avg, hsv)
    hsv[1] = maxOf(hsv[1], 0.55f)
    hsv[2] = hsv[2].coerceIn(0.75f, 1f)
    return AColor.HSVToColor(hsv)
}

/** Kapağı 9:16'ya kırpıp bulanıklaştırır (küçültüp büyüterek). */
private fun blurredBackground(src: Bitmap): Bitmap {
    val cw: Int
    val ch: Int
    if (src.width * 16 > src.height * 9) {
        ch = src.height
        cw = (src.height * 9 / 16).coerceAtLeast(1)
    } else {
        cw = src.width
        ch = (src.width * 16 / 9).coerceAtMost(src.height).coerceAtLeast(1)
    }
    val x = ((src.width - cw) / 2).coerceAtLeast(0)
    val y = ((src.height - ch) / 2).coerceAtLeast(0)
    val crop = Bitmap.createBitmap(src, x, y, minOf(cw, src.width - x), minOf(ch, src.height - y))
    val a = Bitmap.createScaledBitmap(crop, 180, 320, true)
    val b = Bitmap.createScaledBitmap(a, 27, 48, true)
    val c = Bitmap.createScaledBitmap(b, 270, 480, true)
    return Bitmap.createScaledBitmap(c, W, H, true)
}

private fun hm(ms: Long): Pair<Long, Long> {
    val m = ms / 60_000
    return (m / 60) to (m % 60)
}

private fun fmtDay(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("d MMM yyyy", trLocale).format(Date(ms))

private fun hourText(h: Int): String =
    if (h < 0) "—" else String.format(Locale.US, "%02d:00–%02d:00", h, (h + 1) % 24)

private fun tempText(t: Float): String =
    if (t <= 0f) "—" else String.format(trLocale, "%.1f°C", t)

private fun fpsText(f: Float): String = if (f < 0f) "—" else f.toInt().toString()

private val dayNames = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

// ====================================================================== Tarz 1: Neon

private fun styleNeon(d: WrappedData, cover: Bitmap, ac: Int): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val a2 = shiftHue(ac, 45f)
    val bg = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            intArrayOf(mix(ac, AColor.BLACK, 0.55f), mix(a2, AColor.BLACK, 0.80f), 0xFF07070B.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }
    c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
    c.drawCircle(W * 0.95f, 240f, 380f, fillPaint(ca(ac, 70)))
    c.drawCircle(-30f, 1560f, 430f, fillPaint(ca(a2, 60)))
    c.drawCircle(W * 0.8f, 1250f, 160f, fillPaint(ca(AColor.WHITE, 18)))

    val white = AColor.WHITE
    c.drawText("WRAPPED", 80f, 130f, paint(white, 46f, "sans-serif-black", true, spacing = 0.25f))
    c.drawText(d.periodLabel, W - 80f, 130f, paint(ca(white, 210), 40f, align = Paint.Align.RIGHT))

    // Kapak: hafif eğik, beyaz çerçeveli
    c.save()
    c.rotate(-4f, W / 2f, 490f)
    c.drawRoundRect(RectF(250f, 200f, 830f, 780f), 80f, 80f, fillPaint(ca(AColor.BLACK, 90)))
    c.drawRoundRect(RectF(240f, 190f, 820f, 770f), 80f, 80f, fillPaint(white))
    drawCoverRect(c, cover, 252f, 202f, 556f, 70f)
    c.restore()

    // Oyun adı
    val np = paint(white, 84f, "sans-serif-black", true, Paint.Align.CENTER)
    val lines = wrapLines(d.name, np, W - 160f, 2)
    var y = 900f
    lines.forEach { c.drawText(it, W / 2f, y, np); y += 96f }

    // Toplam süre
    val labelY = y + 40f
    c.drawText("TOPLAM SÜRE", W / 2f, labelY, paint(ca(white, 190), 36f, align = Paint.Align.CENTER, spacing = 0.2f))
    val (h, m) = hm(d.total)
    val tp = paint(white, 150f, "sans-serif-black", true, Paint.Align.CENTER)
    val totalText = if (h > 0) "$h sa $m dk" else "$m dk"
    tp.fit(totalText, W - 160f, 150f)
    c.drawText(totalText, W / 2f, labelY + 150f, tp)

    // 2x2 kutu
    val items = listOf(
        "Oturum" to "${d.count}",
        "Ort. oturum" to fmtDur(d.avg),
        "En uzun" to fmtDur(d.longest),
        "Aktif gün" to "${d.activeDays}"
    )
    val cw = 448f
    val chh = 190f
    items.forEachIndexed { i, (label, value) ->
        val col = i % 2
        val row = i / 2
        val l = 80f + col * (cw + 24f)
        val t = 1380f + row * (chh + 24f)
        c.drawRoundRect(RectF(l, t, l + cw, t + chh), 44f, 44f, fillPaint(ca(white, 30)))
        c.drawText(label, l + 36f, t + 62f, paint(ca(white, 200), 32f))
        val vp = paint(white, 64f, "sans-serif-black", true)
        vp.fit(value, cw - 72f, 64f, 30f)
        c.drawText(value, l + 36f, t + 142f, vp)
    }
    c.drawText("Game Launcher", W / 2f, 1850f, paint(ca(white, 150), 34f, align = Paint.Align.CENTER, spacing = 0.1f))
    return bmp
}

// ====================================================================== Tarz 2: Minimal poster

private fun styleMinimal(d: WrappedData, cover: Bitmap, ac: Int): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val ink = 0xFF111111.toInt()
    c.drawColor(0xFFF3EFE6.toInt())

    c.drawText("OYUN ÖZETİ", 80f, 120f, paint(ink, 34f, "sans-serif-medium", true, spacing = 0.2f))
    c.drawText(d.periodLabel, W - 80f, 120f, paint(ca(ink, 150), 34f, align = Paint.Align.RIGHT))
    c.drawRect(80f, 148f, W - 80f, 152f, fillPaint(ink))

    // Ad solda, kapak sağda
    drawCoverRect(c, cover, W - 80f - 280f, 200f, 280f, 60f)
    val np = paint(ink, 88f, "sans-serif-black", true)
    val lines = wrapLines(d.name, np, 600f, 3)
    var y = 300f
    lines.forEach { c.drawText(it, 80f, y, np); y += 98f }

    // Dev sayı
    c.drawText("TOPLAM OYNAMA", 80f, 620f, paint(ca(ink, 150), 34f, "sans-serif-medium", true, spacing = 0.2f))
    val (h, m) = hm(d.total)
    val big = if (h > 0) h.toString() else m.toString()
    val unit1 = if (h > 0) "saat" else "dakika"
    val unit2 = if (h > 0) "$m dk" else ""
    val bp = paint(ink, 400f, "sans-serif-black", true)
    bp.fit(big, 620f, 400f, 120f)
    c.drawText(big, 70f, 960f, bp)
    val ux = 80f + bp.measureText(big) + 30f
    c.drawText(unit1, ux, 960f, paint(ink, 72f, "sans-serif-medium", true))
    if (unit2.isNotEmpty()) c.drawText(unit2, ux, 880f, paint(ca(ink, 170), 60f, "sans-serif-medium"))
    c.drawRoundRect(RectF(80f, 1010f, W - 80f, 1030f), 10f, 10f, fillPaint(ac))

    // Satırlar
    val rows = ArrayList<Pair<String, String>>()
    rows += "Oturum sayısı" to "${d.count}"
    rows += "Ortalama oturum" to fmtDur(d.avg)
    rows += "En uzun oturum" to fmtDur(d.longest)
    rows += "En yoğun gün" to (d.bestDay?.let { "$it • ${fmtDur(d.bestDayMs)}" } ?: "—")
    rows += "En çok oynanan saat" to hourText(d.peakHour)
    rows += "En uzun seri" to "${d.streak} gün"
    if (d.avgFps >= 0f) rows += "Ortalama FPS" to fpsText(d.avgFps)
    rows += "İlk oynama" to fmtDay(d.first)
    rows += "Son oynama" to fmtDay(d.last)
    val shown = rows.take(9)
    val lp = paint(ca(ink, 140), 38f)
    val vp = paint(ink, 42f, "sans-serif-medium", true, Paint.Align.RIGHT)
    var ry = 1110f
    shown.forEach { (label, value) ->
        c.drawText(label, 80f, ry, lp)
        c.drawText(ell(value, vp, 520f), W - 80f, ry, vp)
        c.drawRect(80f, ry + 22f, W - 80f, ry + 24f, fillPaint(ca(ink, 40)))
        ry += 80f
    }
    c.drawText("Game Launcher", 80f, 1860f, paint(ca(ink, 120), 32f, spacing = 0.1f))
    return bmp
}

// ====================================================================== Tarz 3: Cam kart / rapor

private fun styleGlass(d: WrappedData, cover: Bitmap, ac: Int): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val white = AColor.WHITE
    c.drawBitmap(blurredBackground(cover), 0f, 0f, null)
    c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), fillPaint(ca(AColor.BLACK, 120)))

    // Cam kart
    val card = RectF(60f, 330f, 1020f, 1760f)
    c.drawRoundRect(card, 70f, 70f, fillPaint(ca(white, 34)))
    c.drawRoundRect(card, 70f, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = ca(white, 80)
    })

    // Kapak, kartın üst kenarına taşar
    c.drawRoundRect(RectF(382f, 172f, 698f, 488f), 86f, 86f, fillPaint(ca(white, 230)))
    drawCoverRect(c, cover, 390f, 180f, 300f, 80f)

    val np = paint(white, 64f, "sans-serif-black", true, Paint.Align.CENTER)
    np.fit(d.name, 840f, 64f, 34f)
    c.drawText(ell(d.name, np, 840f), W / 2f, 590f, np)
    c.drawText(d.periodLabel, W / 2f, 650f, paint(ca(white, 190), 36f, align = Paint.Align.CENTER))

    val (h, m) = hm(d.total)
    val totalText = if (h > 0) "$h sa $m dk" else "$m dk"
    val tp = paint(white, 140f, "sans-serif-black", true, Paint.Align.CENTER)
    tp.fit(totalText, 840f, 140f, 60f)
    c.drawText(totalText, W / 2f, 800f, tp)
    c.drawText("toplam oynama süresi", W / 2f, 856f, paint(ca(white, 180), 34f, align = Paint.Align.CENTER))
    c.drawRect(110f, 900f, 970f, 902f, fillPaint(ca(white, 60)))

    // Haftanın günleri
    c.drawText("Haftanın günleri", 110f, 960f, paint(ca(white, 190), 32f, "sans-serif-medium", true))
    val maxW = (d.weekMs.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val barBase = 1230f
    val labelP = paint(ca(white, 190), 28f, align = Paint.Align.CENTER)
    for (i in 0 until 7) {
        val cx = 120f + 120f * i + 60f
        val bh = (d.weekMs[i].toFloat() / maxW * 200f).let { if (d.weekMs[i] > 0L) it.coerceAtLeast(10f) else 6f }
        val col = if (d.weekMs[i] == maxW && maxW > 1L) ac else ca(white, 120)
        c.drawRoundRect(RectF(cx - 35f, barBase - bh, cx + 35f, barBase), 18f, 18f, fillPaint(col))
        c.drawText(dayNames[i], cx, barBase + 44f, labelP)
    }

    // Rapor bilgileri
    val cells = listOf(
        "Oturum" to "${d.count}", "Ort. oturum" to fmtDur(d.avg),
        "En uzun oturum" to fmtDur(d.longest), "En çok oynanan saat" to hourText(d.peakHour).substringBefore("–"),
        "Ort. FPS" to fpsText(d.avgFps), "Ort. en yüksek ısı" to tempText(d.avgTemp)
    )
    cells.forEachIndexed { i, (label, value) ->
        val col = i % 2
        val row = i / 2
        val x = 110f + col * 460f
        val y = 1345f + row * 130f
        c.drawText(label, x, y, paint(ca(white, 180), 30f))
        val vp = paint(white, 52f, "sans-serif-black", true)
        vp.fit(value, 400f, 52f, 28f)
        c.drawText(value, x, y + 58f, vp)
    }
    val dates = "İlk: ${fmtDay(d.first)}  •  Son: ${fmtDay(d.last)}"
    val dp = paint(ca(white, 190), 30f, align = Paint.Align.CENTER)
    dp.fit(dates, 840f, 30f, 22f)
    c.drawText(dates, W / 2f, 1715f, dp)
    c.drawText("Game Launcher", W / 2f, 1850f, paint(ca(white, 150), 34f, align = Paint.Align.CENTER, spacing = 0.1f))
    return bmp
}

// ====================================================================== Dışarı açılan işlevler

fun renderWrapped(ctx: Context, g: Game, sessions: List<Session>, range: Range, style: Int): Bitmap {
    val d = buildWrapped(ctx, g, sessions, range)
    val cover = loadCoverBmp(ctx, g)
    val ac = accentOf(cover)
    return when (style) {
        0 -> styleNeon(d, cover, ac)
        1 -> styleMinimal(d, cover, ac)
        else -> styleGlass(d, cover, ac)
    }
}

fun shareWrapped(ctx: Context, bmp: Bitmap) {
    runCatching {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "wrapped.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Wrapped'i paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(ctx, "Paylaşılamadı", Toast.LENGTH_SHORT).show()
    }
}

/** Galerideki Pictures/GameLauncher klasörüne kaydeder (Android 10+). */
fun saveWrapped(ctx: Context, bmp: Bitmap, gameName: String) {
    if (Build.VERSION.SDK_INT < 29) {
        Toast.makeText(ctx, "Bu Android sürümünde Paylaş'ı kullan", Toast.LENGTH_SHORT).show()
        return
    }
    val ok = runCatching {
        val safe = gameName.filter { it.isLetterOrDigit() }.take(24).ifEmpty { "oyun" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "wrapped_${safe}_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GameLauncher")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
        true
    }.getOrDefault(false)
    Toast.makeText(ctx, if (ok) "Galeriye kaydedildi" else "Kaydedilemedi", Toast.LENGTH_SHORT).show()
}

// ====================================================================== Ekran

@Composable
fun WrappedPage(s: AppState, g: Game, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var style by remember { mutableIntStateOf(0) }
    var rangeIdx by remember { mutableIntStateOf(2) }
    val ranges = listOf(Range.WEEK, Range.MONTH, Range.YEAR, Range.ALL)

    val bmp by produceState<Bitmap?>(null, style, rangeIdx, g.cover, g.autoCover, s.sessions) {
        value = null
        value = withContext(Dispatchers.Default) {
            runCatching { renderWrapped(ctx, g, s.sessions, ranges[rangeIdx], style) }.getOrNull()
        }
    }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTopBar("Wrapped", onClose)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp).navigationBarsPadding()
            ) {
                Box(
                    Modifier.fillMaxWidth(0.68f).align(Alignment.CenterHorizontally).aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val b = bmp
                    if (b == null) CircularProgressIndicator()
                    else Image(b.asImageBitmap(), "Wrapped önizleme", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }

                SectionLabel("Tarz")
                SegmentedChoice(listOf("Neon", "Minimal", "Cam"), style) { style = it }
                SectionLabel("Dönem")
                SegmentedChoice(listOf("Hafta", "Ay", "Yıl", "Tümü"), rangeIdx) { rangeIdx = it }

                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Paylaş", { bmp?.let { shareWrapped(ctx, it) } }, Modifier.weight(1f), icon = Icons.Default.Share)
                    SoftButton("Galeriye kaydet", { bmp?.let { saveWrapped(ctx, it, g.name) } }, Modifier.weight(1f), icon = Icons.Default.Save)
                }
                Text(
                    "Görsel 1080×1920 (hikaye boyutu) olarak üretilir. FPS ve ısı değerleri, oyun raporu açıkken oynadığın oturumlardan gelir.",
                    Modifier.padding(top = 12.dp), fontSize = 11.sp, color = cs.onSurfaceVariant
                )
            }
        }
    }
}
