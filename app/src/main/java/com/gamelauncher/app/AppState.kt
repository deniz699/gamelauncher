package com.gamelauncher.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.Executors

/** Telefonda yüklü, kategorisi "oyun" olan başlatılabilir uygulamalar (pkg to ad). */
@Suppress("DEPRECATION")
fun detectInstalledGames(ctx: Context): List<Pair<String, String>> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .filter {
            val ai = it.activityInfo.applicationInfo
            ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        }
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .distinctBy { it.first }
        .filter { it.first != ctx.packageName }
        .sortedBy { it.second.lowercase() }
}

class AppState(private val ctx: Context) {
    private val store = Store(ctx)
    private val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var games by mutableStateOf(store.loadGames())
    var sessions by mutableStateOf(store.loadSessions())
    var hasUsage by mutableStateOf(false)

    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0)) // 0 sistem, 1 açık, 2 koyu, 3 AMOLED
    var accent by mutableIntStateOf(prefs.getInt("accent", 0))
    var dynamic by mutableStateOf(prefs.getBoolean("dynamic", false))
    var bgPath by mutableStateOf(prefs.getString("bg", null))
    var bgBlur by mutableStateOf(prefs.getBoolean("bgblur", true))
    var sort by mutableStateOf(
        runCatching { SortMode.valueOf(prefs.getString("sort", "MANUAL")!!) }.getOrDefault(SortMode.MANUAL)
    )

    fun setTheme(v: Int) { themeMode = v; prefs.edit().putInt("theme", v).apply() }
    @JvmName("applyAccent") fun setAccent(v: Int) { accent = v; prefs.edit().putInt("accent", v).apply() }
    @JvmName("applyDynamic") fun setDynamic(v: Boolean) { dynamic = v; prefs.edit().putBoolean("dynamic", v).apply() }
    @JvmName("applySort") fun setSort(v: SortMode) { sort = v; prefs.edit().putString("sort", v.name).apply() }

    fun sortedGames(): List<Game> = when (sort) {
        SortMode.MANUAL -> games
        SortMode.NAME -> games.sortedBy { it.name.lowercase() }
        SortMode.PLAYTIME -> games.sortedByDescending { playedMs(sessions, it.pkg, Range.ALL) }
        SortMode.RECENT -> games.sortedByDescending { it.lastPlayed }
        SortMode.ADDED -> games.sortedByDescending { it.addedAt }
    }

    private fun update(pkg: String, f: (Game) -> Game) {
        games = games.map { if (it.pkg == pkg) f(it) else it }
        store.saveGames(games)
    }

    private fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
    private fun setHidden(v: Set<String>) { prefs.edit().putStringSet("hidden", v).apply() }

    /** Yalnızca oyun olarak algılanan uygulamalar eklenir. */
    private fun addMany(list: List<Pair<String, String>>) {
        val known = games.map { it.pkg }.toSet()
        val now = System.currentTimeMillis()
        val fresh = list.filter { it.first !in known }
        if (fresh.isEmpty()) return
        games = games + fresh.mapIndexed { idx, (pkg, name) -> Game(pkg, name, addedAt = now + idx) }
        store.saveGames(games)
        // Son 7 günün geçmişini geri doldur (Android bu kadarını tutuyor)
        hasUsage = UsageSync.hasPermission(ctx)
        if (hasUsage) {
            val (s, _) = UsageSync.collect(ctx, fresh.map { it.first }.toSet(), now - 7 * 86_400_000L)
            mergeSessions(s)
        }
        fetchCovers()
    }

    /** Yüklü oyunları kendiliğinden ekler (kullanıcının listeden kaldırdıkları hariç). */
    fun autoAddGames() {
        val skip = hidden()
        addMany(detectInstalledGames(ctx).filter { it.first !in skip })
    }

    /** Listeden kaldırılmış (veya henüz eklenmemiş) oyunlar */
    fun addableGames(): List<Pair<String, String>> {
        val have = games.map { it.pkg }.toSet()
        return detectInstalledGames(ctx).filter { it.first !in have }
    }

    fun add(pkg: String, name: String) {
        // Oyun olmayan hiçbir şey eklenemez
        if (detectInstalledGames(ctx).none { it.first == pkg }) return
        setHidden(hidden() - pkg)
        addMany(listOf(pkg to name))
    }

    fun remove(pkg: String) {
        games.find { it.pkg == pkg }?.let { g ->
            g.cover?.let { File(it).delete() }
            g.autoCover?.let { File(it).delete() }
        }
        setHidden(hidden() + pkg)
        games = games.filter { it.pkg != pkg }
        sessions = sessions.filter { it.pkg != pkg }
        store.saveGames(games); store.saveSessions(sessions)
    }

    fun toggleFav(pkg: String) = update(pkg) { it.copy(favorite = !it.favorite) }

    fun move(pkg: String, delta: Int) {
        val list = games.toMutableList()
        val i = list.indexOfFirst { it.pkg == pkg }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val t = list[i]; list[i] = list[j]; list[j] = t
        games = list; store.saveGames(games)
    }

    fun launch(pkg: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        update(pkg) { it.copy(lastPlayed = System.currentTimeMillis()) }
    }

    fun setCover(pkg: String, uri: Uri) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1000) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return
            val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
            val f = File(dir, "${pkg}_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            games.find { it.pkg == pkg }?.cover?.let { File(it).delete() }
            update(pkg) { it.copy(cover = f.absolutePath) }
        }
    }

    fun clearCover(pkg: String) {
        games.find { it.pkg == pkg }?.cover?.let { File(it).delete() }
        update(pkg) { it.copy(cover = null) }
    }

    fun setBgBlur(v: Boolean) { bgBlur = v; prefs.edit().putBoolean("bgblur", v).apply() }

    /** Galeriden seçilen görseli uygulama duvar kağıdı yapar. */
    fun setBackground(uri: Uri) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outHeight / sample > 2400) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return
            val f = File(ctx.filesDir, "bg_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bgPath?.let { File(it).delete() }
            bgPath = f.absolutePath
            prefs.edit().putString("bg", bgPath).apply()
        }
    }

    fun clearBackground() {
        bgPath?.let { File(it).delete() }
        bgPath = null
        prefs.edit().remove("bg").apply()
    }

    private fun mergeSessions(newS: List<Session>) {
        val known = sessions.map { it.pkg to it.start }.toHashSet()
        val fresh = newS.filter { (it.pkg to it.start) !in known }
        if (fresh.isEmpty()) return
        sessions = sessions + fresh
        store.saveSessions(sessions)
        games = games.map { g ->
            val l = fresh.filter { it.pkg == g.pkg }.maxOfOrNull { it.end }
            if (l != null && l > g.lastPlayed) g.copy(lastPlayed = l) else g
        }
        store.saveGames(games)
    }

    /** Uygulama her öne geldiğinde çağrılır */
    fun sync() {
        autoAddGames()
        fetchCovers()
        hasUsage = UsageSync.hasPermission(ctx)
        if (!hasUsage) return
        val last = prefs.getLong("lastSync", System.currentTimeMillis() - 7 * 86_400_000L)
        val (s, next) = UsageSync.collect(ctx, games.map { it.pkg }.toSet(), last)
        prefs.edit().putLong("lastSync", next).apply()
        mergeSessions(s)
    }

    // ---------- İnternetten otomatik kapak ----------
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fetching = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(3)

    /** Kapağı olmayan oyunlar için internetten görsel çeker; internet yoksa uygulama simgesi kalır. */
    fun fetchCovers() {
        val now = System.currentTimeMillis()
        games.filter { it.cover == null && it.autoCover == null }.forEach { g ->
            if (now - prefs.getLong("tried_${g.pkg}", 0L) < 6 * 3_600_000L) return@forEach
            if (!fetching.add(g.pkg)) return@forEach
            pool.execute {
                var bytes: ByteArray? = null
                var reachable = true
                try {
                    bytes = CoverFetcher.fetch(g.name, g.pkg)
                } catch (e: Exception) {
                    reachable = false
                }
                mainHandler.post {
                    fetching.remove(g.pkg)
                    if (reachable) prefs.edit().putLong("tried_${g.pkg}", System.currentTimeMillis()).apply()
                    bytes?.let { saveAuto(g.pkg, it) }
                }
            }
        }
    }

    private fun saveAuto(pkg: String, bytes: ByteArray) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1000) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return
            val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
            val f = File(dir, "auto_${pkg}_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            games.find { it.pkg == pkg }?.autoCover?.let { File(it).delete() }
            update(pkg) { it.copy(autoCover = f.absolutePath) }
        }
    }
}
