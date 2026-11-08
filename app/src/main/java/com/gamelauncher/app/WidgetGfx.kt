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

        val r = minOf(h * 0.38f, w * 0.17f)
        // Yayın alt uçları merkezden aşağıda kalır; görünür kutuyu dikeyde ortala
        val cy = h / 2f + r * 0.19f
        gauge(c, w * 0.27f, cy, r, battery, 20f, 50f, phoneIcon = true, st = st)
        gauge(c, w * 0.73f, cy, r, cpu, 30f, 90f, phoneIcon = false, st = st)
        return bmp
    }

    private fun tempColor(f: Float): Int = when {
        f < 0.45f -> 0xFF22C55E.toInt()
        f < 0.70f -> 0xFFFBBF24.toInt()
        else -> 0xFFEF4444.toInt()
    }

    private fun gauge(
        c: Canvas, cx: Float, cy: Float, r: Float,
        value: Float?, min: Float, max: Float, phoneIcon: Boolean, st: WStyle
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
        val ty = cy + r * 0.56f - (fm.ascent + fm.descent) / 2f
        c.drawText(txt, cx, ty, tp)
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
