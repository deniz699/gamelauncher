package com.gamelauncher.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/** Widget'lar için Canvas ile çizilen görseller (yay göstergeler, çubuklar, ikonlar). */
object WidgetGfx {

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---------------------------------------------------------- Sıcaklık hapı (Samsung tarzı)

    fun tempsBitmap(wPx: Int, hPx: Int, st: WStyle, battery: Float?, cpu: Float?): Bitmap {
        val bmp = Bitmap.createBitmap(wPx.coerceAtLeast(40), hPx.coerceAtLeast(20), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()

        val bg = paint().apply { color = st.bgWithAlpha }
        c.drawRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, bg)

        // Gösterge + altındaki etiket birlikte ~2.3 r yüksekliğinde; pill'in içine sığacak şekilde ortalanır
        val pad = h * 0.09f
        val r = minOf((h - 2 * pad) / 2.3f, w * 0.17f)
        val cy = (h - 2.3f * r) / 2f + 1.13f * r
        gauge(c, w * 0.27f, cy, r, battery, 20f, 50f, phoneIcon = true, label = "Pil", st = st)
        gauge(c, w * 0.73f, cy, r, cpu, 30f, 90f, phoneIcon = false, label = "CPU", st = st)
        return bmp
    }

    private fun tempColor(f: Float): Int = when {
        f < 0.45f -> 0xFF22C55E.toInt()
        f < 0.70f -> 0xFFFBBF24.toInt()
        else -> 0xFFEF4444.toInt()
    }

    private fun gauge(
        c: Canvas, cx: Float, cy: Float, r: Float,
        value: Float?, min: Float, max: Float, phoneIcon: Boolean, label: String, st: WStyle
    ) {
        val stroke = r * 0.26f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val p = paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        p.color = st.textFaint
        c.drawArc(rect, 150f, 240f, false, p)
        if (value != null) {
            val f = ((value - min) / (max - min)).coerceIn(0.05f, 1f)
            p.color = if (st.tempColors) tempColor(f) else st.accent
            c.drawArc(rect, 150f, 240f * f, false, p)
        }

        // İkon (yayın içinde, üstte)
        val iconCy = cy - r * 0.22f
        val fill = paint().apply { color = st.text }
        if (phoneIcon) {
            val iw = r * 0.34f
            val ih = r * 0.58f
            val body = RectF(cx - iw / 2, iconCy - ih / 2, cx + iw / 2, iconCy + ih / 2)
            c.drawRoundRect(body, iw * 0.28f, iw * 0.28f, fill)
            val dot = paint().apply { color = st.bg }
            c.drawCircle(cx, body.top + ih * 0.12f, iw * 0.09f, dot)
        } else {
            val s = r * 0.40f
            val body = RectF(cx - s / 2, iconCy - s / 2, cx + s / 2, iconCy + s / 2)
            c.drawRoundRect(body, s * 0.16f, s * 0.16f, fill)
            val inner = paint().apply { color = st.bg }
            val q = s * 0.26f
            c.drawRoundRect(RectF(cx - q, iconCy - q, cx + q, iconCy + q), q * 0.3f, q * 0.3f, inner)
            val pin = paint().apply { color = st.text; strokeWidth = s * 0.10f; strokeCap = Paint.Cap.ROUND }
            for (i in -1..1) {
                val o = i * s * 0.28f
                val ext = s * 0.18f
                c.drawLine(cx + o, body.top, cx + o, body.top - ext, pin)
                c.drawLine(cx + o, body.bottom, cx + o, body.bottom + ext, pin)
                c.drawLine(body.left, iconCy + o, body.left - ext, iconCy + o, pin)
                c.drawLine(body.right, iconCy + o, body.right + ext, iconCy + o, pin)
            }
        }

        // Değer
        val tp = paint().apply {
            color = st.text
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = r * 0.50f
        }
        val txt = if (value != null) "${value.roundToInt()}°" else "—"
        val fm = tp.fontMetrics
        val ty = cy + r * 0.45f - (fm.ascent + fm.descent) / 2f
        c.drawText(txt, cx, ty, tp)

        // Etiket (göstergenin altında)
        val lp = paint().apply {
            color = st.textDim
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            textSize = r * 0.40f
        }
        val lfm = lp.fontMetrics
        c.drawText(label, cx, cy + r * 0.95f - (lfm.ascent + lfm.descent) / 2f, lp)
    }


    // ---------------------------------------------------------- Telefon özeti (boyuta uyum sağlayan)

    data class InfoData(
        val model: String, val sys: String,
        val ramUsed: Long, val ramTotal: Long,
        val stoUsed: Long, val stoTotal: Long,
        val battery: Float?, val cpu: Float?
    )

    private fun fitText(text: String, p: android.text.TextPaint, maxW: Float): String =
        android.text.TextUtils.ellipsize(text, p, maxW.coerceAtLeast(1f), android.text.TextUtils.TruncateAt.END).toString()

    /**
     * Tüm içerik dp biriminde çizilir. Yükseklik azaldıkça önce satırlar (sıcaklık, sistem bilgisi,
     * model adı) sırayla gizlenir; en küçük boyutta RAM ve Depolama yan yana gelir.
     * Hiçbir boyutta içerik taşmaz / kesilmez.
     */
    fun infoBitmap(wDp: Float, hDp: Float, scale: Float, st: WStyle, d: InfoData): Bitmap {
        val bmp = Bitmap.createBitmap(
            (wDp * scale).toInt().coerceAtLeast(40), (hDp * scale).toInt().coerceAtLeast(20), Bitmap.Config.ARGB_8888
        )
        val c = Canvas(bmp)
        c.scale(bmp.width / wDp, bmp.height / hDp)

        val radius = minOf(22f, hDp / 2f)
        c.drawRoundRect(RectF(0f, 0f, wDp, hDp), radius, radius, paint().apply { color = st.bgWithAlpha })
        c.drawRoundRect(
            RectF(0.5f, 0.5f, wDp - 0.5f, hDp - 0.5f), radius, radius,
            paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x22FFFFFF }
        )

        // Dikey yükler (dp)
        val barH = 7f
        val rowH = 14f          // etiket satırı
        val gapBar = 4f
        val modelH = 19f
        val sysH = 14f
        val tempH = 17f
        val gap = 9f

        val showTemp: Boolean
        val showSys: Boolean
        val showModel: Boolean
        val sideBySide: Boolean
        val pad: Float

        fun need(model: Boolean, sys: Boolean, temp: Boolean, pd: Float): Float {
            var n = pd * 2
            if (model) n += modelH
            if (sys) n += sysH
            if (model || sys) n += gap
            n += rowH + gapBar + barH          // RAM
            n += gap + rowH + gapBar + barH    // Depolama
            if (temp) n += gap + tempH
            return n
        }

        // (model, sys, temp, kenar boşluğu)
        val tiers = listOf(
            floatArrayOf(1f, 1f, 1f, 14f),
            floatArrayOf(1f, 1f, 0f, 14f),
            floatArrayOf(1f, 0f, 0f, 12f),
            floatArrayOf(1f, 0f, 0f, 8f),
            floatArrayOf(0f, 0f, 0f, 10f)
        )
        var chosen: BooleanArray? = null
        var pd = 14f
        for (t in tiers) {
            if (need(t[0] > 0, t[1] > 0, t[2] > 0, t[3]) <= hDp) {
                chosen = booleanArrayOf(t[0] > 0, t[1] > 0, t[2] > 0); pd = t[3]; break
            }
        }
        if (chosen == null) {
            // Dikeyde sığmıyor: RAM ve Depolama yan yana tek satır
            chosen = booleanArrayOf(false, false, false)
            sideBySide = true
            pd = (hDp * 0.16f).coerceIn(4f, 10f)
        } else sideBySide = false
        showModel = chosen[0]; showSys = chosen[1]; showTemp = chosen[2]
        pad = pd

        val left = if (hDp < 60f) 12f else 14f
        val right = wDp - left
        val inner = right - left

        val bold = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        val reg = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }

        fun text(t: String, x: Float, baseTop: Float, size: Float, p: android.text.TextPaint, col: Int, align: Paint.Align, maxW: Float) {
            p.textSize = size; p.color = col; p.textAlign = align
            val fm = p.fontMetrics
            c.drawText(fitText(t, p, maxW), x, baseTop - fm.ascent, p)
        }

        fun bar(x: Float, y: Float, w: Float, f: Float) {
            val p = paint()
            p.color = st.textFaint
            c.drawRoundRect(RectF(x, y, x + w, y + barH), barH / 2, barH / 2, p)
            val ff = f.coerceIn(0f, 1f)
            if (ff > 0f) {
                p.color = st.accent
                c.drawRoundRect(RectF(x, y, x + maxOf(w * ff, barH), y + barH), barH / 2, barH / 2, p)
            }
        }

        fun metric(label: String, value: String, frac: Float, x: Float, y: Float, w: Float) {
            // değer sağa yaslı; etiket solda, değere yer bırakarak
            reg.textSize = 11f
            val vw = reg.measureText(value)
            text(value, x + w, y, 11f, reg, st.textDim, Paint.Align.RIGHT, w * 0.75f)
            text(label, x, y, 11f, bold, st.text, Paint.Align.LEFT, (w - vw - 6f).coerceAtLeast(10f))
            bar(x, y + rowH + gapBar - 2f, w, frac)
        }

        val ramF = d.ramUsed.toFloat() / d.ramTotal.coerceAtLeast(1)
        val stoF = d.stoUsed.toFloat() / d.stoTotal.coerceAtLeast(1)

        if (sideBySide) {
            val colGap = 14f
            val colW = (inner - colGap) / 2f
            val blockH = rowH + gapBar + barH - 2f
            val y = (hDp - blockH) / 2f
            metric("RAM", "${gb(d.ramUsed)}", ramF, left, y, colW)
            metric("Depolama", "${gb(d.stoUsed)}", stoF, left + colW + colGap, y, colW)
            return bmp
        }

        // İçeriği dikeyde ortala
        var y = (hDp - need(showModel, showSys, showTemp, pad)) / 2f + pad
        if (showModel) {
            text(d.model, left, y, 15f, bold, st.text, Paint.Align.LEFT, inner)
            y += modelH
        }
        if (showSys) {
            text(d.sys, left, y, 11f, reg, st.textDim, Paint.Align.LEFT, inner)
            y += sysH
        }
        if (showModel || showSys) y += gap
        metric("RAM", "${gb(d.ramUsed)} / ${gb(d.ramTotal)}", ramF, left, y, inner)
        y += rowH + gapBar + barH + gap
        metric("Depolama", "${gb(d.stoUsed)} / ${gb(d.stoTotal)}", stoF, left, y, inner)
        y += rowH + gapBar + barH
        if (showTemp) {
            y += gap
            fun t(v: Float?) = v?.let { String.format(java.util.Locale.US, "%.1f °C", it) } ?: "—"
            text("Batarya ${t(d.battery)} • İşlemci ${t(d.cpu)}", left, y, 12f, bold, st.accent, Paint.Align.LEFT, inner)
        }
        return bmp
    }

    // ---------------------------------------------------------- Doluluk çubuğu

    fun barBitmap(fraction: Float, st: WStyle, w: Int = 640, h: Int = 24): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = paint()
        p.color = st.textFaint
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, p)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            p.color = st.accent
            val fw = (w * f).coerceAtLeast(h.toFloat())
            c.drawRoundRect(RectF(0f, 0f, fw, h.toFloat()), h / 2f, h / 2f, p)
        }
        return bmp
    }

    // ---------------------------------------------------------- Oyun logosu (yuvarlatılmış köşe)

    fun appIcon(ctx: Context, pkg: String, size: Int = 96): Bitmap? = runCatching {
        val src = ctx.packageManager.getApplicationIcon(pkg).toBitmap(size, size)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val path = Path().apply {
            addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.24f, size * 0.24f, Path.Direction.CW)
        }
        c.clipPath(path)
        c.drawBitmap(src, 0f, 0f, null)
        out
    }.getOrNull()
}
