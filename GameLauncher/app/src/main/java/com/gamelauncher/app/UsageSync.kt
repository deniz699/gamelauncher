package com.gamelauncher.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

object UsageSync {

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * [from] ile şimdi arasındaki ön plan olaylarından oturumlar çıkarır.
     * Dönen ikinci değer = bir sonraki senkronizasyonun başlangıcı
     * (hâlâ açık olan oyun varsa onun başlangıcı, yoksa şu an).
     */
    fun collect(ctx: Context, pkgs: Set<String>, from: Long): Pair<List<Session>, Long> {
        if (pkgs.isEmpty()) return emptyList<Session>() to System.currentTimeMillis()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val raw = ArrayList<Session>()

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val p = e.packageName
            if (p !in pkgs) continue
            when (e.eventType) {
                1 -> if (!open.containsKey(p)) open[p] = e.timeStamp          // ACTIVITY_RESUMED
                2, 23 -> open.remove(p)?.let { raw += Session(p, it, e.timeStamp) } // PAUSED / STOPPED
            }
        }

        // Oyun içi ekran geçişlerinde oluşan kısa boşlukları birleştir
        val merged = ArrayList<Session>()
        raw.sortedWith(compareBy({ it.pkg }, { it.start })).forEach { s ->
            val last = merged.lastOrNull()
            if (last != null && last.pkg == s.pkg && s.start - last.end < 15_000) {
                merged[merged.size - 1] = last.copy(end = maxOf(last.end, s.end))
            } else merged += s
        }
        val result = merged.filter { it.end - it.start >= 5_000 }
        val next = open.values.minOrNull() ?: now
        return result to next
    }
}
