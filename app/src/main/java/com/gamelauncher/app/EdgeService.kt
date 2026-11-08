package com.gamelauncher.app

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Choreographer
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
import java.io.File
import java.util.Locale
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
    private var panelOwner: String? = null // panelin açıldığı oyun; pencere kapanınca oyuna dönülmüş sayılır
    private var panelLaunchTs = 0L
    private var lastCleaned: String? = null   // otomatik RAM temizliği yapılan son oyun
    private val density get() = resources.displayMetrics.density

    // Oyun içi göstergeler
    private var hudView: TextView? = null
    private var hudSig = ""
    private var frames = 0
    private var hudStart = 0L
    private var fpsCounting = false
    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frames++
            if (fpsCounting) Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private val hudTick = object : Runnable {
        override fun run() {
            updateHud()
            handler.postDelayed(this, 1000)
        }
    }

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
        stopHud()
        removeHandle()
        closePanel()
        super.onDestroy()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        // Eski (sesli/görünür) kanal kaldırılır; yeni kanal en düşük önemde, kilit ekranında gizli
        runCatching { nm.deleteNotificationChannel("edge") }
        nm.createNotificationChannel(
            NotificationChannel("edge_quiet", "Arka plan hizmeti", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
        val n = Notification.Builder(this, "edge_quiet")
            .setContentTitle("Game Launcher")
            .setSmallIcon(android.R.drawable.ic_menu_more)
            .setVisibility(Notification.VISIBILITY_SECRET)
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
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == fg) {
                    // Panelden açılan pencere kapandı: oyun zaten arkada açıktı ve sistem ona yeni bir
                    // "ön plana geldi" olayı göndermez; bu yüzden oyuna dönüldüğünü biz varsayarız.
                    // (Ana ekrana çıkıldıysa hemen ardından gelen launcher olayı bunu düzeltir.)
                    fg = if (e.packageName == panelApp && panelOwner != null) panelOwner else null
                }
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
        if (cur != null && cur !in gamePkgs && cur != panelApp) { panelApp = null; panelOwner = null }
        // Oyun yeniden ön plandaysa (ve açılış gecikmesi geçtiyse) panel oturumu bitmiştir
        if (cur != null && cur in gamePkgs && System.currentTimeMillis() - panelLaunchTs > 4000) {
            panelApp = null; panelOwner = null
        }
        syncHud()
        // Oyun açılınca bir kez RAM temizle (oyunun kendisi korunur)
        if (cur != null && cur in gamePkgs) {
            if (EdgePrefs.autoClean(this) && lastCleaned != cur) { lastCleaned = cur; Booster.clean(this, setOf(cur)) { } }
        } else if (cur != null && cur != panelApp) {
            lastCleaned = null
        }
        val show = EdgePrefs.edgeOn(this) && cur != null && (cur in gamePkgs || cur == panelApp)
        if (!EdgePrefs.edgeOn(this) && !EdgePrefs.hudAny(this) && !EdgePrefs.autoClean(this)) { stopSelf(); return }
        if (show && handleView != null && handleSig != edgeSig()) removeHandle()   // ayar değişti: yeniden çiz
        if (show && handleView == null && panelView == null) addHandle()
        if (!show) { removeHandle(); closePanel() }
    }

    // ------------------------------------------------ Oyun içi göstergeler (FPS / pil / CPU)

    private fun syncHud() {
        val cur = fg
        val inGame = cur != null && cur in gamePkgs
        if (inGame && EdgePrefs.hudAny(this)) {
            if (hudView != null && hudSig != hudSignature()) stopHud()
            if (hudView == null) startHud()
        } else {
            stopHud()
        }
    }

    /** Panelin üstündeki küçük aç/kapa düğmesi (FPS / Pil / CPU). */
    private fun hudToggle(label: String, get: () -> Boolean, set: (Boolean) -> Unit): TextView {
        val d = density
        val tv = TextView(this)
        fun paint() {
            val on = get()
            tv.setTextColor(if (on) 0xFF201F27.toInt() else 0xFFFFFFFF.toInt())
            tv.background = GradientDrawable().apply {
                setColor(if (on) 0xFFBFA8FF.toInt() else 0x33FFFFFF)
                cornerRadius = 12 * d
            }
        }
        tv.apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { set(!get()); paint(); syncHud() }
        }
        paint()
        return tv
    }

    private fun hudSignature() = "${EdgePrefs.hudPos(this)}/${EdgePrefs.hudFps(this)}/${EdgePrefs.hudBat(this)}/${EdgePrefs.hudCpu(this)}"

    private fun startHud() {
        val d = density
        val tv = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f * d, 0f, 0f, 0xFF000000.toInt())   // arka plan yok; okunurluk için gölge
            setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), 0)
        }
        val pos = EdgePrefs.hudPos(this)
        tv.gravity = when (pos) { 1 -> Gravity.CENTER_HORIZONTAL; 2 -> Gravity.END; else -> Gravity.START }
        val lp = overlayParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            gravity = Gravity.TOP or when (pos) { 1 -> Gravity.CENTER_HORIZONTAL; 2 -> Gravity.END; else -> Gravity.START }
        }
        runCatching {
            wm.addView(tv, lp)
            hudView = tv
            hudSig = hudSignature()
            frames = 0
            hudStart = System.currentTimeMillis()
            if (EdgePrefs.hudFps(this)) {
                fpsCounting = true
                Choreographer.getInstance().postFrameCallback(frameCb)
            }
            handler.post(hudTick)
        }
    }

    private fun stopHud() {
        if (hudView == null) return
        handler.removeCallbacks(hudTick)
        fpsCounting = false
        Choreographer.getInstance().removeFrameCallback(frameCb)
        hudView?.let { runCatching { wm.removeView(it) } }
        hudView = null
    }

    /** Bazı cihazlarda ekran sürücüsü gerçek kare hızını dosyaya yazar; okunabiliyorsa onu kullanırız. */
    private fun sysfsFps(): Float? {
        val paths = listOf(
            "/sys/class/drm/sde-crtc-0/measured_fps",
            "/sys/class/graphics/fb0/measured_fps"
        )
        for (p in paths) {
            val t = runCatching { File(p).readText() }.getOrNull() ?: continue
            val m = Regex("fps:?\\s*([0-9]+(?:\\.[0-9]+)?)").find(t) ?: Regex("([0-9]+(?:\\.[0-9]+)?)").find(t) ?: continue
            return m.groupValues[1].toFloatOrNull()
        }
        return null
    }

    private fun updateHud() {
        val tv = hudView ?: return
        val lines = ArrayList<String>()
        if (EdgePrefs.hudFps(this)) {
            val now = System.currentTimeMillis()
            val el = (now - hudStart).coerceAtLeast(1)
            val fps = sysfsFps() ?: (frames * 1000f / el)
            frames = 0
            hudStart = now
            lines += "FPS: ${fps.roundToInt()}"
        }
        if (EdgePrefs.hudBat(this)) {
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val t = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            lines += "PİL: " + (if (t != Int.MIN_VALUE) String.format(Locale.US, "%.1f°C", t / 10f) else "--")
        }
        if (EdgePrefs.hudCpu(this)) {
            val c = cpuTempC()
            lines += "CPU: " + (c?.let { String.format(Locale.US, "%.0f°C", it) } ?: "--")
        }
        tv.text = lines.joinToString("\n")
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

        // Panelde yalnızca Ayarlar > Kenar paneli > "Uygulama seç" ile eklenenler görünür
        val pm = packageManager
        val apps = EdgePrefs.apps(this)
            .filter { it != fg && pm.getLaunchIntentForPackage(it) != null }
            .map { pkg -> pkg to (runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt())
        }
        // RAM temizle düğmesi
        val cleanLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (30 * d).toInt()).apply {
            setMargins((4 * d).toInt(), (3 * d).toInt(), (4 * d).toInt(), (3 * d).toInt())
        }
        list.addView(TextView(this).apply {
            text = "⚡ Temizle"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFF201F27.toInt())
            background = GradientDrawable().apply { setColor(0xFFFFD54F.toInt()); cornerRadius = 12 * d }
            isClickable = true
            setOnClickListener {
                Booster.clean(this@EdgeService, setOfNotNull(fg)) {
                    android.widget.Toast.makeText(this@EdgeService, it, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }, cleanLp)
        // Oyun içi bilgi düğmeleri
        val tl = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (30 * d).toInt()).apply {
            setMargins((4 * d).toInt(), (3 * d).toInt(), (4 * d).toInt(), (3 * d).toInt())
        }
        list.addView(hudToggle("FPS", { EdgePrefs.hudFps(this) }, { EdgePrefs.setHudFps(this, it) }), tl)
        list.addView(hudToggle("Pil", { EdgePrefs.hudBat(this) }, { EdgePrefs.setHudBat(this, it) }), tl)
        list.addView(hudToggle("CPU", { EdgePrefs.hudCpu(this) }, { EdgePrefs.setHudCpu(this, it) }), tl)
        list.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * d).toInt()).apply {
                setMargins((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
            })
        apps.forEach { (pkg, name) -> list.addView(appItem(pkg, name)) }
        if (apps.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Panele uygulama eklenmemiş"
                setTextColor(0xB3FFFFFF.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
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
        panelOwner = fg?.takeIf { it in gamePkgs }
        panelLaunchTs = System.currentTimeMillis()
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
