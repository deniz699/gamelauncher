package com.gamelauncher.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(s: AppState) {
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val backdrop = rememberGlassBackdrop()
    val bg = MaterialTheme.colorScheme.background

    val tabs: List<Pair<String, ImageVector>> = listOf(
        "Tümü" to Icons.Default.SportsEsports,
        "Favoriler" to Icons.Default.Favorite,
        "İstatistik" to Icons.Default.BarChart
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Oyunlarım", fontWeight = FontWeight.Bold) },
                actions = {
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
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            Box(Modifier.fillMaxSize().glassSource(backdrop, bg)) {
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
                when (tab) {
                    0 -> GameGrid(s, s.sortedGames(), "Oyun bulunamadı. Telefondaki oyunlar otomatik eklenir.") { selected = it }
                    1 -> GameGrid(s, s.sortedGames().filter { it.favorite }, "Favori oyun yok. Kalbe dokunarak ekle.") { selected = it }
                    else -> StatsTab(s)
                }
            }

            if (tab != 2) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                        .padding(end = 24.dp, bottom = 104.dp)
                ) { Icon(Icons.Default.Add, "Oyun ekle") }
            }
            }

            GlassBottomBar(
                items = tabs,
                selected = tab,
                glass = s.glass,
                onSelect = { tab = it },
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .widthIn(max = 420.dp).fillMaxWidth().height(64.dp)
            )
        }
    }

    if (showAdd) AddSheet(s) { showAdd = false }
    if (showSettings) SettingsSheet(s) { showSettings = false }
    selected?.let { DetailSheet(s, it) { selected = null } }
}

@Composable
fun GameGrid(s: AppState, list: List<Game>, emptyText: String, onOpen: (String) -> Unit) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list, key = { it.pkg }) { g ->
            Card(onClick = { onOpen(g.pkg) }, shape = RoundedCornerShape(20.dp)) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        Cover(g, Modifier.fillMaxSize())
                        IconButton(
                            onClick = { s.toggleFav(g.pkg) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                if (g.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                "Favori",
                                tint = if (g.favorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Text(
                        g.name, Modifier.padding(12.dp, 8.dp, 12.dp, 4.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold
                    )
                    PlayButton(
                        onClick = { s.launch(g.pkg) },
                        modifier = Modifier.fillMaxWidth().padding(12.dp, 0.dp, 12.dp, 12.dp)
                    )
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
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Ara") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
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
                OutlinedButton(onClick = { s.toggleFav(pkg) }) {
                    Icon(if (g.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                    Spacer(Modifier.width(6.dp)); Text(if (g.favorite) "Favoriden çıkar" else "Favori")
                }
                OutlinedButton(onClick = { picker.launch("image/*") }) {
                    Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Kapak")
                }
            }
            if (g.cover != null) TextButton(onClick = { s.clearCover(pkg) }) { Text("Kapağı sıfırla") }
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
fun StatsTab(s: AppState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Tüm oyunlar", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Range.entries.forEach { r -> InfoRow(r.label, fmtDur(playedMs(s.sessions, null, r))) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Oyun başına", style = MaterialTheme.typography.titleMedium)
        s.games.sortedByDescending { playedMs(s.sessions, it.pkg, Range.ALL) }.forEach { g ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(g, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), 0.85f)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(Range.TODAY, Range.WEEK, Range.YEAR, Range.ALL)
                                .joinToString("  •  ") { "${it.label}: ${fmtDur(playedMs(s.sessions, g.pkg, it))}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

// ---------------------------------------------------------------- Ayarlar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(s: AppState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Ayarlar", style = MaterialTheme.typography.titleLarge)

            Text("Tema", Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Sistem", "Açık", "Koyu", "AMOLED").forEachIndexed { i, label ->
                    FilterChip(selected = s.themeMode == i, onClick = { s.setTheme(i) }, label = { Text(label) })
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Liquid glass alt çubuk", Modifier.weight(1f))
                Switch(checked = s.glass, onCheckedChange = { s.changeGlass(it) })
            }

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Duvar kağıdı renkleri (Material You)", Modifier.weight(1f))
                    Switch(checked = s.dynamic, onCheckedChange = { s.setDynamic(it) })
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

            Text("Süre takibi", Modifier.padding(top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
            Text(
                if (s.hasUsage) "Kullanım erişimi verildi ✓" else "Kullanım erişimi verilmedi — süreler ölçülemez.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Kullanım erişimi ayarlarını aç") }
        }
    }
}

// ---------------------------------------------------------------- Oyna butonu

/** Düz (flat) renkli, gölgesiz, ripple'sız; basınca yumuşakça küçülüp rengi açılır. */
@Composable
fun PlayButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Oyna") {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.95f else 1f, spring(dampingRatio = 0.65f, stiffness = 420f), label = "playScale")
    val base = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (down) base.copy(alpha = 0.82f) else base, tween(160), label = "playBg")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(4.dp))
        Text(label, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
    }
}
