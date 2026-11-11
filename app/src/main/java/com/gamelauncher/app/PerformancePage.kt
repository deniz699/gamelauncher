package com.gamelauncher.app

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun cpuMhz(khz: Long) = khz / 1000
private fun gpuMhz(v: Long) = when {
    v >= 1_000_000 -> v / 1_000_000
    v >= 1000 -> v / 1000
    else -> v
}

@Composable
fun PerformancePage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var rootState by remember { mutableIntStateOf(0) } // 0 kontrol ediliyor, 1 var, 2 yok
    var info by remember { mutableStateOf<PerfInfo?>(null) }
    var level by remember { mutableIntStateOf(Perf.level(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var temp by remember { mutableFloatStateOf(perfBatteryTemp(ctx)) }
    var recheck by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Int?>(null) }
    var autoT by remember { mutableStateOf(Perf.autoThermal(ctx)) }

    BackHandler(onBack = onClose)

    LaunchedEffect(recheck) {
        rootState = 0
        info = null
        val t0 = System.currentTimeMillis()
        val ok = withContext(Dispatchers.IO) { Root.available() }
        val left = 900 - (System.currentTimeMillis() - t0)   // kontrol anında bitse de dönme animasyonu görünsün
        if (left > 0) delay(left)
        rootState = if (ok) 1 else 2
        if (ok) info = withContext(Dispatchers.IO) { Perf.read() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            temp = perfBatteryTemp(ctx)
            if (level > 0 && !busy) {
                busy = true
                val m = withContext(Dispatchers.IO) { Perf.guard(ctx, temp) }
                if (m != null) {
                    level = Perf.level(ctx)
                    info = withContext(Dispatchers.IO) { Perf.read() }
                    msg = m
                }
                busy = false
            }
            delay(3000)
        }
    }

    val unlocked = rootState == 1 && info != null
    fun choose(l: Int) {
        val cur = info ?: return
        if (busy) return
        if (l > 0 && temp >= 42f) {
            msg = "Telefon zaten sıcak (%.1f°C). Soğuyunca tekrar dene.".format(temp)
            return
        }
        busy = true
        scope.launch {
            msg = withContext(Dispatchers.IO) { Perf.apply(ctx, l, cur) }
            level = Perf.level(ctx)
            info = withContext(Dispatchers.IO) { Perf.read() }
            busy = false
            if (level > 0) EdgeService.ensure(ctx)   // sayfa kapansa da ısı koruması çalışsın
        }
    }

    confirm?.let { l ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = cs.surfaceContainerHigh,
            icon = { Icon(Icons.Default.Warning, null, tint = cs.error) },
            title = { Text("Dikkat") },
            text = {
                Text(
                    (if (l == 2) "Maksimum, telefonu en çok zorlayan ayardır. " else "") +
                        "Bu ayar telefonu normalden çok daha fazla ısıtır ve pili yıpratır. " +
                        "Uzun süre yüksek ısıda kalmak bataryaya zarar verebilir. Şarjdayken kullanma. " +
                        "Devam edersen sorumluluk sana aittir."
                )
            },
            confirmButton = {
                TextButton(onClick = { val x = l; confirm = null; Perf.setAcked(ctx); choose(x) }) {
                    Text("Anladım, devam et", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Vazgeç") } }
        )
    }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTopBar("Performans", onClose)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ---- Root durumu
                FlatCard(Modifier.animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Root erişimi", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Crossfade(rootState, animationSpec = tween(250), label = "rootChip") { st ->
                            when (st) {
                                0 -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = cs.primary)
                                1 -> StatusChip(true, "Root var", "")
                                else -> StatusChip(false, "", "Root yok")
                            }
                        }
                    }
                    Text(
                        when (rootState) {
                            0 -> "Root izni isteniyor. Ekranda bir onay penceresi çıkarsa \"İzin ver\"e dokun."
                            1 -> "Root izni var. CPU ve GPU ayarları aşağıda açık."
                            else -> "Bu telefonda root bulunamadı ya da izin verilmedi. CPU / GPU hızlandırma root gerektirir, bu yüzden aşağıdaki seçenekler kilitli. Root verdikten sonra tekrar kontrol edebilirsin."
                        },
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                    Text(
                        "${Build.MANUFACTURER} ${Build.MODEL} • ${socFamily()} (${Build.HARDWARE})",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                    if (rootState == 2) {
                        val d = Root.diag
                        if (d.isNotEmpty()) {
                            Text(
                                "KernelSU kullanıyorsan: KernelSU > Süper kullanıcı listesinden bu uygulamaya izin verdiğinden emin ol, sonra tekrar kontrol et.\n\nTeknik ayrıntı:\n$d",
                                Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                    }
                    if (rootState != 1) {
                        RootCheckButton(
                            loading = rootState == 0, onClick = { if (rootState != 0) recheck++ },
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                        )
                    }
                }

                // ---- Sıcaklık
                val hot = temp >= 42f
                FlatCard(pad = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pil sıcaklığı", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (hot) "Yüksek: Dengeli'ye dönmeni öneririm" else "Normal",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hot) cs.error else cs.onSurfaceVariant
                            )
                        }
                        Text(
                            "%.1f°C".format(temp), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            color = if (hot) cs.error else cs.primary
                        )
                    }
                }

                // ---- Isıya göre otomatik düşürme
                FlatCard(pad = 16.dp) {
                    ToggleRow(
                        "Isıya göre otomatik düşür",
                        "Pil sıcaklığı 40°C'yi geçince Maksimum, Performans'a iner; 43°C'de Dengeli'ye dönülür. 45°C'de bu kapalı olsa da Dengeli'ye dönülür. " +
                            "Sayfa kapalıyken de çalışır (kenar paneli izinleri gerekir).",
                        autoT,
                        { v ->
                            autoT = v; Perf.setAutoThermal(ctx, v)
                            if (v && level > 0) EdgeService.ensure(ctx)
                        }
                    )
                }

                // ---- Uyarı
                FlatCard(color = cs.errorContainer, pad = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, Modifier.size(22.dp), tint = cs.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text("Dikkat", style = MaterialTheme.typography.titleMedium, color = cs.onErrorContainer)
                    }
                    Text(
                        "Bu ayarlar donanımı sınırlarına yakın çalıştırır. Telefon kendini koruyan sistemlere sahiptir ama risk sana aittir.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = cs.onErrorContainer
                    )
                    WarnLine("Aşırı ısınma: ", "Uzun süre yüksek hızda kalmak telefonu dokunulamayacak kadar ısıtabilir.")
                    WarnLine("Pil ömrü: ", "Yüksek ısı ve tüketim bataryayı hızla yıpratır; nadir durumlarda pil şişebilir.")
                    WarnLine("Kararsızlık: ", "Desteklenmeyen bir dosyaya yazmak donma, kendiliğinden yeniden başlama ya da uygulama kapanmasına yol açabilir.")
                    WarnLine("Garanti: ", "Root zaten garantiyi etkileyebilir; bu yüzden oluşan arızalar kapsam dışı kalabilir.")
                    WarnLine("Öneri: ", "Şarjdayken kullanma, kılıfı çıkar, serin ortamda ve kısa süre kullan.")
                }

                // ---- Güç seviyesi
                Text(
                    "Güç seviyesi", Modifier.padding(start = 4.dp, top = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                LevelOption(
                    "Dengeli", "Telefonun kendi ayarları. Pil ve ısı için en iyisi.",
                    level == 0, unlocked && !busy, !unlocked
                ) { choose(0) }
                LevelOption(
                    "Performans", "Çekirdekler ve GPU boşta bile orta-yüksek hızda bekler. Takılmaları azaltır, ısı ve pil biraz artar.",
                    level == 1, unlocked && !busy, !unlocked
                ) { confirm = 1 }
                LevelOption(
                    "Maksimum", "İşlemci performans moduna alınır, GPU en yüksek hıza kilitlenir. En akıcısı bu, ama çok ısınır; kısa süreli kullan.",
                    level == 2, unlocked && !busy, !unlocked
                ) { confirm = 2 }
                if (busy) {
                    Text("Uygulanıyor…", Modifier.padding(start = 4.dp), color = cs.onSurfaceVariant)
                }
                msg?.let {
                    Text(it, Modifier.padding(start = 4.dp), color = cs.primary, fontWeight = FontWeight.Medium)
                }

                // ---- CPU / GPU bilgisi
                val inf = info
                if (rootState == 1 && inf != null) {
                    FlatCard {
                        Text("İşlemci (CPU)", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        if (inf.cpus.isEmpty()) {
                            Text("Frekans bilgisi okunamadı.", Modifier.padding(top = 10.dp), color = cs.onSurfaceVariant)
                        }
                        inf.cpus.forEachIndexed { i, c ->
                            PerfRow(
                                "Küme ${i + 1}" + if (c.cores.isNotBlank()) " (${c.cores})" else "",
                                "${c.gov} • ${cpuMhz(c.min)}–${cpuMhz(c.max)} MHz"
                            )
                        }
                    }
                    FlatCard {
                        Text("Grafik (GPU)", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        val g = inf.gpu
                        if (g == null) {
                            Text(
                                "Bu cihazda desteklenen bir GPU kontrol dosyası bulunamadı. Yalnızca CPU ayarlanır.",
                                Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        } else {
                            PerfRow("Tür", g.kind)
                            if (g.gov.isNotEmpty()) PerfRow("Governor", g.gov)
                            if (g.max > 0) {
                                PerfRow(
                                    "Frekans",
                                    if (g.min > 0) "${gpuMhz(g.min)}–${gpuMhz(g.max)} MHz" else "en çok ${gpuMhz(g.max)} MHz"
                                )
                            }
                            if (g.cur > 0) PerfRow("Şu an", "${gpuMhz(g.cur)} MHz")
                            if (g.backend.startsWith("mtk")) {
                                Text(
                                    "MediaTek'te GPU frekans aralığı verilemez; Maksimum seviyesi GPU'yu en yüksek kademeye sabitler.",
                                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                                )
                            }
                            if (!(g.kind.startsWith("Adreno") || g.kind.contains("devfreq"))) {
                                Text(
                                    "Bu GPU yolu deneysel: farklı cihazlarda doğrulanmadı, sonuç değişebilir.",
                                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = cs.error
                                )
                            }
                        }
                    }
                }

                FlatCard(color = cs.surfaceContainerLow, pad = 16.dp) {
                    Text(
                        "Değerler telefon yeniden başlayınca ya da Dengeli seçilince sıfırlanır. Telefon 42°C üstündeyken yükseltme reddedilir; sayfa açıkken 45°C'yi geçerse otomatik Dengeli'ye dönülür (sayfa kapalıyken bu koruma çalışmaz). Snapdragon, MediaTek ve Exynos'ta üretici servisleri değerleri geri yazabilir; bu durumda sonuç \"kısmen uygulandı\" olur.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelOption(
    title: String, desc: String, selected: Boolean, enabled: Boolean, locked: Boolean, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val click = if (enabled) Modifier.flatClick(onClick = onClick) else Modifier
    val a = if (locked) 0.45f else 1f
    Row(
        Modifier.fillMaxWidth().graphicsLayer { alpha = a }.then(click)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) cs.primaryContainer else cs.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium,
                color = if (selected) cs.onPrimaryContainer else cs.onSurface
            )
            Text(
                desc, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall,
                color = if (selected) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        if (locked) {
            Icon(Icons.Default.Lock, "Kilitli", Modifier.size(20.dp), tint = cs.onSurfaceVariant)
        } else if (selected) {
            Icon(Icons.Default.CheckCircle, "Seçili", Modifier.size(24.dp), tint = cs.onPrimaryContainer)
        }
    }
}

@Composable
private fun PerfRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(label, Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
private fun WarnLine(lead: String, text: String) {
    val c = MaterialTheme.colorScheme.onErrorContainer
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(lead) }
            append(text)
        },
        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = c
    )
}

/** "Tekrar kontrol et": basınca küçülür, kontrol sürerken yerinde dönen yükleme işareti gösterir. */
@Composable
private fun RootCheckButton(loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.96f else 1f, spring(dampingRatio = 0.65f, stiffness = 450f), label = "rcScale")
    val bg by animateColorAsState(
        if (loading) cs.primaryContainer else cs.surfaceContainerHighest, tween(250), label = "rcBg"
    )
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, enabled = !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(loading, animationSpec = tween(200), label = "rcContent") { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (l) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onPrimaryContainer)
                } else {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp), tint = cs.onSurface)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (l) "Kontrol ediliyor…" else "Tekrar kontrol et",
                    color = if (l) cs.onPrimaryContainer else cs.onSurface,
                    fontWeight = FontWeight.Medium, fontSize = 14.sp
                )
            }
        }
    }
}
