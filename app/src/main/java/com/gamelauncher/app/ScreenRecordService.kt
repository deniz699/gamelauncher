package com.gamelauncher.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Ekran kaydı (yalnızca görüntü, ses yok; böylece mikrofon izni gerekmez).
 * Kayıt Movies/GameLauncher klasörüne .mp4 olarak kaydedilir.
 */
class ScreenRecordService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var file: File? = null
    private var finishing = false
    private val projCb = object : MediaProjection.Callback() {
        override fun onStop() { handler.post { finish() } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish(); return START_NOT_STICKY }
        if (recording) return START_NOT_STICKY
        startFg()
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            begin(code, data)
        } catch (e: Exception) {
            Toast.makeText(this, "Ekran kaydı başlatılamadı: ${e.message.orEmpty().take(60)}", Toast.LENGTH_LONG).show()
            cleanup(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rec", "Ekran kaydı", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "rec")
            .setContentTitle("Ekran kaydediliyor")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Durdur", stop
                ).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(2, n)
        }
    }

    private fun begin(code: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(code, data)
        // Android 14+: görüntü oluşturmadan önce geri çağırma kaydedilmeli
        proj.registerCallback(projCb, handler)
        projection = proj

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val (sw, sh) = if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        }
        // Uzun kenarı en çok 1920 px yap, boyutları çift sayıya yuvarla (H.264 gereği)
        val k = min(1f, 1920f / max(sw, sh))
        val w = ((sw * k).toInt() / 2) * 2
        val h = ((sh * k).toInt() / 2) * 2

        val name = "GameLauncher_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        rec.setVideoSize(w, h)
        rec.setVideoFrameRate(30)
        rec.setVideoEncodingBitRate(8_000_000)
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GameLauncher")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val u = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
                ?: throw IllegalStateException("Dosya oluşturulamadı")
            uri = u
            pfd = contentResolver.openFileDescriptor(u, "w")
            rec.setOutputFile(pfd!!.fileDescriptor)
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            val f = File(dir, name)
            file = f
            rec.setOutputFile(f.absolutePath)
        }
        rec.prepare()
        recorder = rec
        display = proj.createVirtualDisplay(
            "GameLauncherRec", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, null
        )
        rec.start()
        recording = true
        instance = this
        Toast.makeText(this, "Ekran kaydı başladı", Toast.LENGTH_SHORT).show()
    }

    /** Kaydı bitirir ve dosyayı galeriye açar. Birden çok kez çağrılabilir. */
    private fun finish() {
        if (finishing) return
        finishing = true
        val ok = cleanup(true)
        if (ok) Toast.makeText(this, "Kayıt kaydedildi: Movies/GameLauncher", Toast.LENGTH_LONG).show()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** true: kayıt dosyası sağlam kaydedildi. */
    private fun cleanup(save: Boolean): Boolean {
        val wasRecording = recording
        recording = false
        if (instance === this) instance = null
        var ok = false
        runCatching { if (wasRecording) { recorder?.stop(); ok = true } }   // çok kısa kayıtlarda stop() hata verir
        runCatching { recorder?.release() }
        runCatching { display?.release() }
        runCatching { projection?.unregisterCallback(projCb); projection?.stop() }
        runCatching { pfd?.close() }
        val u = uri
        if (u != null) {
            if (ok && save) {
                runCatching {
                    contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                }
            } else {
                runCatching { contentResolver.delete(u, null, null) }
            }
        }
        val f = file
        if (f != null && !(ok && save)) runCatching { f.delete() }
        recorder = null; display = null; projection = null; pfd = null; uri = null; file = null
        return ok && save
    }

    override fun onDestroy() {
        if (!finishing) { finishing = true; cleanup(true) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.gamelauncher.app.STOP_RECORD"

        @Volatile var recording = false
        @Volatile private var instance: ScreenRecordService? = null

        /** Kenar panelinden kaydı durdurur. */
        fun stop() {
            val s = instance ?: return
            s.handler.post { s.finish() }
        }
    }
}
