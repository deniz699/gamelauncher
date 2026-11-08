package com.gamelauncher.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import android.os.Build
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

// ---------------------------------------------------------------- Ana ekran

/** Arka plana göre okunur yazı rengi: koyu temada beyaz, açık temada siyah. */
@Composable
fun onBgColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(s: AppState) {
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showDevice by remember { mutableStateOf(false) }
    var statsGame by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    // Geri: Favoriler/İstatistik'ten ana sayfaya döner, ana sayfadayken uygulamadan çıkar
    BackHandler(enabled = tab != 0) { tab = 0 }
    // Sağa/sola kaydırarak sayfa değiştirme (alt çubukla senkron)
    val pagerState = rememberPagerState { 3 }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab) pagerState.animateScrollToPage(tab)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { tab = it }
    }

    val tabs: List<Pair<String, ImageVector>> = listOf(
        "Tümü" to Icons.Default.SportsEsports,
        "Favoriler" to Icons.Default.Favorite,
        "İstatistik" to Icons.Default.BarChart
    )

    Box(Modifier.fillMaxSize()) {
        // Arka plan: bulanık oyun görseli + temaya uyan yumuşak perde
        val canBlur = Build.VERSION.SDK_INT >= 31
        val bgBmp = remember(s.bgPath, s.bgBlur) {
            s.bgPath?.let { loadBg(it, if (s.bgBlur && !canBlur) 220 else 1600) }
        }
        val bgMod = Modifier.fillMaxSize().then(
            if (s.bgBlur && canBlur) Modifier.blur(12.dp, BlurredEdgeTreatment.Unbounded) else Modifier
        )
        Box(Modifier.fillMaxSize().clipToBounds()) {
            if (bgBmp != null) {
                Image(bgBmp, null, bgMod, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
            } else {
                Image(painterResource(R.drawable.bg_games), null, bgMod, contentScale = ContentScale.Crop)
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)))

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = onBgColor(),
            topBar = {
                TopAppBar(
                    title = { Text("Oyunlarım", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = onBgColor(),
                        actionIconContentColor = onBgColor()
                    ),
                    actions = {
                        IconButton(onClick = { showDevice = true }) {
                            Icon(Icons.Default.PhoneAndroid, "Cihaz bilgileri")
                        }
                        if (tab != 2) {
                            Box {
                                IconButton(onClick = { sortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, "Sırala")
                                }
                                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    SortMode.entries.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.label) },
                                            leadingIcon = { if (s.sort == m) Icon(Icons.Default.Check, null) },
                                            onClick = { s.setSort(m); sortMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Ayarlar") }
                    }
                )
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            Column(Modifier.fillMaxSize()) {
                if (!s.hasUsage) {
                    Card(Modifier.fillMaxWidth().padding(12.dp, 8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Oynama süresini takip edebilmek için \"Kullanım erişimi\" izni gerekli.",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                                Text("İzin ver")
                            }
                        }
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> GameGrid(s, s.sortedGames(), "Oyun bulunamadı. Telefondaki oyunlar otomatik eklenir.") { selected = it }
                    1 -> GameGrid(s, s.sortedGames().filter { it.favorite }, "Favori oyun yok. Kalbe dokunarak ekle.") { selected = it }
                    else -> StatsTab(s) { statsGame = it }
                }
                }
            }

            if (tab != 2) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                        .padding(end = 24.dp, bottom = 104.dp)
                ) { Icon(Icons.Default.Add, "Oyun ekle") }
            }

            FloatingBottomBar(
                items = tabs,
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .widthIn(max = 420.dp).fillMaxWidth().height(64.dp)
            )
            }
        }
        if (showSettings) SettingsPage(s) { showSettings = false }
        if (showDevice) DevicePage { showDevice = false }
        statsGame?.let { GameStatsPage(s, it) { statsGame = null } }
    }

    if (showAdd) AddSheet(s) { showAdd = false }
    selected?.let { DetailSheet(s, it) { selected = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameGrid(s: AppState, list: List<Game>, emptyText: String, onOpen: (String) -> Unit) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, Modifier.padding(32.dp), color = onBgColor())
        }
        return
    }
    val panel = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxSize()
            .padding(12.dp, 8.dp, 12.dp, 100.dp)
            .clip(panel)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f))
            .border(0.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), panel)
    ) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(list, key = { it.pkg }) { g ->
            // Yarı saydam kutu; dokun = oyunu aç, uzun bas = detay
            val shape = RoundedCornerShape(14.dp)
            val textColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f), contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
                modifier = Modifier.clip(shape).combinedClickable(
                    onClick = { s.launch(g.pkg) },
                    onLongClick = { onOpen(g.pkg) }
                )
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        Cover(g, Modifier.fillMaxSize())
                        IconButton(
                            onClick = { s.toggleFav(g.pkg) },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                        ) {
                            Icon(
                                if (g.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                "Favori",
                                Modifier.size(16.dp),
                                tint = if (g.favorite) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                    Text(
                        g.name,
                        Modifier.fillMaxWidth().padding(4.dp, 6.dp, 4.dp, 6.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp,
                        textAlign = TextAlign.Center, color = textColor
                    )
                }
            }
        }
    }
    }
}

// ---------------------------------------------------------------- Kapak görseli

private fun loadCover(path: String): ImageBitmap? = runCatching {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, b)
    var sample = 1
    while (b.outWidth / sample > 800) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

private fun loadBg(path: String, maxW: Int): ImageBitmap? = runCatching {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, b)
    var sample = 1
    while (b.outWidth / sample > maxW) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

@Composable
fun Cover(g: Game, modifier: Modifier, iconFraction: Float = 0.5f) {
    val ctx = LocalContext.current
    val path = g.cover ?: g.autoCover
    val cover = remember(path) { path?.let { loadCover(it) } }
    if (cover != null) {
        Image(cover, g.name, modifier, contentScale = ContentScale.Crop)
    } else {
        val icon = remember(g.pkg) {
            runCatching { ctx.packageManager.getApplicationIcon(g.pkg).toBitmap(192, 192).asImageBitmap() }.getOrNull()
        }
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, g.name, Modifier.fillMaxSize(iconFraction))
        }
    }
}

// ---------------------------------------------------------------- Oyun ekle

private data class AppEntry(val pkg: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(s: AppState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Yalnızca oyun kategorisindeki uygulamalar listelenir; oyun dışı hiçbir şey eklenemez.
    val all = remember { detectInstalledGames(ctx).map { AppEntry(it.first, it.second) } }
    val added = s.games.map { it.pkg }.toSet()
    val shown = all.filter { it.pkg !in added && it.name.contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
            Text("Oyun ekle", style = MaterialTheme.typography.titleLarge)
            Text(
                "Yalnızca oyunlar listelenir. Yeni yüklenen oyunlar otomatik eklenir; buradan kaldırdıklarını geri ekleyebilirsin.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (shown.isEmpty()) {
                Text("Eklenebilecek oyun yok.", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Oyun ara") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Temizle") }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            )
            LazyColumn {
                items(shown, key = { it.pkg }) { a ->
                    val icon = remember(a.pkg) {
                        runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { s.add(a.pkg, a.name); onClose() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) Image(icon, null, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.Add, "Ekle")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Oyun detayı

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(s: AppState, pkg: String, onClose: () -> Unit) {
    val g = s.games.find { it.pkg == pkg }
    if (g == null) { LaunchedEffect(Unit) { onClose() }; return }
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) s.setCover(pkg, uri)
    }
    val installed = remember(pkg) {
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp))) {
                Cover(g, Modifier.fillMaxSize())
            }
            Text(g.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            PlayButton(onClick = { s.launch(pkg) }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))

            InfoRow("Telefona yüklenme", fmtDate(installed))
            InfoRow("Listeye eklenme", fmtDate(g.addedAt))
            InfoRow("Son oynanma", if (g.lastPlayed > 0) fmtDate(g.lastPlayed) else "Henüz oynanmadı")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Range.entries.forEach { r ->
                val ms = playedMs(s.sessions, pkg, r)
                InfoRow(r.label, if (r == Range.ALL) "${fmtDur(ms)} (${ms / 60_000} dakika)" else fmtDur(ms))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(
                    if (g.favorite) "Favoriden çıkar" else "Favori", { s.toggleFav(pkg) },
                    icon = if (g.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
                )
                SoftButton("Kapak", { picker.launch("image/*") }, icon = Icons.Default.Image)
            }
            if (g.cover != null) SoftButton("Kapağı sıfırla", { s.clearCover(pkg) }, Modifier.padding(top = 8.dp))
            if (s.sort == SortMode.MANUAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sıralamadaki yeri", Modifier.weight(1f))
                    IconButton(onClick = { s.move(pkg, -1) }) { Icon(Icons.Default.ArrowUpward, "Yukarı") }
                    IconButton(onClick = { s.move(pkg, 1) }) { Icon(Icons.Default.ArrowDownward, "Aşağı") }
                }
            }
            TextButton(
                onClick = { confirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Listeden kaldır") }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Kaldırılsın mı?") },
        text = { Text("${g.name} listeden ve oynama geçmişi kayıtlarından silinir. Oyun telefondan silinmez.") },
        confirmButton = { TextButton(onClick = { confirm = false; s.remove(pkg); onClose() }) { Text("Kaldır") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- İstatistik

@Composable
fun StatsTab(s: AppState, onGame: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), contentColor = MaterialTheme.colorScheme.onSurface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Tüm oyunlar", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Range.entries.forEach { r -> InfoRow(r.label, fmtDur(playedMs(s.sessions, null, r))) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("En çok oynananlar", style = MaterialTheme.typography.titleMedium, color = onBgColor())
        s.games.sortedByDescending { playedMs(s.sessions, it.pkg, Range.ALL) }.forEach { g ->
            Card(onClick = { onGame(g.pkg) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), contentColor = MaterialTheme.colorScheme.onSurface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(g, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), 0.85f)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        g.name, Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        fmtDur(playedMs(s.sessions, g.pkg, Range.ALL)),
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

// ---------------------------------------------------------------- Ayarlar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(s: AppState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) s.setBackground(uri)
    }
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Text("Ayarlar", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding()) {

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

            WidgetColorSettings()

            Text("Oyun içi kenar paneli", Modifier.padding(top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kenar panelini aç")
                    Text(
                        "Oyundayken ekranın kenarında küçük bir çizgi çıkar. Dokununca ya da içeri kaydırınca seçtiğin uygulamaların kısayolları açılır (uygulamaları aşağıdan seç); kısayola dokunmak uygulamayı açılır pencerede (cihaz destekliyorsa) başlatır, uzun basmak normal açar. \"Diğer uygulamaların üzerinde göster\" ve kullanım erişimi izni gerekir; izni verip geri dönünce anahtarı tekrar aç.",
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

            Text("Süre takibi", Modifier.padding(top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
            Text(
                if (s.hasUsage) "Kullanım erişimi verildi ✓" else "Kullanım erişimi verilmedi — süreler ölçülemez.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SoftButton(
                "Kullanım erişimi ayarlarını aç",
                { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                Modifier.padding(top = 8.dp)
            )
        }
        }
    }
}

// ---------------------------------------------------------------- Oyna butonu

/** Düz (flat) renkli, gölgesiz, ripple'sız; basınca yumuşakça küçülür. Yazı rengi zemine göre siyah/beyaz. */
@Composable
fun PlayButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Oyna", compact: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.95f else 1f, spring(dampingRatio = 0.65f, stiffness = 420f), label = "playScale")
    val base = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (down) base.copy(alpha = 0.82f) else base, tween(160), label = "playBg")
    val fg = if (base.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = if (compact) 6.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 12.sp else 14.sp)
    }
}

// ---------------------------------------------------------------- Düz (flat) bileşenler

/** Düz, tonlu buton: gölge/ripple yok, basınca yumuşakça küçülür. */
@Composable
fun SoftButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.96f else 1f, spring(dampingRatio = 0.65f, stiffness = 450f), label = "sbScale")
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (down) cs.secondaryContainer.copy(alpha = 0.7f) else cs.secondaryContainer, tween(140), label = "sbBg"
    )
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = cs.onSecondaryContainer, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/** Düz, modern açma/kapama düğmesi (tema rengine uyar). */
@Composable
fun FlatSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val x by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "swX")
    val track by animateColorAsState(if (checked) cs.primary else cs.surfaceContainerHighest, tween(180), label = "swT")
    val thumb by animateColorAsState(if (checked) cs.onPrimary else cs.outline, tween(180), label = "swH")
    Box(
        Modifier
            .width(48.dp).height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .toggleable(
                value = checked, role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onValueChange = onCheckedChange
            )
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .offset { IntOffset((x * 20.dp.toPx()).roundToInt(), 0) }
                .size(20.dp).clip(CircleShape).background(thumb)
        )
    }
}

/** Yan yana seçenekler; seçili olan tema rengiyle dolu. */
@Composable
fun SegmentedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) cs.primary else Color.Transparent, tween(180), label = "segBg")
            val fg by animateColorAsState(if (sel) cs.onPrimary else cs.onSurfaceVariant, tween(180), label = "segFg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}
