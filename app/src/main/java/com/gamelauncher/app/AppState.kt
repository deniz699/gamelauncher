package com.gamelauncher.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

class AppState(private val ctx: Context) {
    private val store = Store(ctx)
    private val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var games by mutableStateOf(store.loadGames())
    var sessions by mutableStateOf(store.loadSessions())
    var hasUsage by mutableStateOf(false)

    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0)) // 0 sistem, 1 açık, 2 koyu, 3 AMOLED
    var accent by mutableIntStateOf(prefs.getInt("accent", 0))
    var dynamic by mutableStateOf(prefs.getBoolean("dynamic", false))
    var sort by mutableStateOf(
        runCatching { SortMode.valueOf(prefs.getString("sort", "MANUAL")!!) }.getOrDefault(SortMode.MANUAL)
    )

    fun setTheme(v: Int) { themeMode = v; prefs.edit().putInt("theme", v).apply() }
    fun setAccent(v: Int) { accent = v; prefs.edit().putInt("accent", v).apply() }
    fun setDynamic(v: Boolean) { dynamic = v; prefs.edit().putBoolean("dynamic", v).apply() }
    fun setSort(v: SortMode) { sort = v; prefs.edit().putString("sort", v.name).apply() }

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

    fun add(pkg: String, name: String) {
        if (games.any { it.pkg == pkg }) return
        games = games + Game(pkg, name, addedAt = System.currentTimeMillis())
        store.saveGames(games)
        // Son 7 günün geçmişini geri doldur (Android bu kadarını tutuyor)
        hasUsage = UsageSync.hasPermission(ctx)
        if (hasUsage) {
            val (s, _) = UsageSync.collect(ctx, setOf(pkg), System.currentTimeMillis() - 7 * 86_400_000L)
            mergeSessions(s)
        }
    }

    fun remove(pkg: String) {
        games.find { it.pkg == pkg }?.cover?.let { File(it).delete() }
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
        hasUsage = UsageSync.hasPermission(ctx)
        if (!hasUsage) return
        val last = prefs.getLong("lastSync", System.currentTimeMillis() - 7 * 86_400_000L)
        val (s, next) = UsageSync.collect(ctx, games.map { it.pkg }.toSet(), last)
        prefs.edit().putLong("lastSync", next).apply()
        mergeSessions(s)
    }
}
