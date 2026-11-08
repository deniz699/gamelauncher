package com.gamelauncher.app

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

class GamesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, WidgetUi.games(context)) }
    }
}

class TempWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, WidgetUi.temps(context, it)) }
    }
}

class InfoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, WidgetUi.info(context)) }
    }
}

object WidgetUi {

    /** Uygulama açıldığında / senkronda tüm widget'ları yeniler. */
    fun updateAll(ctx: Context) {
        runCatching {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, GamesWidget::class.java)).forEach { mgr.updateAppWidget(it, games(ctx)) }
            mgr.getAppWidgetIds(ComponentName(ctx, TempWidget::class.java)).forEach { mgr.updateAppWidget(it, temps(ctx, it)) }
            mgr.getAppWidgetIds(ComponentName(ctx, InfoWidget::class.java)).forEach { mgr.updateAppWidget(it, info(ctx)) }
        }
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun refresh(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, TempWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun batteryIntent(ctx: Context): Intent? =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun batteryTemp(ctx: Context): Float? {
        val t = batteryIntent(ctx)?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        return if (t == Int.MIN_VALUE) null else t / 10f
    }

    private fun batteryLevel(ctx: Context): Int {
        val i = batteryIntent(ctx) ?: return -1
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0) -1 else l * 100 / s
    }

    private fun t(v: Float?) = v?.let { String.format(Locale.US, "%.1f °C", it) } ?: "—"

    // ---------------------------------------------------------- 1) Oyunlar ve süreler

    fun games(ctx: Context): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_games)
        val store = Store(ctx)
        val games = store.loadGames()
        val sessions = store.loadSessions()
        val rows = listOf(
            Triple(R.id.g1r, R.id.g1n, R.id.g1t), Triple(R.id.g2r, R.id.g2n, R.id.g2t),
            Triple(R.id.g3r, R.id.g3n, R.id.g3t), Triple(R.id.g4r, R.id.g4n, R.id.g4t),
            Triple(R.id.g5r, R.id.g5n, R.id.g5t), Triple(R.id.g6r, R.id.g6n, R.id.g6t)
        )
        val top = games.map { it to playedMs(sessions, it.pkg, Range.ALL) }.sortedByDescending { it.second }.take(rows.size)
        rv.setTextViewText(R.id.title, "Oyunlarım • Bugün ${fmtDur(playedMs(sessions, null, Range.TODAY))}")
        rv.setViewVisibility(R.id.empty, if (top.isEmpty()) View.VISIBLE else View.GONE)
        rows.forEachIndexed { i, (r, n, tm) ->
            if (i < top.size) {
                rv.setViewVisibility(r, View.VISIBLE)
                rv.setTextViewText(n, top[i].first.name)
                rv.setTextViewText(tm, fmtDur(top[i].second))
            } else rv.setViewVisibility(r, View.GONE)
        }
        rv.setOnClickPendingIntent(R.id.root, openApp(ctx))
        return rv
    }

    // ---------------------------------------------------------- 2) Sıcaklıklar

    fun temps(ctx: Context, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_temps)
        rv.setTextViewText(R.id.bat, t(batteryTemp(ctx)))
        rv.setTextViewText(R.id.cpu, t(cpuTempC()))
        val lv = batteryLevel(ctx)
        rv.setTextViewText(R.id.lvl, if (lv >= 0) "Şarj %$lv" else "")
        rv.setOnClickPendingIntent(R.id.root, refresh(ctx, id))
        return rv
    }

    // ---------------------------------------------------------- 3) Telefon + son oynananlar + sıcaklık

    fun info(ctx: Context): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_info)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong

        rv.setTextViewText(R.id.iModel, "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
        rv.setTextViewText(R.id.iSys, "Android ${Build.VERSION.RELEASE} • SDK ${Build.VERSION.SDK_INT}")
        rv.setTextViewText(R.id.iMem, "RAM ${gb(mi.totalMem)} (boş ${gb(mi.availMem)}) • Depolama ${gb(total)} (boş ${gb(free)})")
        rv.setTextViewText(R.id.iTemp, "Batarya ${t(batteryTemp(ctx))} • İşlemci ${t(cpuTempC())}")

        val store = Store(ctx)
        val sessions = store.loadSessions()
        val recent = store.loadGames().filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(3)
        val rows = listOf(
            Triple(R.id.i1r, R.id.i1n, R.id.i1t), Triple(R.id.i2r, R.id.i2n, R.id.i2t), Triple(R.id.i3r, R.id.i3n, R.id.i3t)
        )
        rv.setViewVisibility(R.id.iEmpty, if (recent.isEmpty()) View.VISIBLE else View.GONE)
        rows.forEachIndexed { i, (r, n, tm) ->
            if (i < recent.size) {
                rv.setViewVisibility(r, View.VISIBLE)
                rv.setTextViewText(n, recent[i].name)
                rv.setTextViewText(tm, fmtDur(playedMs(sessions, recent[i].pkg, Range.ALL)))
            } else rv.setViewVisibility(r, View.GONE)
        }
        rv.setOnClickPendingIntent(R.id.root, openApp(ctx))
        return rv
    }
}
