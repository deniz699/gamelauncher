package com.gamelauncher.app

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.graphics.lerp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.animateDpAsState
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
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var showNewGroup by remember { mutableStateOf(false) }
    var delGroup by remember { mutableStateOf<String?>(null) }
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

    // Geri: aramayı kapatır; Favoriler/İstatistik'ten ana sayfaya döner; ana sayfadayken uygulamadan çıkar
    BackHandler(enabled = tab != 0) { tab = 0 }
    BackHandler(enabled = searching) { searching = false; query = "" }
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

    val activeGroup = group?.takeIf { it in s.groups }
    val filtering = query.isNotBlank() || activeGroup != null
    fun visible(list: List<Game>): List<Game> = list.filter { g ->
        (activeGroup == null || s.groupOf[g.pkg] == activeGroup) &&
            g.name.contains(query.trim(), ignoreCase = true)
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HomeHeader(
                s, tab, searching, query, { query = it },
                onSearch = { searching = !searching; if (!searching) query = "" },
                onAppearance = { showAppearance = true },
                onSettings = { showSettings = true }
            )
            if (tab != 2) {
                FilterRow(s, activeGroup, { group = it }, { showNewGroup = true }, { delGroup = it })
            }
            if (!s.hasUsage) UsageBanner()
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> GameGrid(
                        s, visible(s.sortedGames()),
                        if (filtering) "Eşleşen oyun yok." else "Oyun bulunamadı. Telefondaki oyunlar otomatik eklenir.",
                        header = { HomeExtras(s, onStats = { tab = 2 }, onDevice = { showDevice = true }) },
                        showHeader = !filtering
                    ) { selected = it }
                    1 -> GameGrid(
                        s, visible(s.sortedGames().filter { it.favorite }),
                        if (filtering) "Eşleşen oyun yok." else "Favori oyun yok. Kalbe dokunarak ekle."
                    ) { selected = it }
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

    if (showNewGroup) {
        NewGroupDialog(
            onAdd = { name -> s.addGroup(name)?.let { group = it } },
            onClose = { showNewGroup = false }
        )
    }
    delGroup?.let { n ->
        AlertDialog(
            onDismissRequest = { delGroup = null },
            containerColor = cs.surfaceContainerHigh,
            title = { Text("Grup silinsin mi?") },
            text = { Text("\"$n\" grubu silinir. Oyunlar silinmez, sadece gruptan çıkar.") },
            confirmButton = {
                TextButton(onClick = { s.removeGroup(n); if (group == n) group = null; delGroup = null }) {
                    Text("Sil", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { delGroup = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun HomeHeader(
    s: AppState, tab: Int, searching: Boolean, query: String, onQuery: (String) -> Unit,
    onSearch: () -> Unit, onAppearance: () -> Unit, onSettings: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val title = when (tab) { 0 -> "Oyunlarım"; 1 -> "Favoriler"; else -> "İstatistik" }
    val sub = when (tab) {
        0 -> "${s.games.size} oyun"
        1 -> "${s.games.count { it.favorite }} oyun"
        else -> "Oynama süreleri"
    }
    val expanded = searching && tab != 2
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    ) {
        // Kutu, sağdaki palet + ayar düğmelerinin soluna kadar genişler
        val full = maxWidth - 104.dp
        val sw by animateDpAsState(
            if (expanded) full else 44.dp,
            spring(dampingRatio = 0.85f, stiffness = 380f), label = "searchW"
        )
        val prog = if (full > 44.dp) ((sw - 44.dp) / (full - 44.dp)).coerceIn(0f, 1f) else 0f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).graphicsLayer { alpha = 1f - prog }) {
                Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1)
            }
            if (tab != 2) {
                SearchPill(sw, expanded, query, onQuery, onSearch)
                Spacer(Modifier.width(8.dp))
            }
            IconBox(Icons.Default.Palette, "Görünüm", onAppearance)
            Spacer(Modifier.width(8.dp))
            IconBox(Icons.Default.Settings, "Ayarlar", onSettings)
        }
    }
}

/** Dokununca soldan sağa değil, sağdan sola doğru kayarak açılan arama kutusu. */
@Composable
private fun SearchPill(
    width: Dp, active: Boolean, query: String, onQuery: (String) -> Unit, onToggle: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val fr = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val src = remember { MutableInteractionSource() }
    LaunchedEffect(active) {
        if (active) {
            delay(220)
            runCatching { fr.requestFocus() }
        } else {
            focus.clearFocus()
        }
    }
    Row(
        Modifier.width(width).height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) cs.surfaceContainerHighest else cs.surfaceContainerHigh)
            .clickable(enabled = !active, interactionSource = src, indication = null) { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Search, "Ara", Modifier.size(22.dp), tint = cs.onSurface)
        }
        if (width > 120.dp) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Oyun ara", color = cs.onSurfaceVariant, fontSize = 16.sp, maxLines = 1)
                }
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
            }
            Box(
                Modifier.size(44.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Close, "Aramayı kapat", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
        }
    }
}

@Composable
private fun FlatChip(
    label: String, selected: Boolean,
    onLongClick: (() -> Unit)? = null, onTrailing: (() -> Unit)? = null, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else cs.surfaceContainerHigh, tween(180), label = "chipBg")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, tween(180), label = "chipFg")
    Row(
        Modifier.flatClick(onLongClick = onLongClick, onClick = onClick)
            .clip(RoundedCornerShape(50)).background(bg)
            .padding(start = 16.dp, end = if (onTrailing != null) 8.dp else 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        if (onTrailing != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(fg.copy(alpha = 0.18f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTrailing() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Close, "Grubu sil", Modifier.size(14.dp), tint = fg) }
        }
    }
}

/** Gruplar (seçiliyken ✕ ile ya da uzun basınca silinir) + liste/ızgara geçişi + sıralama. */
@Composable
private fun FilterRow(
    s: AppState, active: String?, onSelect: (String?) -> Unit, onNew: () -> Unit, onDelete: (String) -> Unit
) {
    var sortMenu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FlatChip("Tümü", active == null) { onSelect(null) } }
            items(s.groups, key = { it }) { n ->
                val del = if (active == n) ({ onDelete(n) }) else null
                FlatChip(n, active == n, onLongClick = { onDelete(n) }, onTrailing = del) { onSelect(n) }
            }
            item { FlatChip("+ Grup", false) { onNew() } }
        }
        Spacer(Modifier.width(8.dp))
        IconBox(
            if (s.listView) Icons.Default.GridView else Icons.Default.ViewAgenda,
            if (s.listView) "Izgara görünümü" else "Liste görünümü",
            { s.changeListView(!s.listView) },
            size = 40.dp, radius = 12.dp
        )
        Spacer(Modifier.width(8.dp))
        Box {
            IconBox(Icons.AutoMirrored.Filled.Sort, "Sırala", { sortMenu = true }, size = 40.dp, radius = 12.dp)
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
}

@Composable
private fun NewGroupDialog(onAdd: (String) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = cs.surfaceContainerHigh,
        title = { Text("Yeni grup") },
        text = {
            TextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                placeholder = { Text("Örn. Battle Royale") },
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHighest,
                    unfocusedContainerColor = cs.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name); onClose() }) { Text("Oluştur") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Vazgeç") } }
    )
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

/** Ana sayfanın üstü: bugünkü süre + hızlı eylemler, kaldığın yer şeridi, haftalık özet. */
@Composable
private fun HomeExtras(s: AppState, onStats: () -> Unit, onDevice: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HomeSummary(s, onStats, onDevice)
        RecentStrip(s)
        WeekCard(s)
    }
}

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

private fun agoText(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "Az önce"
        m < 60 -> "$m dk önce"
        m < 24 * 60 -> "${m / 60} sa önce"
        else -> "${m / (24 * 60)} gün önce"
    }
}

/** En son açılan 4 oyun: tek dokunuşla devam. */
@Composable
private fun RecentStrip(s: AppState) {
    val cs = MaterialTheme.colorScheme
    val recent = s.games.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(4)
    if (recent.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Kaldığın yerden", Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleMedium
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recent, key = { it.pkg }) { g ->
                Row(
                    Modifier.flatClick { s.launch(g.pkg) }
                        .clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainer).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(g, Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)), 0.85f)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.widthIn(max = 110.dp)) {
                        Text(g.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(agoText(g.lastPlayed), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(34.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, "Devam et", Modifier.size(20.dp), tint = cs.onPrimary)
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

private fun weekBuckets(sessions: List<Session>): List<Triple<String, String, Long>> {
    val z = ZoneId.systemDefault()
    val today = LocalDate.now(z)
    val short = DateTimeFormatter.ofPattern("EEE", Locale("tr"))
    val full = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("tr"))
    return (6 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        val a0 = d.atStartOfDay(z).toInstant().toEpochMilli()
        val b0 = d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        var sum = 0L
        for (se in sessions) {
            val a = maxOf(se.start, a0)
            val b = minOf(se.end, b0)
            if (b > a) sum += b - a
        }
        Triple(d.format(short), d.format(full), sum)
    }
}

/** Son 7 gün: bir çubuğa dokununca o günün süresi üstte görünür. */
@Composable
private fun WeekCard(s: AppState) {
    val cs = MaterialTheme.colorScheme
    val days = remember(s.sessions) { weekBuckets(s.sessions) }
    var sel by remember { mutableIntStateOf(6) }
    val maxV = days.maxOf { it.third }.coerceAtLeast(60_000L)
    val cur = days[sel.coerceIn(0, 6)]
    FlatCard(Modifier.padding(top = 10.dp), pad = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (sel == 6) "Bugün" else cur.second,
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Haftalık toplam: ${fmtDur(days.sumOf { it.third })}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            DurationText(cur.third, cs.onSurface, 22.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).height(84.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEachIndexed { i, (label, _, ms) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sel = i },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val h = (56f * ms.toFloat() / maxV).coerceAtLeast(5f)
                    Box(
                        Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (i == sel) cs.primary else cs.primaryContainer)
                    )
                    Text(
                        label, Modifier.padding(top = 6.dp), fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == sel) cs.onSurface else cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GameGrid(
    s: AppState,
    list: List<Game>,
    emptyText: String,
    header: (@Composable () -> Unit)? = null,
    showHeader: Boolean = true,
    onOpen: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (s.listView) 1 else 2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 168.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (header != null && showHeader) {
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
        itemsIndexed(list, key = { _, g -> g.pkg }) { i, g ->
            if (s.listView) GameRow(s, g, i, onOpen) else GameTile(s, g, i, onOpen)
        }
    }
}

/** İlk kartlar sırayla yukarı kayarak belirir (hafif, tek seferlik). */
@Composable
private fun Modifier.enterAnim(index: Int): Modifier {
    if (index > 9) return this
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 45L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(320), label = "enA")
    val y by animateFloatAsState(if (shown) 0f else 24f, tween(320), label = "enY")
    return this.graphicsLayer { alpha = a; translationY = y * density }
}

/** Dokun = oyunu aç, uzun bas = detay. */
@Composable
private fun GameTile(s: AppState, g: Game, index: Int, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val total = remember(s.sessions, g.pkg) { playedMs(s.sessions, g.pkg, Range.ALL) }
    Column(
        Modifier.enterAnim(index).flatClick(onLongClick = { onOpen(g.pkg) }) { s.launch(g.pkg) }
            .clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainer).padding(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
            Cover(g, Modifier.fillMaxSize())
            HeartButton(
                g.favorite, { s.toggleFav(g.pkg) },
                Modifier.align(Alignment.TopEnd).padding(8.dp), 34.dp, cs.background, 18.dp
            )
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
                Modifier.size(36.dp).flatClick { s.launch(g.pkg) }.clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PlayArrow, "Oyna", Modifier.size(22.dp), tint = cs.onPrimary) }
        }
    }
}

/** Kompakt liste görünümü. */
@Composable
private fun GameRow(s: AppState, g: Game, index: Int, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val total = remember(s.sessions, g.pkg) { playedMs(s.sessions, g.pkg, Range.ALL) }
    Row(
        Modifier.enterAnim(index).fillMaxWidth().flatClick(onLongClick = { onOpen(g.pkg) }) { s.launch(g.pkg) }
            .clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(g, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                g.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp
            )
            Spacer(Modifier.height(4.dp))
            Text(fmtDur(total), fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
        HeartButton(g.favorite, { s.toggleFav(g.pkg) }, size = 40.dp, container = cs.surfaceContainerHighest, iconSize = 20.dp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(40.dp).flatClick { s.launch(g.pkg) }.clip(CircleShape).background(cs.primary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PlayArrow, "Oyna", Modifier.size(24.dp), tint = cs.onPrimary) }
        Spacer(Modifier.width(2.dp))
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

/** Kapak görselinin baskın canlı rengi (oyuna özel vurgu rengi için). */
private fun coverColor(ctx: Context, g: Game): Color? = runCatching {
    val path = g.cover ?: g.autoCover
    val decoded: Bitmap? = if (path != null) {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
    } else {
        ctx.packageManager.getApplicationIcon(g.pkg).toBitmap(48, 48)
    }
    val src = decoded ?: return@runCatching null
    val bmp = Bitmap.createScaledBitmap(src, 24, 24, true)
    val hsv = FloatArray(3)
    var r = 0.0; var gr = 0.0; var b = 0.0; var wSum = 0.0
    for (y in 0 until 24) for (x in 0 until 24) {
        val px = bmp.getPixel(x, y)
        android.graphics.Color.colorToHSV(px, hsv)
        val w = (hsv[1] * hsv[2]).toDouble().let { it * it }
        r += android.graphics.Color.red(px) * w
        gr += android.graphics.Color.green(px) * w
        b += android.graphics.Color.blue(px) * w
        wSum += w
    }
    if (wSum < 0.5) return@runCatching null
    val avg = android.graphics.Color.rgb((r / wSum).toInt(), (gr / wSum).toInt(), (b / wSum).toInt())
    android.graphics.Color.colorToHSV(avg, hsv)
    hsv[1] = maxOf(hsv[1], 0.55f)
    hsv[2] = hsv[2].coerceIn(0.75f, 1f)
    Color(android.graphics.Color.HSVToColor(hsv))
}.getOrNull()

/** Detay sayfası oyunun kapak rengiyle açılır. */
@Composable
fun DetailSheet(s: AppState, pkg: String, onClose: () -> Unit) {
    val g = s.games.find { it.pkg == pkg }
    if (g == null) { LaunchedEffect(Unit) { onClose() }; return }
    val ctx = LocalContext.current
    val base = MaterialTheme.colorScheme
    val tint = remember(g.pkg, g.cover, g.autoCover) { coverColor(ctx, g) }
    val scheme = if (tint == null) base else {
        val dark = base.background.luminance() < 0.5f
        val p = if (dark) tint else lerp(tint, Color.Black, 0.15f)
        val onP = if (p.luminance() > 0.45f) Color(0xFF111111) else Color.White
        base.copy(
            primary = p, onPrimary = onP,
            primaryContainer = lerp(base.background, p, if (dark) 0.30f else 0.22f),
            onPrimaryContainer = if (dark) lerp(p, Color.White, 0.65f) else lerp(p, Color.Black, 0.45f),
            secondary = p, onSecondary = onP, tertiary = p, onTertiary = onP
        )
    }
    MaterialTheme(colorScheme = scheme) { DetailSheetContent(s, pkg, onClose) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheetContent(s: AppState, pkg: String, onClose: () -> Unit) {
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

            Text(
                "Grup", Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant
            )
            if (s.groups.isEmpty()) {
                Text(
                    "Ana ekrandaki \"+ Grup\" ile grup oluştur, sonra buradan oyuna ata.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FlatChip("Yok", s.groupOf[pkg] == null) { s.setGroup(pkg, null) } }
                    items(s.groups, key = { it }) { n -> FlatChip(n, s.groupOf[pkg] == n) { s.setGroup(pkg, n) } }
                }
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

private class Records(
    val curStreak: Int,
    val bestStreak: Int,
    val longest: Session?,
    val bestDay: Pair<LocalDate, Long>?
)

private fun computeRecords(sessions: List<Session>): Records {
    val z = ZoneId.systemDefault()
    val perDay = HashMap<LocalDate, Long>()
    for (se in sessions) {
        var day = Instant.ofEpochMilli(se.start).atZone(z).toLocalDate()
        val lastDay = Instant.ofEpochMilli(se.end).atZone(z).toLocalDate()
        var guard = 0
        while (!day.isAfter(lastDay) && guard++ < 400) {
            val a = maxOf(se.start, day.atStartOfDay(z).toInstant().toEpochMilli())
            val b = minOf(se.end, day.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli())
            if (b > a) perDay[day] = (perDay[day] ?: 0L) + (b - a)
            day = day.plusDays(1)
        }
    }
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (dd in perDay.keys.sorted()) {
        run = if (prev != null && prev.plusDays(1) == dd) run + 1 else 1
        if (run > best) best = run
        prev = dd
    }
    val today = LocalDate.now(z)
    var cur = 0
    var cursor = if (perDay.containsKey(today)) today else today.minusDays(1)
    while (perDay.containsKey(cursor)) { cur++; cursor = cursor.minusDays(1) }
    return Records(
        cur, best,
        sessions.maxByOrNull { it.end - it.start },
        perDay.entries.maxByOrNull { it.value }?.let { it.key to it.value }
    )
}

@Composable
private fun RecordTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainer).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, maxLines = 1)
        Text(sub, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Oturum geçmişini CSV olarak paylaşır (Excel için ; ayraçlı, UTF-8). */
private fun exportCsv(ctx: Context, s: AppState) {
    runCatching {
        val names = s.games.associate { it.pkg to it.name }
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("\uFEFFoyun;paket;baslangic;bitis;sure_dk\n")
        s.sessions.sortedBy { it.start }.forEach { se ->
            val n = (names[se.pkg] ?: se.pkg).replace("\"", "\"\"")
            sb.append('"').append(n).append("\";").append(se.pkg).append(';')
                .append(f.format(Date(se.start))).append(';').append(f.format(Date(se.end))).append(';')
                .append((se.end - se.start) / 60_000).append('\n')
        }
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "oyun-istatistikleri.csv")
        file.writeText(sb.toString())
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "İstatistikleri paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        android.widget.Toast.makeText(ctx, "Dışa aktarılamadı", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun StatsTab(s: AppState, onGame: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val totals = remember(s.sessions) { Range.entries.associateWith { playedMs(s.sessions, null, it) } }
    val ranked = remember(s.sessions, s.games) {
        s.games.map { it to playedMs(s.sessions, it.pkg, Range.ALL) }.sortedByDescending { it.second }
    }
    val rec = remember(s.sessions) { computeRecords(s.sessions) }
    val maxMs = (ranked.firstOrNull()?.second ?: 0L).coerceAtLeast(1L)
    val dayFmt = remember { DateTimeFormatter.ofPattern("d MMMM", Locale("tr")) }

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
            "Seri ve rekorlar", Modifier.padding(start = 4.dp, top = 14.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleMedium
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecordTile(
                "Mevcut seri", "${rec.curStreak} gün",
                if (rec.curStreak > 0) "Üst üste oynanan gün" else "Bugün oyna, seri başlasın",
                Modifier.weight(1f)
            )
            RecordTile("En uzun seri", "${rec.bestStreak} gün", "Şimdiye kadarki en iyi", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val l = rec.longest
            RecordTile(
                "En uzun oturum", if (l != null) fmtDur(l.end - l.start) else "—",
                l?.let { x -> s.games.find { it.pkg == x.pkg }?.name ?: "" } ?: "Henüz kayıt yok",
                Modifier.weight(1f)
            )
            val bd = rec.bestDay
            RecordTile(
                "En yoğun gün", if (bd != null) fmtDur(bd.second) else "—",
                bd?.first?.format(dayFmt) ?: "Henüz kayıt yok",
                Modifier.weight(1f)
            )
        }

        Text(
            "En çok oynananlar", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
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
        SoftButton(
            "CSV olarak paylaş", { exportCsv(ctx, s) },
            Modifier.fillMaxWidth().padding(top = 6.dp), icon = Icons.Default.Share
        )
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
                Modifier.weight(1f).flatClick { onSelect(i) }.clip(RoundedCornerShape(14.dp)).background(bg)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}
