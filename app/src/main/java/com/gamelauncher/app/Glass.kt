package com.gamelauncher.app

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

// "Liquid Glass" (buzlu cam) modu.
// Cam etkisi: renkli, yumuşak ışıklı bir arka plan + üstünde yarı saydam beyaz katmanlar,
// üst-sol köşeden parlayan ince bir kenar çizgisi ve hafif bir ışık yansıması.
// Mod kapalıyken tüm yardımcılar eski düz (flat) görünümü aynen üretir.

val LocalGlass = compositionLocalOf { false }

/** Cam modunda diyalog / menü için neredeyse opak koyu yüzey (okunabilirlik); değilse eski renk. */
@Composable
fun popupColor(): Color =
    if (LocalGlass.current) Color(0xF2161334) else MaterialTheme.colorScheme.surfaceContainerHigh

/** Cam modunda alt sayfa (bottom sheet) zemini. */
@Composable
fun sheetColor(): Color =
    if (LocalGlass.current) Color(0xF20E0C26) else MaterialTheme.colorScheme.background

/** DropdownMenu gibi Material bileşenlerinin cam modunda şeffaf kalmaması için. */
@Composable
fun PopupTheme(content: @Composable () -> Unit) {
    if (LocalGlass.current) {
        val cs = MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme = cs.copy(surfaceContainer = popupColor()),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    } else {
        content()
    }
}

/**
 * Alt sayfa (bottom sheet): ayrı bir pencerede açıldığı ve zemini zaten neredeyse opak olduğu için
 * içindeki kartlarda arka plan bulanıklığı kapatılır (yoksa kartlar fotoğraf gibi görünürdü).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = sheetColor(),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides null, LocalGlassSource provides null) { content() }
    }
}

private val rimBrush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.26f))
)
private val sheenBrush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f), Color.Transparent)
)

/**
 * Tüm arka planın (renk / resim / GIF / video) bir kez bulanıklaştırılmış kopyası.
 * Her cam yüzey bunun kendi altına denk gelen parçasını çizer; böylece her kartın arkası buzlu cam olur
 * ve yazılar arka plan resmine karışmaz. Android 12 altında ya da cam modu kapalıyken null.
 */
val LocalGlassBackdrop = compositionLocalOf<GraphicsLayer?> { null }

/** Cam yüzeylerin altına konan koyu perde: okunabilirlik için (bulanık arka plan parlaksa bile yazı net kalır). */
private const val GLASS_SCRIM = 0.26f
private const val GLASS_SCRIM_TINTED = 0.10f

/** Bundan küçük öğeler (düğme, çip, anahtar) zaten bir kartın içindedir; onlara ayrıca bulanık arka plan çizilmez. */
private val MIN_BLUR_SIZE = 44.dp

/** Bulanık arka planın bu öğenin altına denk gelen kısmını çizer. Ekran koordinatına hizalıdır, kaydırınca kaymaz. */
private fun Modifier.blurredBackdrop(): Modifier = composed {
    val layer = LocalGlassBackdrop.current
    if (!LocalGlass.current || layer == null) {
        Modifier
    } else {
        var pos by remember { mutableStateOf(Offset.Zero) }
        Modifier
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                if (p != pos) pos = p
            }
            .drawBehind {
                if (size.minDimension >= MIN_BLUR_SIZE.toPx()) {
                    translate(-pos.x, -pos.y) { drawLayer(layer) }
                }
            }
    }
}

/** Büyük cam öğeler için koyu perde (küçük öğelerde çizilmez). */
private fun Modifier.glassScrim(alpha: Float): Modifier = drawBehind {
    if (size.minDimension >= MIN_BLUR_SIZE.toPx()) drawRect(Color.Black.copy(alpha = alpha))
}

/**
 * Düz modda: klip + düz renk (eskisiyle aynı).
 * Cam modunda: bulanık arka plan + koyu perde + yarı saydam renk + ışık yansıması + parlak kenar.
 * [blurContent] true ise (alt çubuk) altından kayan kartlar da bulanıklaştırılır.
 */
fun Modifier.glassSurface(shape: Shape, color: Color, blurContent: Boolean = false): Modifier = composed {
    if (!LocalGlass.current) {
        Modifier.clip(shape).background(color)
    } else {
        Modifier.clip(shape)
            .blurredBackdrop()
            .then(if (blurContent) Modifier.glassBackdropBlur(shape) else Modifier)
            .glassScrim(GLASS_SCRIM)
            .background(color)
            .background(sheenBrush, shape)
            .border(1.dp, rimBrush, shape)
    }
}

/** Renkli cam: [tint] tonunda, yarı saydam gradyan dolgu. Düz modda [flat] rengi (varsayılan [tint]). */
fun Modifier.glassTinted(shape: Shape, tint: Color, flat: Color = tint): Modifier = composed {
    if (!LocalGlass.current) {
        Modifier.clip(shape).background(flat)
    } else {
        Modifier.clip(shape)
            .blurredBackdrop()
            .glassScrim(GLASS_SCRIM_TINTED)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.82f), tint.copy(alpha = 0.40f))), shape)
            .background(sheenBrush, shape)
            .border(1.dp, rimBrush, shape)
    }
}

/** Alt sayfalar (Ayarlar, İstatistik…): cam modunda şeffaf (arka planı en üstteki tek katman verir), değilse düz Surface. */
@Composable
fun PageSurface(modifier: Modifier = Modifier, color: Color, content: @Composable () -> Unit) {
    Surface(modifier, color = if (LocalGlass.current) Color.Transparent else color, content = content)
}

/**
 * Ana ekrandaki kaydırılan içeriğin (arka plan + liste) kaydedildiği katman.
 * Alt çubuk gibi cam öğeler bunun bulanık bir kopyasını kendi arkasında gösterir (gerçek buzlu cam).
 */
val LocalGlassSource = compositionLocalOf<GraphicsLayer?> { null }

/**
 * Gerçek arka plan bulanıklığı: öğenin altında kalan içerik (kaydırılan kartlar vb.) bulanıklaştırılıp
 * öğenin şekline kırpılarak çizilir. Android 12 altında ya da kaynak yoksa hiçbir şey yapmaz
 * (yarı saydam dolgu yine de görünür). Bulanıklık sadece öğenin kendi alanında hesaplanır.
 */
fun Modifier.glassBackdropBlur(shape: Shape, radius: Float = 36f): Modifier = composed {
    val src = LocalGlassSource.current
    if (!LocalGlass.current || src == null || Build.VERSION.SDK_INT < 31) {
        Modifier
    } else {
        val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
        var pos by remember { mutableStateOf(Offset.Zero) }
        Modifier
            .onGloballyPositioned { pos = it.positionInRoot() }
            .drawBehind {
                val pad = 64f
                layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                val sz = IntSize((size.width + 2 * pad).toInt(), (size.height + 2 * pad).toInt())
                layer.record(this, layoutDirection, sz) {
                    translate(pad - pos.x, pad - pos.y) { drawLayer(src) }
                }
                val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
                clipPath(path) {
                    translate(-pad, -pad) { drawLayer(layer) }
                }
            }
    }
}

/**
 * Cam modunun arka planı. Özel arka plan yoksa koyu zemin üstünde renkli ışık lekeleri (bir kez çizilip önbelleğe alınır);
 * varsa seçilen resim / GIF / video ekranı kaplayacak şekilde gösterilir, üstüne okunurluk için hafif karartma gelir.
 */
@Composable
fun GlassBackdrop(
    path: String? = null, kind: String = "image", modifier: Modifier = Modifier,
    blurLayer: GraphicsLayer? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    val file = remember(path) { path?.let { java.io.File(it) }?.takeIf { it.exists() } }
    val raw = androidx.compose.ui.graphics.rememberGraphicsLayer()
    Box(
        modifier.fillMaxSize().then(
            if (blurLayer == null) Modifier else Modifier.drawWithContent {
                // Arka plan bir katmana kaydedilir; ekrana normal çizilir, ayrıca bulanık kopyası kartlar için hazırlanır
                val sz = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                raw.record(this, layoutDirection, sz) { this@drawWithContent.drawContent() }
                blurLayer.renderEffect =
                    if (Build.VERSION.SDK_INT >= 31) {
                        val r = 22.dp.toPx()
                        BlurEffect(r, r, TileMode.Clamp)
                    } else null
                blurLayer.record(this, layoutDirection, sz) { drawLayer(raw) }
                drawLayer(raw)
            }
        )
    ) {
        if (file == null) {
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val w = (size.width / 4f).toInt().coerceAtLeast(2)
                    val h = (size.height / 4f).toInt().coerceAtLeast(2)
                    val bmp = ImageBitmap(w, h)
                    CanvasDrawScope().draw(this, layoutDirection, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
                        paintGlassBlobs(accent)
                    }
                    val dst = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                    onDrawBehind { drawImage(bmp, dstSize = dst, filterQuality = FilterQuality.Medium) }
                }
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0A1E)))
            when (kind) {
                "video" -> VideoBackdrop(file)
                "gif" -> GifBackdrop(file)
                else -> ImageBackdrop(file)
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
        }
    }
}

@Composable
private fun ImageBackdrop(file: java.io.File) {
    val bmp = remember(file.path) {
        runCatching { android.graphics.BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
    }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bmp, null, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

@Composable
private fun GifBackdrop(file: java.io.File) {
    val drawable = remember(file.path) {
        runCatching<android.graphics.drawable.Drawable?> {
            if (Build.VERSION.SDK_INT >= 28) {
                android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(file))
            } else {
                android.graphics.drawable.BitmapDrawable(null, android.graphics.BitmapFactory.decodeFile(file.path))
            }
        }.getOrNull()
    }
    if (drawable != null) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { c ->
                android.widget.ImageView(c).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(drawable)
                    if (Build.VERSION.SDK_INT >= 28 && drawable is android.graphics.drawable.AnimatedImageDrawable) {
                        drawable.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                        drawable.start()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Sessiz, döngüde oynayan video; TextureView üzerinde ekranı kaplayacak (center-crop) şekilde. */
@Composable
private fun VideoBackdrop(file: java.io.File) {
    val player = remember(file.path) { LoopingVideo(file.path) }
    androidx.compose.runtime.DisposableEffect(player) { onDispose { player.release() } }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c -> android.view.TextureView(c).apply { surfaceTextureListener = player.listener(this) } },
        modifier = Modifier.fillMaxSize()
    )
}

private class LoopingVideo(private val path: String) {
    private var mp: android.media.MediaPlayer? = null

    fun listener(tv: android.view.TextureView) = object : android.view.TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
            release()
            val m = android.media.MediaPlayer()
            runCatching {
                m.setDataSource(path)
                m.setSurface(android.view.Surface(st))
                m.isLooping = true
                m.setVolume(0f, 0f)
                m.setOnVideoSizeChangedListener { _, vw, vh -> crop(tv, vw, vh) }
                m.setOnPreparedListener { it.start() }
                m.prepareAsync()
                mp = m
            }.onFailure { runCatching { m.release() } }
        }

        override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
            mp?.let { crop(tv, it.videoWidth, it.videoHeight) }
        }

        override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
            release()
            return true
        }

        override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
    }

    private fun crop(tv: android.view.TextureView, vw: Int, vh: Int) {
        val w = tv.width.toFloat()
        val h = tv.height.toFloat()
        if (vw <= 0 || vh <= 0 || w <= 0f || h <= 0f) return
        val scale = maxOf(w / vw, h / vh)
        val m = android.graphics.Matrix()
        m.setScale(vw * scale / w, vh * scale / h, w / 2f, h / 2f)
        tv.setTransform(m)
    }

    fun release() {
        val m = mp ?: return
        mp = null
        runCatching { m.stop() }
        runCatching { m.release() }
    }
}

private fun DrawScope.paintGlassBlobs(accent: Color) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0D0B26), Color(0xFF0A0818), Color(0xFF130A14))))
    fun blob(c: Color, cx: Float, cy: Float, r: Float) {
        drawRect(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(w * cx, h * cy), radius = r))
    }
    val r = maxOf(w, h)
    blob(Color(0xFF6A3DD8).copy(alpha = 0.55f), 0.50f, 0.30f, r * 0.55f)   // mor (orta-üst)
    blob(Color(0xFF2B4BFF).copy(alpha = 0.40f), 0.05f, 0.12f, r * 0.45f)   // mavi (sol-üst)
    blob(Color(0xFF7A1D2E).copy(alpha = 0.55f), 1.00f, 0.02f, r * 0.40f)   // bordo (sağ-üst)
    blob(Color(0xFFC03A9A).copy(alpha = 0.30f), 0.85f, 0.58f, r * 0.40f)   // pembe (sağ-orta)
    blob(accent.copy(alpha = 0.60f), 0.12f, 0.97f, r * 0.45f)              // vurgu rengi (sol-alt)
    blob(Color(0xFF14A8FF).copy(alpha = 0.55f), 0.92f, 0.95f, r * 0.42f)   // camgöbeği (sağ-alt)
}
