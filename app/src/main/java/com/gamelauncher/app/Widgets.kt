package com.gamelauncher.app

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt

// ------------------------------------------------------------------ Sağlayıcılar

class GamesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, WidgetUi.games(context, mgr, it)) }
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.list)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        mgr.updateAppWidget(id, WidgetUi.games(context, mgr, id))
        mgr.notifyAppWidgetViewDataChanged(intArrayOf(id), R.id.list)
    }
}

class TempWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, WidgetUi.temps(context, mgr, it)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        mgr.updateAppWidget(id, WidgetUi.temps(context, mgr, id))
    }
}

class InfoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, WidgetUi.info(context)) }
    }
}

// ------------------------------------------------------------------ Kaydırılabilir oyun listesi

class GamesListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = GamesFactory(applicationContext)
}

private class GamesFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
    private data class Row(val pkg: String, val name: String, val time: String)

    private var rows: List<Row> = emptyList()
    private var style: WStyle = WidgetStyle.load(ctx)

    override fun onCreate() {}
    override fun onDestroy() {}

    override fun onDataSetChanged() {
        style = WidgetStyle.load(ctx)
        val store = Store(ctx)
        val sessions = store.loadSessions()
        rows = store.loadGames()
            .map { it to playedMs(sessions, it.pkg, Range.ALL) }
            .sortedByDescending { it.second }
            .map { Row(it.first.pkg, it.first.name, fmtDur(it.second)) }
    }

    override fun getCount() = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_games_item)
        val r = rows.getOrNull(position) ?: return rv
        rv.setTextViewText(R.id.name, r.name)
        rv.setTextViewText(R.id.time, r.time)
        rv.setTextColor(R.id.name, style.text)
        rv.setTextColor(R.id.time, style.accent)
        val icon = WidgetGfx.appIcon(ctx, r.pkg)
        if (icon != null) rv.setImageViewBitmap(R.id.icon, icon)
        rv.setOnClickFillInIntent(R.id.row, Intent())
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
}

// ------------------------------------------------------------------ Çizim

object WidgetUi {

    /** Uygulama açıldığında / senkronda / renk değişince tüm widget'ları yeniler. */
    fun updateAll(ctx: Context) {
        runCatching {
            val mgr = AppWidgetManager.getInstance(ctx)
            val g = mgr.getAppWidgetIds(ComponentName(ctx, GamesWidget::class.java))
            g.forEach { mgr.updateAppWidget(it, games(ctx, mgr, it)) }
            if (g.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(g, R.id.list)
            mgr.getAppWidgetIds(ComponentName(ctx, TempWidget::class.java))
                .forEach { mgr.updateAppWidget(it, temps(ctx, mgr, it)) }
            mgr.getAppWidgetIds(ComponentName(ctx, InfoWidget::class.java))
                .forEach { mgr.updateAppWidget(it, info(ctx)) }
        }
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Liste satırları için şablon (satır dokunuşları bunu kullanır) */
    private fun openAppTemplate(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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

    private fun t(v: Float?) = v?.let { String.format(Locale.US, "%.1f °C", it) } ?: "—"

    /** Widget'ın şu anki boyutu (dp). Bilinmiyorsa 0. */
    private fun sizeDp(ctx: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = mgr.getAppWidgetOptions(id)
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return w to h
    }

    /** Zemin: beyaz şekle renk ve saydamlık uygular (köşeler yuvarlak kalır). */
    private fun styleBg(rv: RemoteViews, st: WStyle) {
        rv.setInt(R.id.bg, "setColorFilter", st.bg or (0xFF shl 24))
        rv.setInt(R.id.bg, "setImageAlpha", st.alpha)
    }

    // ---------------------------------------------------------- 1) Oyunlar ve süreler

    private const val HEADER_DP = 50  // kart iç boşlukları (12 + 12) + başlık satırı (~26)
    private const val ROW_DP = 46     // widget_games_item satır yüksekliği

    fun games(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_games)
        val st = WidgetStyle.load(ctx)
        val store = Store(ctx)
        val games = store.loadGames()
        val sessions = store.loadSessions()

        styleBg(rv, st)
        rv.setTextViewText(R.id.title, "Oyunlarım • Bugün ${fmtDur(playedMs(sessions, null, Range.TODAY))}")
        rv.setTextColor(R.id.title, st.text)
        rv.setTextColor(R.id.empty, st.textDim)

        val svc = Intent(ctx, GamesListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.fromParts("widget", id.toString(), null)
        }
        rv.setRemoteAdapter(R.id.list, svc)
        rv.setEmptyView(R.id.list, R.id.empty)
        rv.setPendingIntentTemplate(R.id.list, openAppTemplate(ctx))
        rv.setOnClickPendingIntent(R.id.title, openApp(ctx))

        // Kart, oyun sayısı kadar yüksek olur; oyun çoksa widget boyunu doldurur ve liste kaydırılır.
        if (Build.VERSION.SDK_INT >= 31) {
            val need = HEADER_DP + (if (games.isEmpty()) 24 else games.size * ROW_DP) + 2
            val avail = sizeDp(ctx, mgr, id).second
            val h = if (avail > 0) min(need, avail) else need
            rv.setViewLayoutHeight(R.id.card, h.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        return rv
    }

    // ---------------------------------------------------------- 2) Sıcaklıklar (Samsung tarzı hap)

    fun temps(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_temps)
        val st = WidgetStyle.load(ctx)
        val (wd, hd) = sizeDp(ctx, mgr, id)
        val wDp = if (wd > 0) wd else 160
        val hDp = if (hd > 0) hd else 72
        val density = ctx.resources.displayMetrics.density
        // Bitmap çok büyümesin (RemoteViews sınırı): en fazla ~250 bin piksel
        val scale = min(density, sqrt(250_000f / (wDp * hDp)))
        val bmp = WidgetGfx.tempsBitmap((wDp * scale).toInt(), (hDp * scale).toInt(), st, batteryTemp(ctx), cpuTempC())
        rv.setImageViewBitmap(R.id.img, bmp)
        rv.setOnClickPendingIntent(R.id.root, refresh(ctx, id))
        return rv
    }

    // ---------------------------------------------------------- 3) Telefon özeti (RAM / depolama çubukları)

    fun info(ctx: Context): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_info)
        val st = WidgetStyle.load(ctx)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong

        styleBg(rv, st)
        rv.setTextViewText(R.id.iModel, "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
        rv.setTextViewText(R.id.iSys, "Android ${Build.VERSION.RELEASE} • SDK ${Build.VERSION.SDK_INT}")
        rv.setTextViewText(R.id.iTemp, "Batarya ${t(batteryTemp(ctx))} • İşlemci ${t(cpuTempC())}")

        val ramUsed = mi.totalMem - mi.availMem
        val stoUsed = total - free
        rv.setTextViewText(R.id.iRamV, "${gb(ramUsed)} / ${gb(mi.totalMem)}")
        rv.setTextViewText(R.id.iStoV, "${gb(stoUsed)} / ${gb(total)}")
        rv.setImageViewBitmap(R.id.iRamBar, WidgetGfx.barBitmap(ramUsed.toFloat() / mi.totalMem.coerceAtLeast(1), st))
        rv.setImageViewBitmap(R.id.iStoBar, WidgetGfx.barBitmap(stoUsed.toFloat() / total.coerceAtLeast(1), st))

        rv.setTextColor(R.id.iModel, st.text)
        rv.setTextColor(R.id.iSys, st.textDim)
        rv.setTextColor(R.id.iRamL, st.text)
        rv.setTextColor(R.id.iStoL, st.text)
        rv.setTextColor(R.id.iRamV, st.textDim)
        rv.setTextColor(R.id.iStoV, st.textDim)
        rv.setTextColor(R.id.iTemp, st.accent)
        rv.setOnClickPendingIntent(R.id.root, openApp(ctx))
        return rv
    }
}
