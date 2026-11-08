package com.gamelauncher.app

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Oyun içindeyken ekranın solunda küçük bir çizgi (tutamaç) gösterir.
 * Dokununca / sağa kaydırınca oyunlarına kısayol paneli açılır.
 */
class EdgeService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var handleView: View? = null
    private var panelView: View? = null
    private var lastEvent = 0L
    private var fg: String? = null
    private var gamePkgs: Set<String> = emptySet()
    private var counter = 0
    private var handleSig = ""
    private var panelApp: String? = null   // panelden açılan uygulama; üstünde durduğu sürece çizgi kalır
    private val density get() = resources.displayMetrics.density

    private val poll = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, 1200)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        lastEvent = System.currentTimeMillis() - 10 * 60_000L
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeHandle()
        closePanel()
        super.onDestroy()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("edge", "Kenar paneli", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "edge")
            .setContentTitle("Oyun kenar paneli açık")
            .setContentText("Oyundayken solda çıkan çizgiye dokun")
            .setSmallIcon(android.R.drawable.ic_menu_more)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ------------------------------------------------ Ön plandaki uygulamayı izle

    @Suppress("DEPRECATION")
    private fun pollForeground() {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(lastEvent, now) ?: return
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> fg = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == fg) fg = null
            }
        }
        lastEvent = now - 1000
    }

    private fun step() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        if (counter++ % 8 == 0) gamePkgs = Store(this).loadGames().map { it.pkg }.toSet()
        runCatching { pollForeground() }
        val cur = fg
        // Panelden açılan uygulama ön plana geçene kadar beklenir; başka bir uygulamaya geçilirse sıfırlanır
        if (cur != null && cur !in gamePkgs && cur != panelApp) panelApp = null
        val show = cur != null && (cur in gamePkgs || cur == panelApp)
        if (show && handleView != null && handleSig != edgeSig()) removeHandle()   // ayar değişti: yeniden çiz
        if (show && handleView == null && panelView == null) addHandle()
        if (!show) { removeHandle(); closePanel() }
    }

    // ------------------------------------------------ Tutamaç (kenardaki çizgi)

    private fun edgeSig() = "${EdgePrefs.side(this)}/${EdgePrefs.lenDp(this)}/${EdgePrefs.yFrac(this)}"

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    private fun sideGravity(side: Int) = if (side == 1) Gravity.END else Gravity.START

    private fun addHandle() {
        val d = density
        val (sw, sh) = screenSize()
        var side = EdgePrefs.side(this)
        val barLen = (EdgePrefs.lenDp(this) * d).toInt()
        val w = (28 * d).toInt()
        val h = barLen + (36 * d).toInt()          // çizginin üstünde/altında tutması kolay boşluk

        val v = FrameLayout(this)
        val barBg = GradientDrawable().apply {
            setColor(0xB3FFFFFF.toInt())
            cornerRadius = 4 * d
        }
        val bar = View(this).apply { background = barBg }
        val barLp = FrameLayout.LayoutParams((5 * d).toInt(), barLen, sideGravity(side) or Gravity.CENTER_VERTICAL)
        v.addView(bar, barLp)

        val lp = overlayParams(
            w, h,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or sideGravity(side)
            y = (EdgePrefs.yFrac(this@EdgeService) * sh - h / 2f).roundToInt().coerceIn(0, (sh - h).coerceAtLeast(0))
        }

        var downX = 0f
        var downY = 0f
        var moving = false
        val longPress = Runnable {
            moving = true
            barBg.setColor(0xFFBFA8FF.toInt())
            bar.scaleX = 1.8f
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        fun place(ev: MotionEvent) {
            val newSide = if (ev.rawX > sw / 2f) 1 else 0
            if (newSide != side) {
                side = newSide
                lp.gravity = Gravity.TOP or sideGravity(side)
                barLp.gravity = sideGravity(side) or Gravity.CENTER_VERTICAL
                bar.layoutParams = barLp
            }
            lp.y = (ev.rawY - h / 2f).roundToInt().coerceIn(0, (sh - h).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(v, lp) }
        }

        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; moving = false
                    handler.postDelayed(longPress, 380)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (moving) {
                        place(ev)
                    } else {
                        val dx = ev.rawX - downX
                        val inward = if (side == 1) -dx else dx
                        if (abs(dx) > 10 * d || abs(ev.rawY - downY) > 10 * d) handler.removeCallbacks(longPress)
                        if (inward > 24 * d) { handler.removeCallbacks(longPress); openPanel() }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (moving) {
                        moving = false
                        EdgePrefs.setSide(this, side)
                        EdgePrefs.setYFrac(this, ((lp.y + h / 2f) / sh).coerceIn(0.05f, 0.95f))
                        handleSig = edgeSig()
                        barBg.setColor(0xB3FFFFFF.toInt())
                        bar.scaleX = 1f
                    } else if (ev.actionMasked == MotionEvent.ACTION_UP &&
                        abs(ev.rawX - downX) < 12 * d && abs(ev.rawY - downY) < 12 * d
                    ) {
                        openPanel()
                    }
                }
            }
            true
        }

        runCatching {
            wm.addView(v, lp)
            handleView = v
            handleSig = edgeSig()
            if (Build.VERSION.SDK_INT >= 29) {
                v.post { v.systemGestureExclusionRects = listOf(Rect(0, 0, w, h)) }
            }
        }
    }

    private fun removeHandle() {
        handleView?.let { runCatching { wm.removeView(it) } }
        handleView = null
    }

    // ------------------------------------------------ Kısayol paneli

    private fun appItem(pkg: String, name: String): LinearLayout {
        val d = density
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            isClickable = true
            setOnClickListener { launchGame(pkg, true) }
            setOnLongClickListener { launchGame(pkg, false); true }
        }
        val icon = ImageView(this).apply {
            runCatching { setImageDrawable(packageManager.getApplicationIcon(pkg)) }
        }
        item.addView(icon, LinearLayout.LayoutParams((46 * d).toInt(), (46 * d).toInt()))
        item.addView(TextView(this).apply {
            text = name
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams((66 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        return item
    }

    private fun openPanel() {
        if (panelView != null) return
        val d = density
        val (sw, sh) = screenSize()
        val side = EdgePrefs.side(this)
        val centerY = EdgePrefs.yFrac(this) * sh
        removeHandle()

        val games = Store(this).loadGames().filter { it.pkg != fg }
        val gamePkgs = games.map { it.pkg }.toSet()
        val pm = packageManager
        val extra = EdgePrefs.apps(this)
            .filter { it != fg && it !in gamePkgs && pm.getLaunchIntentForPackage(it) != null }
            .map { pkg -> pkg to (runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt())
        }
        games.forEach { list.addView(appItem(it.pkg, it.name)) }
        if (games.isNotEmpty() && extra.isNotEmpty()) {
            list.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) },
                LinearLayout.LayoutParams((40 * d).toInt(), (1 * d).toInt()).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = (4 * d).toInt(); bottomMargin = (4 * d).toInt()
                })
        }
        extra.forEach { (pkg, name) -> list.addView(appItem(pkg, name)) }
        if (games.isEmpty() && extra.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Başka oyun yok"
                setTextColor(0xB3FFFFFF.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
            })
        }

        val panelW = (82 * d).toInt()
        list.measure(
            View.MeasureSpec.makeMeasureSpec(panelW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val panelH = list.measuredHeight.coerceIn((120 * d).toInt(), (sh * 0.8f).toInt())
        val panelTop = (centerY - panelH / 2f).roundToInt().coerceIn(0, (sh - panelH).coerceAtLeast(0))

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
            background = GradientDrawable().apply {
                setColor(0xF2201F27.toInt())
                val r = 24 * d
                cornerRadii = if (side == 1) floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                else floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { closePanel() }
            addView(scroll, FrameLayout.LayoutParams(panelW, panelH, Gravity.TOP or sideGravity(side)).apply {
                topMargin = panelTop
            })
        }
        val lp = overlayParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        runCatching {
            wm.addView(root, lp)
            panelView = root
            val off = panelW.toFloat() * (if (side == 1) 1 else -1)
            scroll.translationX = off
            root.alpha = 0f
            root.animate().alpha(1f).setDuration(160).start()
            scroll.animate().translationX(0f).setDuration(220).start()
        }
    }

    private fun closePanel() {
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
    }

    /** popup = true: açılır pencere (cihaz serbest pencere destekliyorsa), değilse normal açılır. */
    private fun launchGame(pkg: String, popup: Boolean) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        closePanel()
        panelApp = pkg
        if (popup) {
            // Kare pencere: kısa kenarın %72'si, ekranın ortasında (oyun yatay olsa da kare kalır)
            val (sw, sh) = screenSize()
            val side = (minOf(sw, sh) * 0.72f).toInt()
            val l = (sw - side) / 2
            val t = (sh - side) / 2
            val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + side, t + side))
            runCatching { startActivity(i, opts.toBundle()) }.onFailure { runCatching { startActivity(i) } }
        } else {
            runCatching { startActivity(i) }
        }
    }
}
