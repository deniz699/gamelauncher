package com.gamelauncher.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Arka planda belirli aralıklarla yeni sürüm var mı diye bakar, varsa bildirim gösterir. */
class UpdateWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Updater.repo().isBlank()) return Result.success()      // yerel derleme
        val r = runCatching { Updater.fetchLatest() }.getOrElse {
            return if (it is java.io.IOException) Result.retry() else Result.success()
        }
        val prefs = ctx.getSharedPreferences("updater", Context.MODE_PRIVATE)
        // Yeni sürüm var mı + bu sürüm için daha önce bildirim gönderdik mi?
        if (r.build > Updater.current(ctx).code && prefs.getInt("notified", 0) < r.build) {
            if (notify(ctx)) prefs.edit().putInt("notified", r.build).apply()
        }
        return Result.success()
    }

    private fun notify(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        nm.createNotificationChannel(
            NotificationChannel("update", "Güncellemeler", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, "update")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Yeni sürüm mevcut")
            .setContentText("Game Launcher güncellemesi hazır. Ayarlar → Güncellemeler'den yükleyebilirsin.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
        return true
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
