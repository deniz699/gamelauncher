package com.gamelauncher.app

import android.content.Context

/** Widget renkleri. Renkler 0xAARRGGBB, bg opak saklanır. */
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

/**
 * İki katman:
 *  - Varsayılan renkler (Ayarlar > Varsayılan widget renkleri)
 *  - Widget'a özel renkler (ana ekranda widget'a basılı tut > Ayarlar). Varsa varsayılanın yerine geçer.
 */
object WidgetStyle {
    val FACTORY = WStyle(
        bg = 0xFF000000.toInt(),
        alpha = 230,
        text = 0xFFFFFFFF.toInt(),
        accent = 0xFFBFA8FF.toInt(),
        tempColors = false
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val FIELDS = listOf("bg", "alpha", "text", "accent", "tempcolor")

    private fun read(ctx: Context, prefix: String): WStyle {
        val p = prefs(ctx)
        return WStyle(
            bg = p.getInt("${prefix}_bg", FACTORY.bg),
            alpha = p.getInt("${prefix}_alpha", FACTORY.alpha),
            text = p.getInt("${prefix}_text", FACTORY.text),
            accent = p.getInt("${prefix}_accent", FACTORY.accent),
            tempColors = p.getBoolean("${prefix}_tempcolor", FACTORY.tempColors)
        )
    }

    private fun write(ctx: Context, prefix: String, s: WStyle) {
        prefs(ctx).edit()
            .putInt("${prefix}_bg", s.bg or (0xFF shl 24))
            .putInt("${prefix}_alpha", s.alpha)
            .putInt("${prefix}_text", s.text or (0xFF shl 24))
            .putInt("${prefix}_accent", s.accent or (0xFF shl 24))
            .putBoolean("${prefix}_tempcolor", s.tempColors)
            .apply()
    }

    private fun remove(ctx: Context, prefix: String) {
        val e = prefs(ctx).edit()
        FIELDS.forEach { e.remove("${prefix}_$it") }
        e.apply()
    }

    // ---- Varsayılan
    fun loadDefault(ctx: Context): WStyle = read(ctx, "w")
    fun saveDefault(ctx: Context, s: WStyle) = write(ctx, "w", s)

    // ---- Widget'a özel
    fun hasCustom(ctx: Context, id: Int): Boolean = id != 0 && prefs(ctx).contains("wc${id}_bg")
    fun saveFor(ctx: Context, id: Int, s: WStyle) = write(ctx, "wc$id", s)
    fun clearFor(ctx: Context, id: Int) = remove(ctx, "wc$id")

    /** Widget için geçerli renkler: özel varsa o, yoksa varsayılan. */
    fun load(ctx: Context, id: Int = 0): WStyle =
        if (hasCustom(ctx, id)) read(ctx, "wc$id") else loadDefault(ctx)

    /** Varsayılanı ve tüm widget'lara özel renkleri silip fabrika renklerine döner. */
    fun resetAll(ctx: Context) {
        val p = prefs(ctx)
        val e = p.edit()
        FIELDS.forEach { e.remove("w_$it") }
        p.all.keys.filter { it.startsWith("wc") }.forEach { e.remove(it) }
        e.apply()
    }
}
