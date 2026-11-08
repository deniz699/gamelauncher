package com.gamelauncher.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * RAM temizleme: arka plandaki uygulamaların işlemlerini kapattırır (KILL_BACKGROUND_PROCESSES).
 * Root gerektirmez ama sınırlıdır: Android bu uygulamaları sonradan kendisi yeniden başlatabilir.
 */
object Booster {
    private fun avail(ctx: Context): Long {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
    }

    /** keep: kapatılmayacak paketler (ör. şu an oynanan oyun). Sonuç metni ana iş parçacığında done'a verilir. */
    fun clean(ctx: Context, keep: Set<String>, done: (String) -> Unit) {
        val app = ctx.applicationContext
        val pm = app.packageManager
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val skip = HashSet(keep)
        skip += app.packageName
        runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName?.let { skip += it }
        }
        runCatching {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { skip += it }
        }
        val before = avail(app)
        Thread {
            runCatching {
                pm.getInstalledApplications(0).forEach { ai ->
                    val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    if (!system && ai.packageName !in skip) runCatching { am.killBackgroundProcesses(ai.packageName) }
                }
            }
            Thread.sleep(900)
            val after = avail(app)
            val diff = ((after - before) / 1048576L).coerceAtLeast(0)
            val msg = "RAM temizlendi • boş: ${gb(after)}" + if (diff > 0) " (+$diff MB)" else ""
            Handler(Looper.getMainLooper()).post { done(msg) }
        }.start()
    }
}
