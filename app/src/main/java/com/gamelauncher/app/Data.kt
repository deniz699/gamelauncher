package com.gamelauncher.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale

data class Game(
    val pkg: String,
    val name: String,
    val cover: String? = null,
    val favorite: Boolean = false,
    val addedAt: Long,
    val lastPlayed: Long = 0L
)

data class Session(val pkg: String, val start: Long, val end: Long)

enum class SortMode(val label: String) {
    MANUAL("Kendi sıralamam"),
    NAME("İsim (A-Z)"),
    PLAYTIME("Oynama süresi"),
    RECENT("Son oynanan"),
    ADDED("Eklenme tarihi")
}

enum class Range(val label: String) {
    TODAY("Bugün"), WEEK("Bu hafta"), MONTH("Bu ay"), YEAR("Bu yıl"), ALL("Toplam")
}

class Store(ctx: Context) {
    private val gamesFile = File(ctx.filesDir, "games.json")
    private val sessionsFile = File(ctx.filesDir, "sessions.json")

    fun loadGames(): List<Game> = runCatching {
        val a = JSONArray(gamesFile.readText())
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Game(
                pkg = o.getString("pkg"),
                name = o.getString("name"),
                cover = o.optString("cover").ifEmpty { null },
                favorite = o.optBoolean("fav"),
                addedAt = o.optLong("added"),
                lastPlayed = o.optLong("last")
            )
        }
    }.getOrDefault(emptyList())

    fun saveGames(list: List<Game>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("pkg", it.pkg).put("name", it.name).put("cover", it.cover ?: "")
                    .put("fav", it.favorite).put("added", it.addedAt).put("last", it.lastPlayed)
            )
        }
        gamesFile.writeText(a.toString())
    }

    fun loadSessions(): List<Session> = runCatching {
        val a = JSONArray(sessionsFile.readText())
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Session(o.getString("p"), o.getLong("s"), o.getLong("e"))
        }
    }.getOrDefault(emptyList())

    fun saveSessions(list: List<Session>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("p", it.pkg).put("s", it.start).put("e", it.end)) }
        sessionsFile.writeText(a.toString())
    }
}

// ---------- Süre hesaplama ----------

fun rangeStart(r: Range): Long {
    val z = ZoneId.systemDefault()
    val today = LocalDate.now(z)
    val d = when (r) {
        Range.TODAY -> today
        Range.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Range.MONTH -> today.withDayOfMonth(1)
        Range.YEAR -> today.withDayOfYear(1)
        Range.ALL -> return 0L
    }
    return d.atStartOfDay(z).toInstant().toEpochMilli()
}

/** pkg == null ise tüm oyunların toplamı */
fun playedMs(sessions: List<Session>, pkg: String?, r: Range): Long {
    val from = rangeStart(r)
    var sum = 0L
    for (s in sessions) {
        if (pkg != null && s.pkg != pkg) continue
        val st = maxOf(s.start, from)
        if (s.end > st) sum += s.end - st
    }
    return sum
}

fun fmtDur(ms: Long): String {
    val m = ms / 60_000
    val h = m / 60
    return when {
        m < 1 -> "0 dk"
        h == 0L -> "$m dk"
        else -> "$h sa ${m % 60} dk"
    }
}

fun fmtDate(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr")).format(Date(ms))
