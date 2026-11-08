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
        val show = cur != null && cur in gamePkgs
        if (show && handleView == null && panelView == null) addHandle()
        if (!show) { removeHandle(); closePanel() }
    }

    // ------------------------------------------------ Tutamaç (sol kenardaki çizgi)

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    private fun addHandle() {
        val d = density
        val v = FrameLayout(this)
        val bar = View(this).apply {
            background = GradientDrawable().apply {
                setColor(0xB3FFFFFF.toInt())
                cornerRadius = 4 * d
            }
        }
        v.addView(bar, FrameLayout.LayoutParams((5 * d).toInt(), (84 * d).toInt(), Gravity.START or Gravity.CENTER_VERTICAL))

        var downX = 0f
        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> downX = ev.rawX
                MotionEvent.ACTION_MOVE -> if (ev.rawX - downX > 24 * d) openPanel()
                MotionEvent.ACTION_UP -> if (abs(ev.rawX - downX) < 12 * d) openPanel()
            }
            true
        }
        val w = (24 * d).toInt()
        val h = (150 * d).toInt()
        val lp = overlayParams(w, h, 0).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        runCatching {
            wm.addView(v, lp)
            handleView = v
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

    private fun openPanel() {
        if (panelView != null) return
        removeHandle()
        val d = density
        val dm = resources.displayMetrics

        val games = Store(this).loadGames().filter { it.pkg != fg }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt())
        }
        val pm = packageManager
        games.forEach { g ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
                isClickable = true
                setOnClickListener { launchGame(g.pkg, true) }
                setOnLongClickListener { launchGame(g.pkg, false); true }
            }
            val icon = ImageView(this).apply {
                runCatching { setImageDrawable(pm.getApplicationIcon(g.pkg)) }
            }
            item.addView(icon, LinearLayout.LayoutParams((46 * d).toInt(), (46 * d).toInt()))
            item.addView(TextView(this).apply {
                text = g.name
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams((66 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            list.addView(item)
        }
        if (games.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Başka oyun yok"
                setTextColor(0xB3FFFFFF.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
            })
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
            background = GradientDrawable().apply {
                setColor(0xF2201F27.toInt())
                val r = 24 * d
                cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            }
        }
        val panelW = (82 * d).toInt()
        val panelH = (dm.heightPixels * 0.68f).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { closePanel() }
            addView(scroll, FrameLayout.LayoutParams(panelW, panelH, Gravity.START or Gravity.CENTER_VERTICAL))
        }
        val lp = overlayParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        runCatching {
            wm.addView(root, lp)
            panelView = root
            scroll.translationX = -panelW.toFloat()
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
        if (popup) {
            val dm = resources.displayMetrics
            val w = (dm.widthPixels * 0.8f).toInt()
            val h = (dm.heightPixels * 0.55f).toInt()
            val l = (dm.widthPixels - w) / 2
            val t = (dm.heightPixels - h) / 4
            val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h))
            runCatching { startActivity(i, opts.toBundle()) }.onFailure { runCatching { startActivity(i) } }
        } else {
            runCatching { startActivity(i) }
        }
    }
}
