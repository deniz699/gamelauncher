package com.gamelauncher.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Ana ekran

@Composable
fun App(s: AppState) {
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showDevice by remember { mutableStateOf(false) }
    var statsGame by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme

    // Durum/gezinme çubuğu ikonları temaya göre açık ya da koyu olsun
    val view = LocalView.current
    val darkBg = cs.background.luminance() < 0.5f
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !darkBg
                isAppearanceLightNavigationBars = !darkBg
            }
        }
    }

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

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HomeHeader(s, tab, onAppearance = { showAppearance = true }, onSettings = { showSettings = true })
            if (!s.hasUsage) UsageBanner()
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> GameGrid(
                        s, s.sortedGames(), "Oyun bulunamadı. Telefondaki oyunlar otomatik eklenir.",
                        header = { HomeSummary(s, onStats = { tab = 2 }, onDevice = { showDevice = true }) }
                    ) { selected = it }
                    1 -> GameGrid(s, s.sortedGames().filter { it.favorite }, "Favori oyun yok. Kalbe dokunarak ekle.") { selected = it }
                    else -> StatsTab(s) { statsGame = it }
                }
            }
        }

        if (tab != 2) {
            IconBox(
                Icons.Default.Add, "Oyun ekle", { showAdd = true },
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 88.dp),
                container = cs.primary, tint = cs.onPrimary, size = 56.dp, radius = 18.dp
            )
        }

        FlatBottomNav(
            items = tabs,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 420.dp).fillMaxWidth().height(64.dp)
        )

        if (showSettings) SettingsPage(s) { showSettings = false }
        if (showDevice) DevicePage { showDevice = false }
        statsGame?.let { GameStatsPage(s, it) { statsGame = null } }
    }

    if (showAdd) AddSheet(s) { showAdd = false }
    if (showAppearance) AppearanceSheet(s) { showAppearance = false }
    selected?.let { DetailSheet(s, it) { selected = null } }
}

@Composable
private fun HomeHeader(s: AppState, tab: Int, onAppearance: () -> Unit, onSettings: () -> Unit) {
    var sortMenu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val title = when (tab) { 0 -> "Oyunlarım"; 1 -> "Favoriler"; else -> "İstatistik" }
    val sub = when (tab) {
        0 -> "${s.games.size} oyun"
        1 -> "${s.games.count { it.favorite }} oyun"
        else -> "Oynama süreleri"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab != 2) {
                Box {
                    IconBox(Icons.AutoMirrored.Filled.Sort, "Sırala", { sortMenu = true })
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
            IconBox(Icons.Default.Palette, "Görünüm", onAppearance)
            IconBox(Icons.Default.Settings, "Ayarlar", onSettings)
        }
    }
}

@Composable
private fun UsageBanner() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp)).background(cs.primaryContainer)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Oynama süresini takip edebilmek için \"Kullanım erişimi\" izni gerekli.",
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer
        )
        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
            Text("İzin ver", color = cs.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

/** Ana sayfanın üstü: bugünkü süre (iri) + iki hızlı eylem. */
@Composable
private fun HomeSummary(s: AppState, onStats: () -> Unit, onDevice: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val today = remember(s.sessions) { playedMs(s.sessions, null, Range.TODAY) }
    Row(Modifier.fillMaxWidth().height(128.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1.5f).fillMaxHeight().flatClick(onClick = onStats)
                .clip(RoundedCornerShape(28.dp)).background(cs.primary).padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Bugün oynanan", color = cs.onPrimary.copy(alpha = 0.75f), fontWeight = FontWeight.Medium, fontSize = 13.sp)
            DurationText(today, cs.onPrimary, 36.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(Icons.Default.FlashOn, "RAM temizle", Modifier.weight(1f)) {
                Booster.clean(ctx, emptySet()) { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show() }
            }
            ActionTile(Icons.Default.PhoneAndroid, "Cihaz", Modifier.weight(1f), onDevice)
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().flatClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = cs.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GameGrid(
    s: AppState,
    list: List<Game>,
    emptyText: String,
    header: (@Composable () -> Unit)? = null,
    onOpen: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 168.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    emptyText, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(list, key = { it.pkg }) { g -> GameTile(s, g, onOpen) }
    }
}

/** Dokun = oyunu aç, uzun bas = detay. */
@Composable
private fun GameTile(s: AppState, g: Game, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val total = remember(s.sessions, g.pkg) { playedMs(s.sessions, g.pkg, Range.ALL) }
    Column(
        Modifier.flatClick(onLongClick = { onOpen(g.pkg) }) { s.launch(g.pkg) }
            .clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainer).padding(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
            Cover(g, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(CircleShape)
                    .background(if (g.favorite) cs.primary else cs.background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.toggleFav(g.pkg) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (g.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favori", Modifier.size(18.dp),
                    tint = if (g.favorite) cs.onPrimary else cs.onSurface
                )
            }
        }
        Text(
            g.name, Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(top = 12.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp,
            color = cs.onSurface
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, top = 8.dp, bottom = 4.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerHighest)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(fmtDur(total), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(cs.primary)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.launch(g.pkg) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PlayArrow, "Oyna", Modifier.size(22.dp), tint = cs.onPrimary) }
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
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    // Yalnızca oyun kategorisindeki uygulamalar listelenir; oyun dışı hiçbir şey eklenemez.
    val all = remember { detectInstalledGames(ctx).map { AppEntry(it.first, it.second) } }
    val added = s.games.map { it.pkg }.toSet()
    val shown = all.filter { it.pkg !in added && it.name.contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = cs.background, contentColor = cs.onSurface) {
        Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
            Text("Oyun ekle", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Yalnızca oyunlar listelenir. Yeni yüklenen oyunlar otomatik eklenir; buradan kaldırdıklarını geri ekleyebilirsin.",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            FlatSearchField(query, { query = it }, "Oyun ara", Modifier.padding(vertical = 14.dp))
            if (shown.isEmpty()) {
                Text("Eklenebilecek oyun yok.", Modifier.padding(vertical = 16.dp), color = cs.onSurfaceVariant)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.pkg }) { a ->
                    val icon = remember(a.pkg) {
                        runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    }
                    Row(
                        Modifier.fillMaxWidth().flatClick { s.add(a.pkg, a.name); onClose() }
                            .clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) Image(icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(a.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(cs.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Add, "Ekle", Modifier.size(18.dp), tint = cs.onPrimaryContainer) }
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
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) s.setCover(pkg, uri)
    }
    val installed = remember(pkg) {
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
    }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = cs.background, contentColor = cs.onSurface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp).navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(26.dp))) {
                Cover(g, Modifier.fillMaxSize())
            }
            Text(g.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
            PlayButton(onClick = { s.launch(pkg) }, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))

            StatTile(
                Range.ALL.label, playedMs(s.sessions, pkg, Range.ALL), Modifier.fillMaxWidth(),
                container = cs.primaryContainer, content = cs.onPrimaryContainer, valueSize = 32.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Range.TODAY.label, playedMs(s.sessions, pkg, Range.TODAY), Modifier.weight(1f), valueSize = 20.sp)
                StatTile(Range.WEEK.label, playedMs(s.sessions, pkg, Range.WEEK), Modifier.weight(1f), valueSize = 20.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Range.MONTH.label, playedMs(s.sessions, pkg, Range.MONTH), Modifier.weight(1f), valueSize = 20.sp)
                StatTile(Range.YEAR.label, playedMs(s.sessions, pkg, Range.YEAR), Modifier.weight(1f), valueSize = 20.sp)
            }
            Spacer(Modifier.height(10.dp))
            FlatCard(pad = 16.dp) {
                InfoRow("Telefona yüklenme", fmtDate(installed))
                InfoRow("Listeye eklenme", fmtDate(g.addedAt))
                InfoRow("Son oynanma", if (g.lastPlayed > 0) fmtDate(g.lastPlayed) else "Henüz oynanmadı")
            }

            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(
                    if (g.favorite) "Favoriden çıkar" else "Favori", { s.toggleFav(pkg) }, Modifier.weight(1f),
                    icon = if (g.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
                )
                SoftButton("Kapak", { picker.launch("image/*") }, Modifier.weight(1f), icon = Icons.Default.Image)
            }
            if (g.cover != null) SoftButton("Kapağı sıfırla", { s.clearCover(pkg) }, Modifier.fillMaxWidth().padding(top = 10.dp))
            if (s.sort == SortMode.MANUAL) {
                FlatCard(Modifier.padding(top = 10.dp), pad = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sıralamadaki yeri", Modifier.weight(1f).padding(start = 4.dp), fontWeight = FontWeight.Medium)
                        IconBox(Icons.Default.ArrowUpward, "Yukarı", { s.move(pkg, -1) }, container = cs.surfaceContainerHighest)
                        Spacer(Modifier.width(8.dp))
                        IconBox(Icons.Default.ArrowDownward, "Aşağı", { s.move(pkg, 1) }, container = cs.surfaceContainerHighest)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).flatClick { confirm = true }
                    .clip(RoundedCornerShape(14.dp)).background(cs.errorContainer).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = cs.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text("Listeden kaldır", color = cs.onErrorContainer, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = cs.surfaceContainerHigh,
        title = { Text("Kaldırılsın mı?") },
        text = { Text("${g.name} listeden ve oynama geçmişi kayıtlarından silinir. Oyun telefondan silinmez.") },
        confirmButton = { TextButton(onClick = { confirm = false; s.remove(pkg); onClose() }) { Text("Kaldır", color = cs.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- İstatistik

@Composable
fun StatsTab(s: AppState, onGame: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val totals = remember(s.sessions) { Range.entries.associateWith { playedMs(s.sessions, null, it) } }
    val ranked = remember(s.sessions, s.games) {
        s.games.map { it to playedMs(s.sessions, it.pkg, Range.ALL) }.sortedByDescending { it.second }
    }
    val maxMs = (ranked.firstOrNull()?.second ?: 0L).coerceAtLeast(1L)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.primary).padding(22.dp)) {
            Text("Toplam oynama süresi", color = cs.onPrimary.copy(alpha = 0.75f), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            DurationText(totals[Range.ALL] ?: 0L, cs.onPrimary, 48.sp)
        }
        Spacer(Modifier.height(10.dp))
        listOf(Range.TODAY to Range.WEEK, Range.MONTH to Range.YEAR).forEach { (a, b) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(a.label, totals[a] ?: 0L, Modifier.weight(1f))
                StatTile(b.label, totals[b] ?: 0L, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        Text(
            "En çok oynananlar", Modifier.padding(start = 4.dp, top = 14.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleMedium
        )
        ranked.forEachIndexed { i, (g, ms) ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).flatClick { onGame(g.pkg) }
                    .clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainer).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}", Modifier.width(26.dp), textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = if (i == 0) cs.primary else cs.onSurfaceVariant
                )
                Cover(g, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), 0.85f)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Text(fmtDur(ms), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.surfaceContainerHighest)) {
                        Box(
                            Modifier.fillMaxWidth((ms.toFloat() / maxMs).coerceIn(0.02f, 1f)).fillMaxHeight().background(cs.primary)
                        )
                    }
                }
            }
        }
        if (ranked.isEmpty()) {
            Text("Henüz oyun yok.", Modifier.padding(4.dp), color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(160.dp))
    }
}

// ---------------------------------------------------------------- Düz (flat) bileşenler

/** Düz renkli, gölgesiz, ripple'sız; basınca yumuşakça küçülür. Yazı rengi zemine göre siyah/beyaz. */
@Composable
fun PlayButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Oyna", compact: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.95f else 1f, spring(dampingRatio = 0.65f, stiffness = 420f), label = "playScale")
    val base = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (down) base.copy(alpha = 0.82f) else base, tween(160), label = "playBg")
    val fg = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = if (compact) 6.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = if (compact) 12.sp else 15.sp)
    }
}

/** Düz, tonlu buton: gölge/ripple yok, basınca yumuşakça küçülür. */
@Composable
fun SoftButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.96f else 1f, spring(dampingRatio = 0.65f, stiffness = 450f), label = "sbScale")
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (down) cs.surfaceContainerHighest.copy(alpha = 0.7f) else cs.surfaceContainerHighest, tween(140), label = "sbBg"
    )
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = cs.onSurface)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = cs.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) cs.primary else Color.Transparent, tween(180), label = "segBg")
            val fg by animateColorAsState(if (sel) cs.onPrimary else cs.onSurfaceVariant, tween(180), label = "segFg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}
