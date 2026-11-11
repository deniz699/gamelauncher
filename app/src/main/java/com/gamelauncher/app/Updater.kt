package com.gamelauncher.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Güncelleme akışı:
 *  1) GitHub Actions her derlemede, kaynak koddan ayrı olan herkese açık "releases" deposunda
 *     "v1.0.<numara>" etiketli bir sürüm yayınlar ve yalnızca APK'yı ekler (build.yml).
 *  2) Uygulama GitHub'daki en son sürümü sorar, numarasını kendi sürüm numarasıyla karşılaştırır.
 *  3) Yeni sürüm varsa APK'yı indirir ve Android'in kurma ekranını açar (kullanıcı onaylar).
 */
object Updater {
    data class Release(val tag: String, val build: Int, val notes: String, val apkUrl: String?, val pageUrl: String)
    data class Current(val name: String, val code: Long)

    class NoRepoException : Exception()
    class NotFoundException : Exception()

    fun repo(): String = BuildConfig.GITHUB_REPO

    fun current(ctx: Context): Current {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        return Current(pi.versionName ?: "?", code)
    }

    /** Ağ işlemi: arka plan iş parçacığında çağır. */
    fun fetchLatest(): Release {
        if (repo().isBlank()) throw NoRepoException()
        val c = URL("https://api.github.com/repos/${repo()}/releases/latest").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "GameLauncher")
            if (c.responseCode == 404) throw NotFoundException()
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = o.getString("tag_name")
            var apk: String? = null
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = a.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }
            return Release(
                tag = tag,
                build = Regex("(\\d+)\\s*$").find(tag)?.value?.toIntOrNull() ?: 0,
                notes = o.optString("body"),
                apkUrl = apk,
                pageUrl = o.optString("html_url")
            )
        } finally {
            c.disconnect()
        }
    }

    fun explain(t: Throwable): String = when (t) {
        is NoRepoException -> "Bu sürüm GitHub üzerinden derlenmediği için güncelleme kontrol edilemiyor."
        is NotFoundException -> "Yayınlanmış bir sürüm bulunamadı. Depo gizli olabilir ya da henüz sürüm yayınlanmamış."
        is java.io.IOException -> "İnternet bağlantısı kurulamadı. Bağlantını kontrol edip tekrar dene."
        else -> "Güncelleme kontrol edilemedi."
    }

    /** Ağ işlemi: arka plan iş parçacığında çağır. İlerleme: (inen bayt, toplam bayt); toplam bilinmiyorsa 0. */
    fun download(ctx: Context, url: String, onProgress: (Long, Long) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "GameLauncher.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "GameLauncher")
            if (c.responseCode != 200) throw FileNotFoundException()
            val total = c.contentLengthLong
            var done = 0L
            var last = -1
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else (done / (256 * 1024)).toInt()
                        if (pct != last) { last = pct; onProgress(done, maxOf(total, 0L)) }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return out
    }

    /** false: "bilinmeyen kaynaklardan kur" izni yok; izin ekranı açıldı. */
    fun install(ctx: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
