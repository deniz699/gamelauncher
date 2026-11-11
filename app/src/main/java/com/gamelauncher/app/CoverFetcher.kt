package com.gamelauncher.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object CoverFetcher {

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** İnternet yoksa IOException fırlatır; sonuç bulunamazsa null döner. */
    fun fetch(name: String, pkg: String): ByteArray? {
        itunesImageUrl(name)?.let { u -> getOrNull(u)?.let { return it } }
        playImageUrl(pkg)?.let { u -> getOrNull(u)?.let { return it } }
        return null
    }

    /**
     * Kapak seçerken gösterilecek görsel adresleri (en fazla 12).
     * İnternet yoksa IOException fırlatır; hiçbir şey bulunamazsa boş liste döner.
     */
    fun suggestions(name: String, pkg: String): List<String> {
        // İlk istek internet yoksa burada IOException fırlatır
        val apple = itunesCandidates(name)
        val out = LinkedHashSet<String>()
        // Google Play: paket adı birebir tuttuğu için en güvenilir görsel
        runCatching { playImageUrl(pkg) }.getOrNull()?.let { out += it }
        out.addAll(apple)
        return out.take(12)
    }

    /** Görseli indirir; hata olursa null. */
    fun download(url: String): ByteArray? = runCatching { getOrNull(url) }.getOrNull()

    private fun itunesCandidates(name: String): List<String> {
        val body = getOrNull(
            "https://itunes.apple.com/search?term=${URLEncoder.encode(name, "UTF-8")}&entity=software&limit=10"
        ) ?: return emptyList()
        return runCatching {
            val arr = JSONObject(body.toString(Charsets.UTF_8)).optJSONArray("results") ?: return emptyList()
            val n = norm(name)
            val games = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optJSONArray("genres")?.toString()?.contains("Games") == true }
            val close = games.filter {
                val t = norm(it.optString("trackName"))
                t.length >= 3 && n.length >= 3 && (t.startsWith(n) || n.startsWith(t) || t.contains(n) || n.contains(t))
            }
            val urls = ArrayList<String>()
            close.ifEmpty { games.take(3) }.forEach { o ->
                val art = o.optString("artworkUrl512")
                if (art.isNotEmpty()) urls += art
                for (key in listOf("screenshotUrls", "ipadScreenshotUrls")) {
                    val a = o.optJSONArray(key) ?: continue
                    for (i in 0 until minOf(2, a.length())) urls += a.getString(i)
                }
            }
            urls
        }.getOrDefault(emptyList())
    }

    /** Oyun adıyla arar, oyun ekran görüntüsü (yoksa resmi görsel) bulur. */
    private fun itunesImageUrl(name: String): String? {
        val body = getOrNull(
            "https://itunes.apple.com/search?term=${URLEncoder.encode(name, "UTF-8")}&entity=software&limit=8"
        ) ?: return null
        return runCatching {
            val arr = JSONObject(body.toString(Charsets.UTF_8)).optJSONArray("results") ?: return null
            val n = norm(name)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optJSONArray("genres")?.toString()?.contains("Games") != true) continue
                val t = norm(o.optString("trackName"))
                if (t.length < 3 || n.length < 3) continue
                if (!(t.startsWith(n) || n.startsWith(t))) continue
                for (key in listOf("screenshotUrls", "ipadScreenshotUrls")) {
                    val a = o.optJSONArray(key)
                    if (a != null && a.length() > 0) return a.getString(0)
                }
                val art = o.optString("artworkUrl512")
                if (art.isNotEmpty()) return art
            }
            null
        }.getOrNull()
    }

    /** Paket adıyla Google Play sayfasındaki görseli bulur. */
    private fun playImageUrl(pkg: String): String? {
        val body = getOrNull("https://play.google.com/store/apps/details?id=$pkg&hl=en&gl=US") ?: return null
        val html = body.toString(Charsets.UTF_8)
        val m = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"").find(html) ?: return null
        return m.groupValues[1].replace("&amp;", "&")
    }

    private fun getOrNull(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.instanceFollowRedirects = true
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            if (c.responseCode !in 200..299) return null
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
