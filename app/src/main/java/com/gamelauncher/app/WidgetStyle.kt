package com.gamelauncher.app

import android.content.Context

/** Widget renkleri (Ayarlar > Widget renkleri'nden değiştirilir). Renkler 0xAARRGGBB, bg opak saklanır. */
data class WStyle(
    val bg: Int,
    val alpha: Int,          // 0..255 arka plan saydamlığı
    val text: Int,
    val accent: Int,
    val tempColors: Boolean  // sıcaklığa göre yeşil/turuncu/kırmızı
) {
    val textDim: Int get() = (text and 0x00FFFFFF) or (0xB3 shl 24)
    val textFaint: Int get() = (text and 0x00FFFFFF) or (0x2E shl 24)
    val bgWithAlpha: Int get() = (bg and 0x00FFFFFF) or (alpha shl 24)
}

object WidgetStyle {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun load(ctx: Context): WStyle {
        val p = prefs(ctx)
        return WStyle(
            bg = p.getInt("w_bg", 0xFF1B1A22.toInt()),
            alpha = p.getInt("w_alpha", 230),
            text = p.getInt("w_text", 0xFFFFFFFF.toInt()),
            accent = p.getInt("w_accent", 0xFFBFA8FF.toInt()),
            tempColors = p.getBoolean("w_tempcolor", false)
        )
    }

    fun save(ctx: Context, s: WStyle) {
        prefs(ctx).edit()
            .putInt("w_bg", s.bg or (0xFF shl 24))
            .putInt("w_alpha", s.alpha)
            .putInt("w_text", s.text or (0xFF shl 24))
            .putInt("w_accent", s.accent or (0xFF shl 24))
            .putBoolean("w_tempcolor", s.tempColors)
            .apply()
    }
}
