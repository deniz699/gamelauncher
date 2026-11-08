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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ====================================================================== Görünüm (ana sayfadaki kalem)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(s: AppState, onClose: () -> Unit) {
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) s.setBackground(uri)
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text("Görünüm", style = MaterialTheme.typography.titleLarge)

            Text("Tema", Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
            SegmentedChoice(listOf("Sistem", "Açık", "Koyu", "AMOLED"), s.themeMode) { s.setTheme(it) }

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Telefonun renklerini kullan")
                        Text(
                            "Uygulama renklerini telefonunun duvar kağıdına göre otomatik ayarlar (Material You). Açıkken vurgu rengi seçilemez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FlatSwitch(checked = s.dynamic, onCheckedChange = { s.setDynamic(it) })
                }
            }

            if (!s.dynamic || android.os.Build.VERSION.SDK_INT < 31) {
                Text("Vurgu rengi", Modifier.padding(top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    accents.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .then(
                                    if (s.accent == i) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                                    else Modifier
                                )
                                .clickable { s.setAccent(i) }
                        )
                    }
                }
            }

            Text("Arka plan", Modifier.padding(top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Duvar kağıdı seç", { bgPicker.launch("image/*") }, icon = Icons.Default.Image)
                if (s.bgPath != null) {
                    SoftButton("Varsayılana dön", { s.clearBackground() })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Arka planı bulanıklaştır", Modifier.weight(1f))
                FlatSwitch(checked = s.bgBlur, onCheckedChange = { s.changeBgBlur(it) })
            }
        }
    }
}

// ====================================================================== Ayarlar (liste)

private enum class Sub(val title: String) {
    EDGE("Yan Panel"), USAGE("Süre takibi"), UPDATES("Güncellemeler"),
    HELP("Yardım"), CONTACT("İletişim"), ABOUT("Hakkında")
}

@Composable
fun SettingsPage(s: AppState, onClose: () -> Unit) {
    var sub by remember { mutableStateOf<Sub?>(null) }
    BackHandler { if (sub != null) sub = null else onClose() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val cur = sub) {
            null -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                TopRow("Ayarlar", onClose)
                Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    SettingsRow(Icons.Default.VerticalSplit, "Yan Panel", "Oyun içi kenar paneli, konum ve uygulamalar") { sub = Sub.EDGE }
                    SettingsRow(Icons.Default.Timer, "Süre takibi", if (s.hasUsage) "Kullanım erişimi verildi" else "Kullanım erişimi gerekli") { sub = Sub.USAGE }
                    SettingsRow(Icons.Default.SystemUpdate, "Güncellemeler", "Sürüm ${Updater.current(LocalContext.current).name}") { sub = Sub.UPDATES }
                    SettingsRow(Icons.AutoMirrored.Filled.HelpOutline, "Yardım", "Sık sorulan sorular") { sub = Sub.HELP }
                    SettingsRow(Icons.Default.Mail, "İletişim", null) { sub = Sub.CONTACT }
                    SettingsRow(Icons.Default.Info, "Hakkında", null) { sub = Sub.ABOUT }
                }
            }
            else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                TopRow(cur.title) { sub = null }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp).navigationBarsPadding()
                ) {
                    when (cur) {
                        Sub.EDGE -> EdgePage(s)
                        Sub.USAGE -> UsagePage(s)
                        Sub.UPDATES -> UpdatesPage()
                        Sub.HELP -> HelpPage()
                        Sub.CONTACT -> {}   // şimdilik boş
                        Sub.ABOUT -> AboutPage()
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRow(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = cs.onSecondaryContainer) }
        Spacer(Modifier.width(16.dp))
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
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Kenar panelini aç")
            Text(
                "Oyundayken ekranın kenarında küçük bir çizgi çıkar. Dokununca ya da içeri kaydırınca seçtiğin uygulamaların kısayolları açılır; " +
                    "kısayola dokunmak uygulamayı açılır pencerede (cihaz destekliyorsa) başlatır, uzun basmak normal açar. " +
                    "\"Diğer uygulamaların üzerinde göster\" ve kullanım erişimi izni gerekir; izni verip geri dönünce anahtarı tekrar aç.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        FlatSwitch(checked = s.edge, onCheckedChange = { on ->
            if (!on) {
                s.changeEdge(false)
            } else if (!Settings.canDrawOverlays(ctx)) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            } else {
                if (android.os.Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                s.changeEdge(true)
            }
        })
    }
    EdgePanelSettings()
}

// ====================================================================== Süre takibi

@Composable
private fun UsagePage(s: AppState) {
    val ctx = LocalContext.current
    Text(
        "Oynama sürelerini ölçebilmek için \"Kullanım erişimi\" izni gerekir. Oyun içi kenar paneli de bu izni kullanır.",
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        if (s.hasUsage) "Kullanım erişimi verildi ✓" else "Kullanım erişimi verilmedi — süreler ölçülemez.",
        Modifier.padding(top = 12.dp)
    )
    SoftButton(
        "Kullanım erişimi ayarlarını aç",
        { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        Modifier.padding(top = 12.dp)
    )
}

// ====================================================================== Güncellemeler

private sealed interface UpState {
    object Idle : UpState
    object Checking : UpState
    object UpToDate : UpState
    class Available(val r: Updater.Release) : UpState
    class Downloading(val r: Updater.Release, val p: Float) : UpState
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

    fun download(r: Updater.Release) {
        val url = r.apkUrl ?: run { st = UpState.Failed("Bu sürümde indirilebilir APK bulunamadı."); return }
        scope.launch {
            st = UpState.Downloading(r, 0f)
            val res = withContext(Dispatchers.IO) {
                runCatching { Updater.download(ctx, url) { p -> st = UpState.Downloading(r, p) } }
            }
            res.fold(
                onSuccess = { f -> st = if (Updater.install(ctx, f)) UpState.Available(r) else UpState.NeedsPermission(r, f) },
                onFailure = { st = UpState.Failed("İndirme başarısız oldu. Bağlantını kontrol edip tekrar dene.") }
            )
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            when (val x = st) {
                is UpState.Checking, is UpState.Downloading ->
                    if (x is UpState.Downloading) {
                        CircularProgressIndicator(progress = { x.p }, modifier = Modifier.size(88.dp), strokeWidth = 6.dp)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(88.dp), strokeWidth = 6.dp)
                    }
                is UpState.UpToDate -> AnimatedCheck(104.dp, cs.primary)
                is UpState.Failed -> Icon(Icons.Default.CloudOff, null, Modifier.size(64.dp), tint = cs.error)
                else -> Icon(Icons.Default.SystemUpdate, null, Modifier.size(64.dp), tint = cs.primary)
            }
        }

        val title = when (val x = st) {
            is UpState.Idle -> "Sürüm ${cur.name}"
            is UpState.Checking -> "Güncellemeler kontrol ediliyor…"
            is UpState.UpToDate -> "Uygulaman güncel"
            is UpState.Available -> "Yeni sürüm var: 1.0.${x.r.build}"
            is UpState.Downloading -> "İndiriliyor… %${(x.p * 100).toInt()}"
            is UpState.NeedsPermission -> "Kurulum izni gerekli"
            is UpState.Failed -> "Kontrol edilemedi"
        }
        Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        val sub = when (val x = st) {
            is UpState.Idle -> "Güncellemeleri GitHub'daki son sürümden kontrol eder."
            is UpState.UpToDate -> "Kurulu sürüm: ${cur.name}"
            is UpState.Available -> "Kurulu sürüm: ${cur.name}"
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

        Spacer(Modifier.height(24.dp))
        when (val x = st) {
            is UpState.Idle, is UpState.UpToDate, is UpState.Failed ->
                PlayButton({ check() }, Modifier.fillMaxWidth(), label = "Güncellemeyi kontrol et")
            is UpState.Available ->
                PlayButton({ download(x.r) }, Modifier.fillMaxWidth(), label = "İndir ve güncelle")
            is UpState.NeedsPermission ->
                PlayButton({ if (Updater.install(ctx, x.file)) st = UpState.Available(x.r) }, Modifier.fillMaxWidth(), label = "Tekrar dene")
            else -> {}
        }

        val a = st as? UpState.Available
        if (a != null && a.r.notes.isNotBlank()) {
            Column(
                Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceVariant.copy(alpha = 0.5f)).padding(14.dp)
            ) {
                Text("Yenilikler", style = MaterialTheme.typography.titleSmall)
                Text(a.r.notes.trim().take(600), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "Nasıl güncellenir? Yeni sürüm varsa APK uygulamanın içinden indirilir ve Android'in kurma ekranı açılır; " +
                "\"Yükle\"ye dokunman yeterli. Ayarların, oyun listen ve widget'ların korunur. " +
                "Güncellemeler kendiliğinden kurulmaz; her zaman senin onayınla yapılır.",
            Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}

// ====================================================================== Yardım (SSS)

private val faq = listOf(
    "Oynama süreleri neden görünmüyor?" to
        "Süreleri ölçmek için \"Kullanım erişimi\" izni gerekir. Ayarlar > Süre takibi'nden izni verip uygulamaya dön; süreler bundan sonra kaydedilir.",
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
        "Ana sayfada sol üstteki kalem simgesine dokun. Tema, vurgu rengi, duvar kağıdı ve bulanıklık orada.",
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
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                .background(cs.surfaceVariant.copy(alpha = 0.4f))
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
    val cur = remember { Updater.current(LocalContext.current) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Game Launcher", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Sürüm ${cur.name}", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Text("Derleme ${cur.code}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Text(
            "Oyunlarını, oynama sürelerini ve widget'larını tek yerde topla.",
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
