package com.gamelauncher.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ====================================================================== Görünüm (ana sayfadaki palet)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(s: AppState, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, containerColor = sheetColor(), contentColor = cs.onSurface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text("Görünüm", style = MaterialTheme.typography.headlineSmall)

            FlatCard(Modifier.padding(top = 16.dp), pad = 16.dp) {
                ToggleRow(
                    "Liquid Glass",
                    "Buzlu cam (yarı saydam) görünüm. Açıkken koyu tema kullanılır.",
                    s.glass, { s.changeGlass(it) }
                )
            }

            if (!s.glass) {
                SectionLabel("Tema")
                SegmentedChoice(listOf("Sistem", "Açık", "Koyu", "AMOLED"), s.themeMode) { s.setTheme(it) }
            }

            SectionLabel("Yazı boyutu")
            SegmentedChoice(listOf("Küçük", "Normal", "Büyük"), s.fontStep) { s.changeFontStep(it) }

            if (android.os.Build.VERSION.SDK_INT >= 31 && !s.glass) {
                FlatCard(Modifier.padding(top = 16.dp), pad = 16.dp) {
                    ToggleRow(
                        "Telefonun renklerini kullan",
                        "Uygulama renklerini telefonunun duvar kağıdına göre otomatik ayarlar (Material You). Açıkken vurgu rengi seçilemez.",
                        s.dynamic, { s.setDynamic(it) }
                    )
                }
            }

            if (s.glass || !s.dynamic || android.os.Build.VERSION.SDK_INT < 31) {
                SectionLabel("Vurgu rengi")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    accents.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(c).clickable { s.setAccent(i) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (s.accent == i) {
                                Icon(
                                    Icons.Default.Check, null, Modifier.size(20.dp),
                                    tint = if (c.luminance() > 0.45f) Color(0xFF111111) else Color.White
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel("Uygulama simgesi")
            SegmentedChoice(listOf("Beyaz", "Siyah"), if (s.iconDark) 1 else 0) { s.setIconDark(it == 1) }
            val activeDark = s.iconDark
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(false to R.drawable.logo_white, true to R.drawable.logo_black).forEach { (isDark, res) ->
                    androidx.compose.foundation.Image(
                        painterResource(res), if (isDark) "Siyah simge" else "Beyaz simge",
                        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                            .then(
                                if (activeDark == isDark) Modifier.border(BorderStroke(3.dp, cs.primary), RoundedCornerShape(16.dp))
                                else Modifier
                            )
                    )
                }
            }
            val iconCtx = LocalContext.current
            var iconStatus by remember { mutableStateOf(IconSwitcher.state(iconCtx)) }
            Text(
                "Şu an: $iconStatus\n(ONE UI sürümlerde çalışmayabilir)",
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            SoftButton(
                "Simgeyi şimdi uygula",
                {
                    val err = IconSwitcher.sync(iconCtx)
                    iconStatus = IconSwitcher.state(iconCtx) + (err?.let { " — HATA: $it" } ?: "")
                },
                Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            Text(
                "Simge, uygulamadan çıkınca (ana ekrana dönünce) güncellenir; Android ekrandayken değiştirirse uygulamayı kapatır. Bazı ana ekranlarda değişmesi birkaç saniye sürebilir.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

// ====================================================================== Ayarlar (liste)

private enum class Sub(val title: String) {
    EDGE("Yan Panel"), UPDATES("Güncellemeler"), PERMS("İzinler"),
    HELP("Yardım"), CONTACT("İletişim"), ABOUT("Hakkında")
}

@Composable
fun SettingsPage(s: AppState, onClose: () -> Unit) {
    var sub by remember { mutableStateOf<Sub?>(null) }
    BackHandler { if (sub != null) sub = null else onClose() }

    PageSurface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val cur = sub) {
            null -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                ScreenTopBar("Ayarlar", onClose)
                Column(
                    Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                        .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FlatCard(pad = 16.dp) {
                        ToggleRow(
                            "Liquid Glass",
                            "Buzlu cam görünümü: kartlar, düğmeler ve alt çubuk yarı saydam olur, arkada renkli ışıklar görünür. Koyu tema kullanır.",
                            s.glass, { s.changeGlass(it) }
                        )
                    }
                    SettingsRow(Icons.Default.VerticalSplit, "Yan Panel", "Kenar paneli, FPS / sıcaklık göstergesi, uygulamalar") { sub = Sub.EDGE }
                    SettingsRow(Icons.Default.SystemUpdate, "Güncellemeler", "Sürüm ${Updater.current(LocalContext.current).name}") { sub = Sub.UPDATES }
                    SettingsRow(Icons.Default.Lock, "İzinler", if (s.hasUsage) "Kullanım erişimi verildi" else "Kullanım erişimi gerekli") { sub = Sub.PERMS }
                    SettingsRow(Icons.AutoMirrored.Filled.HelpOutline, "Yardım", "Sık sorulan sorular") { sub = Sub.HELP }
                    SettingsRow(Icons.Default.Mail, "İletişim", null) { sub = Sub.CONTACT }
                    SettingsRow(Icons.Default.Info, "Hakkında", null) { sub = Sub.ABOUT }
                }
            }
            else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                ScreenTopBar(cur.title) { sub = null }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp).navigationBarsPadding()
                ) {
                    when (cur) {
                        Sub.EDGE -> EdgePage(s)
                        Sub.UPDATES -> UpdatesPage()
                        Sub.PERMS -> PermissionsPage(s)
                        Sub.HELP -> HelpPage()
                        Sub.CONTACT -> ContactPage()
                        Sub.ABOUT -> AboutPage()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().flatClick(onClick = onClick)
            .glassSurface(RoundedCornerShape(22.dp), cs.surfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).glassSurface(RoundedCornerShape(14.dp), cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = cs.onPrimaryContainer) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
    }
}

// ====================================================================== Yan Panel

@Composable
private fun EdgePage(s: AppState) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlatCard {
            ToggleRow(
                "Kenar panelini aç",
                "Oyundayken ekranın kenarında küçük bir çizgi çıkar. Dokununca ya da içeri kaydırınca seçtiğin uygulamaların kısayolları açılır; " +
                    "kısayola dokunmak uygulamayı açılır pencerede (cihaz destekliyorsa) başlatır, uzun basmak normal açar. " +
                    "\"Diğer uygulamaların üzerinde göster\" ve kullanım erişimi izni gerekir; izni verip geri dönünce anahtarı tekrar aç.",
                s.edge,
                { on ->
                    if (!on) {
                        s.changeEdge(false)
                    } else if (!Settings.canDrawOverlays(ctx)) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                    } else {
                        s.changeEdge(true)
                    }
                }
            )
        }
        FlatCard { EdgePanelSettings() }
        HudSection(s)
        ReportSection(s)
    }
}

// ====================================================================== Oyun içi bilgi (Yan Panel sayfasının içinde)

@Composable
private fun HudSection(s: AppState) {
    val ctx = LocalContext.current
    var fps by remember { mutableStateOf(EdgePrefs.hudFps(ctx)) }
    var bat by remember { mutableStateOf(EdgePrefs.hudBat(ctx)) }
    var cpu by remember { mutableStateOf(EdgePrefs.hudCpu(ctx)) }
    var pos by remember { mutableIntStateOf(EdgePrefs.hudPos(ctx)) }

    // Açmak için "Diğer uygulamaların üzerinde göster" izni gerekir; yoksa izin ekranına gidilir
    fun toggle(on: Boolean, set: (Boolean) -> Unit, save: (Boolean) -> Unit) {
        if (on && !Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            return
        }
        set(on); save(on); s.applyEdge()
    }

    FlatCard {
        Text("Oyun içi bilgi", style = MaterialTheme.typography.titleMedium)
        Text(
            "Oyundayken ekranda yazı olarak gösterilir; arka planı yoktur ve dokunmayı engellemez. Her biri ayrı açılıp kapanır. Yan paneli açınca da buradan aç/kapatabilirsin.",
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ToggleRow(
            "FPS", "Ekranda \"FPS: 60\" gibi görünür", fps,
            { toggle(it, { v -> fps = v }) { v -> EdgePrefs.setHudFps(ctx, v) } },
            Modifier.padding(top = 16.dp)
        )
        ToggleRow(
            "Pil sıcaklığı", "Fps satırının altına eklenir", bat,
            { toggle(it, { v -> bat = v }) { v -> EdgePrefs.setHudBat(ctx, v) } },
            Modifier.padding(top = 16.dp)
        )
        ToggleRow(
            "CPU sıcaklığı", "Pil sıcaklığının altına eklenir. Bazı telefonlarda Android okumaya izin vermez, \"--\" görünür.", cpu,
            { toggle(it, { v -> cpu = v }) { v -> EdgePrefs.setHudCpu(ctx, v) } },
            Modifier.padding(top = 16.dp)
        )
        Text("Konum", Modifier.padding(top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
        SegmentedChoice(listOf("Üst sol", "Üst orta", "Üst sağ"), pos) { pos = it; EdgePrefs.setHudPos(ctx, it) }
    }
}

@Composable
private fun ReportSection(s: AppState) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(EdgePrefs.reportOn(ctx)) }
    FlatCard {
        ToggleRow(
            "Oyun raporu",
            "Oyun bitince ekranda kısa bir özet çıkar: süre, FPS, pil düşüşü, en yüksek ısı ve önceki oturumla karşılaştırma. " +
                "Raporlar oyunun istatistik sayfasında saklanır. FPS'in ölçülebilmesi için root gerekir; ölçülemezse FPS yazılmaz.",
            on,
            { v ->
                if (v && !Settings.canDrawOverlays(ctx)) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                } else {
                    on = v; EdgePrefs.setReportOn(ctx, v); s.applyEdge()
                }
            }
        )
    }
}

// ====================================================================== İzinler

@Composable
private fun PermissionCard(
    title: String, desc: String, ok: Boolean?, okText: String, noText: String,
    button: String, onClick: () -> Unit
) {
    FlatCard(Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (ok != null) {
                Spacer(Modifier.width(8.dp))
                StatusChip(ok, okText, noText)
            }
        }
        Text(
            desc, Modifier.padding(top = 8.dp, bottom = 14.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SoftButton(button, onClick, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PermissionsPage(s: AppState) {
    val ctx = LocalContext.current
    Text(
        "Süre takibi, yan panel ve oyun içi bilgi için gereken izinler burada.",
        Modifier.padding(start = 4.dp, bottom = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    PermissionCard(
        "Kullanım erişimi",
        "Oynama sürelerini ölçmek için gerekir. Yan panel ve oyun içi bilgi de oyunun açık olduğunu bu izinle anlar.",
        s.hasUsage, "Verildi", "Verilmedi",
        "Kullanım erişimi ayarlarını aç"
    ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

    PermissionCard(
        "Diğer uygulamaların üzerinde göster",
        "Yan panel çizgisi ve oyun içi FPS / sıcaklık yazıları için gerekir.",
        Settings.canDrawOverlays(ctx), "Verildi", "Verilmedi",
        "Üstte gösterme ayarlarını aç"
    ) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }

    PermissionCard(
        "Üstteki bildirim",
        "Android, arka planda çalışan yan panel ve oyun içi bilgi için bir bildirim gösterilmesini şart koşar. " +
            "Bildirimi kapatırsan özellikler yine çalışır. Açılan ekranda \"Arka plan hizmeti\"ni kapat.",
        null, "", "",
        "Bildirim ayarlarını aç"
    ) {
        val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, "edge_quiet")
        runCatching { ctx.startActivity(i) }.onFailure {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        }
    }
}

// ====================================================================== Güncellemeler

private sealed interface UpState {
    object Idle : UpState
    object Checking : UpState
    object UpToDate : UpState
    class Available(val r: Updater.Release) : UpState
    class Downloading(val r: Updater.Release, val done: Long, val total: Long) : UpState {
        val p: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    class Ready(val r: Updater.Release, val file: File) : UpState
    class NeedsPermission(val r: Updater.Release, val file: File) : UpState
    class Failed(val msg: String) : UpState
}

/** Daire çizilir, ardından tik işareti çizilerek belirir. */
@Composable
private fun AnimatedCheck(size: androidx.compose.ui.unit.Dp, color: Color) {
    val circle = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        circle.animateTo(1f, tween(450))
        tick.animateTo(1f, tween(350))
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.07f
        val pad = sw
        drawArc(
            color = color, startAngle = -90f, sweepAngle = 360f * circle.value, useCenter = false,
            topLeft = Offset(pad, pad), size = androidx.compose.ui.geometry.Size(w - 2 * pad, h - 2 * pad),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        val path = Path().apply {
            moveTo(w * 0.29f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.67f)
            lineTo(w * 0.72f, h * 0.36f)
        }
        val seg = Path()
        val pm = PathMeasure().apply { setPath(path, false) }
        pm.getSegment(0f, pm.length * tick.value, seg, true)
        drawPath(seg, color, style = Stroke(sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun UpdatesPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val cur = remember { Updater.current(ctx) }
    var st by remember { mutableStateOf<UpState>(UpState.Idle) }

    fun download(r: Updater.Release) {
        val url = r.apkUrl ?: run { st = UpState.Failed("Bu sürümde indirilebilir APK bulunamadı."); return }
        scope.launch {
            st = UpState.Downloading(r, 0L, 0L)
            val res = withContext(Dispatchers.IO) {
                runCatching { Updater.download(ctx, url) { d, t -> st = UpState.Downloading(r, d, t) } }
            }
            res.fold(
                onSuccess = { f -> st = if (Updater.install(ctx, f)) UpState.Ready(r, f) else UpState.NeedsPermission(r, f) },
                onFailure = { st = UpState.Failed("İndirme başarısız oldu. Bağlantını kontrol edip tekrar dene.") }
            )
        }
    }

    fun check() {
        st = UpState.Checking
        scope.launch {
            val t0 = System.currentTimeMillis()
            val res = withContext(Dispatchers.IO) { runCatching { Updater.fetchLatest() } }
            val wait = 1000 - (System.currentTimeMillis() - t0)   // dönen animasyon en az 1 sn görünsün
            if (wait > 0) delay(wait)
            st = res.fold(
                onSuccess = { if (it.build > cur.code) UpState.Available(it) else UpState.UpToDate },
                onFailure = { UpState.Failed(Updater.explain(it)) }
            )
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            when (val x = st) {
                is UpState.Checking -> CircularProgressIndicator(modifier = Modifier.size(88.dp), strokeWidth = 6.dp)
                is UpState.Downloading -> Icon(Icons.Default.SystemUpdate, null, Modifier.size(64.dp), tint = cs.primary)
                is UpState.UpToDate -> AnimatedCheck(104.dp, cs.primary)
                is UpState.Failed -> Icon(Icons.Default.CloudOff, null, Modifier.size(64.dp), tint = cs.error)
                else -> Icon(Icons.Default.SystemUpdate, null, Modifier.size(64.dp), tint = cs.primary)
            }
        }

        val title = when (val x = st) {
            is UpState.Idle -> "Sürüm ${cur.name}"
            is UpState.Checking -> "Güncellemeler kontrol ediliyor…"
            is UpState.UpToDate -> "Uygulamanız güncel"
            is UpState.Available -> "Yeni sürüm var"
            is UpState.Downloading -> "Güncelleme indiriliyor…"
            is UpState.Ready -> "İndirme tamamlandı"
            is UpState.NeedsPermission -> "Kurulum izni gerekli"
            is UpState.Failed -> "Kontrol edilemedi"
        }
        Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        val sub = when (val x = st) {
            is UpState.Idle -> "Güncellemeleri kontrol etmek için aşağıdaki düğmeye dokun."
            is UpState.UpToDate -> "Kurulu sürüm: ${cur.name}"
            is UpState.Available -> "Kurulu sürüm: ${cur.name}"
            is UpState.Ready -> "Kurulum ekranında \"Yükle\"ye dokun. Kurulum bitince uygulama yeniden açılır."
            is UpState.NeedsPermission -> "Açılan ekranda bu uygulamaya \"bu kaynaktan yüklemeye izin ver\" de, geri dönüp tekrar dokun."
            is UpState.Failed -> x.msg
            else -> null
        }
        if (sub != null) {
            Text(
                sub, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }

        val d = st as? UpState.Downloading
        if (d != null) {
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { d.p },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (d.total > 0) "%${(d.p * 100).toInt()}" else "…",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Text(
                    if (d.total > 0) "%.1f / %.1f MB".format(d.done / 1048576f, d.total / 1048576f)
                    else "%.1f MB".format(d.done / 1048576f),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        when (val x = st) {
            is UpState.Idle, is UpState.UpToDate, is UpState.Failed ->
                PlayButton({ check() }, Modifier.fillMaxWidth(), label = "Güncellemeyi kontrol et")
            is UpState.Available ->
                PlayButton({ download(x.r) }, Modifier.fillMaxWidth(), label = "İndir ve güncelle")
            is UpState.Ready ->
                PlayButton({ Updater.install(ctx, x.file) }, Modifier.fillMaxWidth(), label = "Şimdi kur")
            is UpState.NeedsPermission ->
                PlayButton({ if (Updater.install(ctx, x.file)) st = UpState.Ready(x.r, x.file) }, Modifier.fillMaxWidth(), label = "Tekrar dene")
            else -> {}
        }

        val a = st as? UpState.Available
        if (a != null && a.r.notes.isNotBlank()) {
            Column(
                Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceContainer).padding(16.dp)
            ) {
                Text("Yenilikler", style = MaterialTheme.typography.titleSmall)
                Text(a.r.notes.trim().take(600), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "Yeni sürüm varsa \"İndir ve güncelle\"ye dokun; APK indirilir ve Android'in kurma ekranı açılır. " +
                "\"Yükle\"ye dokunman yeterli. Ayarların, oyun listen ve widget'ların korunur. " +
                "Android güvenlik gereği kurulumu her zaman senin onayınla yapar.",
            Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}

// ====================================================================== Yardım (SSS)

private val faq = listOf(
    "Oynama süreleri neden görünmüyor?" to
        "Süreleri ölçmek için \"Kullanım erişimi\" izni gerekir. Ayarlar > İzinler'den izni verip uygulamaya dön; süreler bundan sonra kaydedilir.",
    "Widget'ları nasıl eklerim?" to
        "Ana ekranda boş bir yere basılı tut, Widget'lar'ı aç ve Game Launcher'ı seç. Üç widget var: Oyunlar ve süreler, Sıcaklıklar, Telefon özeti.",
    "Widget'ın rengini nasıl değiştiririm?" to
        "Ana ekranda widget'a basılı tut ve çıkan Ayarlar düğmesine dokun. Her widget'ın rengi ve saydamlığı ayrı ayarlanır. Düğme çıkmazsa widget'ı silip yeniden ekle.",
    "Yan panel nasıl çalışır?" to
        "Ayarlar > Yan Panel'den aç. \"Diğer uygulamaların üzerinde göster\" ve kullanım erişimi izinleri gerekir. Oyundayken ekranın kenarında küçük bir çizgi çıkar; dokununca ya da içeri kaydırınca panel açılır.",
    "Yan panelde hangi uygulamalar görünür?" to
        "Yalnızca Ayarlar > Yan Panel > Uygulama seç bölümünde işaretlediklerin. Oyunlar dahil istediğin uygulamayı ekleyebilirsin.",
    "Yan paneldeki çizgiyi nasıl taşırım?" to
        "Çizgiye basılı tut, çizgi mor olunca parmağını sürükle. Yukarı aşağı ya da öbür kenara taşıyabilirsin. Uzunluğu ve yüksekliği Ayarlar > Yan Panel'den de ayarlanır.",
    "Panelden açtığım pencere neden küçük açılmıyor?" to
        "Açılır pencere için cihazın serbest pencere desteği olması gerekir. Desteklemeyen cihazlarda uygulama tam ekran açılır. Kısayola uzun basarsan uygulama normal açılır.",
    "Tema ve duvar kağıdını nerede değiştiririm?" to
        "Ana sayfada üstteki telefon simgesinin solundaki kalem simgesine dokun. Tema, vurgu rengi, duvar kağıdı ve bulanıklık orada.",
    "Telefon ve Ağ Testi nerede?" to
        "Ana sayfada alt çubuğun en sağındaki \"Telefon ve Ağ Testi\" sekmesine dokun. İçinde iki büyük düğme var: Test Kiti ile Ağ Kontrolü ve Hız Testi.",
    "İnternet hız testi nasıl çalışır?" to
        "Ağ Kontrolü ve Hız Testi > Hız sekmesinde başlat. Önce ping ve dalgalanmayı, sonra 8'er saniye indirme ve yükleme hızını (Mbps) ölçer. Test o an kullandığın ağ üzerinden yapılır ve yüzlerce MB veri harcayabilir; mobil veride dikkat et.",
    "Ping ve dalgalanma ne demek?" to
        "Ping, verinin gidip gelme süresidir (ms); oyunda ne kadar düşükse o kadar iyi. Dalgalanma, bu sürenin ne kadar oynadığını gösterir; yüksekse ping sıçrar ve oyun takılır. Cevapsız, hiç dönmeyen paket sayısıdır.",
    "Wi-Fi mi 4G/5G mi oynamak için daha iyi?" to
        "Ağ Kontrolü'nde Wi-Fi/4G sekmesi ikisini aynı DNS sunucusuna aynı anda 10'ar sorguyla ölçüp karşılaştırır ve hangisinin daha iyi olduğunu yazar. Uygulama bağlantını değiştirmez, yalnızca ölçer. Mobil veri kapalıysa ya da Wi-Fi'a bağlı değilsen o taraf ölçülemez.",
    "Bölge ölçümü neyi gösterir?" to
        "Bölge sekmesi büyük bulut sağlayıcısının Frankfurt, Tokyo, ABD gibi bölgelerine bağlantı süresini ölçer ve en yakını gösterir. Bunlar oyunun kendi sunucuları değildir; oyunda sunucu seçeneği varsa en düşük süreli bölgeye yakın olanı seçmek için yol göstericidir.",
    "Test Kiti ne işe yarar?" to
        "Telefonun donanımını test eder. Dokunma sekmesi ekranın her yerinin dokunmayı algılayıp algılamadığını, dokunma hızını (Hz) ve aynı anda kaç parmağı okuduğunu gösterir. Ekran sekmesi ölü piksel ve leke için ekranı tek renge boyar. Kumanda sekmesi bağlı gamepad'in tuşlarını ve çubuklarını canlı gösterir. Sağ üstten ses ve titreşimi kapatabilirsin.",
    "Kumanda sekmesi kumandamı görmüyor" to
        "Kumandayı Bluetooth ya da USB ile telefona bağla, sonra Kumanda sekmesi açıkken herhangi bir tuşuna bas. Kumanda telefonda tanınmıyorsa önce telefonun Bluetooth ayarlarından eşleştir.",
    "Oyun boyutunu nerede görürüm?" to
        "Oyunun istatistik sayfasında \"Toplam süre\" altında, oyuna dokununca açılan pencerede de ayrıntılı kart olarak. Veri ve önbellek dahil gerçek boyut için \"Kullanım erişimi\" izni gerekir; izin yoksa yalnızca APK boyutu gösterilir.",
    "Uygulamayı nasıl güncellerim?" to
        "Ayarlar > Güncellemeler'e gir ve \"Güncellemeyi kontrol et\"e dokun. Yeni sürüm varsa indirip kurabilirsin."
)

@Composable
private fun HelpPage() {
    val cs = MaterialTheme.colorScheme
    Text(
        "Sık sorulan sorular",
        Modifier.padding(top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = cs.onSurfaceVariant
    )
    faq.forEach { (q, a) ->
        var open by remember { mutableStateOf(false) }
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp))
                .background(cs.surfaceContainer)
                .clickable { open = !open }
                .animateContentSize()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = cs.onSurfaceVariant)
            }
            if (open) {
                Text(a, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

// ====================================================================== Hakkında

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val cur = remember { Updater.current(context) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Game Launcher", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Sürüm ${cur.name}", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Text("2026", Modifier.padding(top = 32.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
