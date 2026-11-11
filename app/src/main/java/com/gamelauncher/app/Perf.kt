package com.gamelauncher.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

/** Root (su) çalıştırma. su yoksa ya da izin verilmezse null döner. */
object Root {
    fun run(cmd: String, timeoutSec: Long = 40): Pair<Int, String>? = runCatching {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        var out = ""
        val reader = Thread { out = p.inputStream.bufferedReader().readText() }
        reader.start()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroy()
            return@runCatching null
        }
        reader.join(2000)
        p.exitValue() to out.trim()
    }.getOrNull()

    fun available(): Boolean =
        run("id")?.let { it.first == 0 && it.second.contains("uid=0") } == true
}

data class CpuPolicy(
    val dir: String, val cores: String, val gov: String, val govs: List<String>,
    val min: Long, val max: Long, val hwMin: Long, val hwMax: Long
)

/**
 * backend: "nodes" = min/max frekans dosyaları (Snapdragon Adreno, Mali devfreq, Exynos, Tensor),
 *          "mtk1" = MediaTek /proc/gpufreq, "mtk2" = MediaTek /proc/gpufreqv2
 */
data class GpuInfo(
    val backend: String, val kind: String, val dir: String,
    val minFile: String, val maxFile: String, val govFile: String?,
    val gov: String, val govs: List<String>,
    val min: Long, val max: Long, val cur: Long, val freqs: List<Long>,
    val pwrPath: String?, val pwrMin: String?
)

data class PerfInfo(val cpus: List<CpuPolicy>, val gpu: GpuInfo?)

fun perfBatteryTemp(ctx: Context): Float {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return (i?.getIntExtra("temperature", 0) ?: 0) / 10f
}

/** İşlemci ailesi tahmini (Build bilgilerinden). */
fun socFamily(): String {
    val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else ""
    val h = "${Build.HARDWARE} ${Build.BOARD} $soc".lowercase()
    return when {
        "qcom" in h || "qualcomm" in h || "snapdragon" in h || Regex("\\b(msm|sm|sdm|sdx)\\d").containsMatchIn(h) -> "Snapdragon"
        "mediatek" in h || Regex("\\bmt\\d{4}").containsMatchIn(h) -> "MediaTek"
        "exynos" in h || "s5e" in h || "universal" in h -> "Exynos"
        "tensor" in h || Regex("\\bgs\\d{3}").containsMatchIn(h) -> "Google Tensor"
        else -> "Bilinmeyen işlemci"
    }
}

/** CPU / GPU frekans ve governor ayarları (sysfs / proc). Yazmak için root gerekir. */
object Perf {
    private const val CPU_BASE = "/sys/devices/system/cpu"
    private const val MTK1_FIX = "/proc/gpufreq/gpufreq_opp_freq"
    private const val MTK2_FIX = "/proc/gpufreqv2/fix_target_opp_index"
    private val names = listOf("Dengeli", "Performans", "Maksimum")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("perf", Context.MODE_PRIVATE)

    fun level(ctx: Context): Int = prefs(ctx).getInt("level", 0)

    private fun ls(dir: String): List<String> {
        File(dir).list()?.let { if (it.isNotEmpty()) return it.sorted() }
        val r = Root.run("ls $dir 2>/dev/null") ?: return emptyList()
        return r.second.lines().map { it.trim() }.filter { it.isNotEmpty() }.sorted()
    }

    /** Birçok dosyayı tek root oturumunda okur: yol -> içerik */
    private fun readAll(paths: List<String>): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        val script = paths.joinToString("\n") { "echo \"@@$it\"; cat $it 2>/dev/null" }
        val out = Root.run(script)?.second ?: return emptyMap()
        val map = HashMap<String, String>()
        var cur: String? = null
        val sb = StringBuilder()
        for (line in out.lines()) {
            if (line.startsWith("@@")) {
                cur?.let { map[it] = sb.toString().trim() }
                cur = line.substring(2)
                sb.setLength(0)
            } else {
                sb.append(line).append('\n')
            }
        }
        cur?.let { map[it] = sb.toString().trim() }
        return map.filterValues { it.isNotEmpty() }
    }

    private fun num(s: String?): Long = s?.trim()?.toLongOrNull() ?: 0L
    private fun words(s: String?): List<String> =
        s?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()

    // ------------------------------------------------------------ GPU tespiti

    private class Node(
        val dir: String, val min: String, val max: String, val avail: String,
        val gov: String?, val govs: String?, val cur: String?, val kind: String
    )

    /** Frekans aralığı dosyası sunan GPU yolları (Snapdragon, Mali devfreq, Exynos, Tensor). */
    private fun nodeCandidates(): List<Node> {
        val l = ArrayList<Node>()
        if (ls("/sys/class/kgsl/kgsl-3d0/devfreq").isNotEmpty()) {
            l += Node(
                "/sys/class/kgsl/kgsl-3d0/devfreq", "min_freq", "max_freq", "available_frequencies",
                "governor", "available_governors", "cur_freq", "Adreno (Snapdragon)"
            )
        }
        ls("/sys/class/devfreq")
            .filter { n -> listOf("gpu", "mali", "kgsl").any { n.contains(it, ignoreCase = true) } }
            .forEach {
                l += Node(
                    "/sys/class/devfreq/$it", "min_freq", "max_freq", "available_frequencies",
                    "governor", "available_governors", "cur_freq", "Mali / GPU (devfreq)"
                )
            }
        if (ls("/sys/kernel/gpu").isNotEmpty()) {
            l += Node(
                "/sys/kernel/gpu", "gpu_min_clock", "gpu_max_clock", "gpu_available_frequencies",
                "gpu_governor", "gpu_available_governors", "gpu_clock", "Mali (Exynos)"
            )
        }
        ls("/sys/devices/platform").filter { it.contains("mali", ignoreCase = true) }.forEach {
            l += Node(
                "/sys/devices/platform/$it", "scaling_min_freq", "scaling_max_freq", "available_frequencies",
                null, null, null, "Mali (Exynos / Tensor)"
            )
        }
        return l
    }

    private fun readNodes(): GpuInfo? {
        for (n in nodeCandidates()) {
            val pwr = if (n.kind.startsWith("Adreno")) "/sys/class/kgsl/kgsl-3d0/min_pwrlevel" else null
            val wanted = ArrayList<String>()
            wanted += "${n.dir}/${n.min}"
            wanted += "${n.dir}/${n.max}"
            wanted += "${n.dir}/${n.avail}"
            n.gov?.let { wanted += "${n.dir}/$it" }
            n.govs?.let { wanted += "${n.dir}/$it" }
            n.cur?.let { wanted += "${n.dir}/$it" }
            pwr?.let { wanted += it }
            val g = readAll(wanted)
            val max = num(g["${n.dir}/${n.max}"])
            if (max <= 0) continue
            return GpuInfo(
                "nodes", n.kind, n.dir, n.min, n.max, n.gov,
                n.gov?.let { g["${n.dir}/$it"] } ?: "",
                words(n.govs?.let { g["${n.dir}/$it"] }),
                num(g["${n.dir}/${n.min}"]), max,
                num(n.cur?.let { g["${n.dir}/$it"] }),
                words(g["${n.dir}/${n.avail}"]).mapNotNull { it.toLongOrNull() }.sorted(),
                if (pwr != null && g.containsKey(pwr)) pwr else null,
                if (pwr != null) g[pwr] else null
            )
        }
        return null
    }

    /** MediaTek: aralık yok, GPU'yu en yüksek kademeye sabitleme / bırakma vardır. */
    private fun readMtk(): GpuInfo? {
        if (ls("/proc/gpufreqv2").contains("fix_target_opp_index")) {
            return GpuInfo("mtk2", "MediaTek GPU (gpufreqv2)", "/proc/gpufreqv2", "", "", null, "", emptyList(), 0, 0, 0, emptyList(), null, null)
        }
        if (ls("/proc/gpufreq").contains("gpufreq_opp_freq")) {
            val key = "/proc/gpufreq/gpufreq_opp_dump"
            val dump = readAll(listOf(key))[key] ?: ""
            val top = Regex("freq\\s*=\\s*(\\d+)").findAll(dump)
                .mapNotNull { it.groupValues[1].toLongOrNull() }.maxOrNull() ?: 0L
            return GpuInfo("mtk1", "MediaTek GPU (gpufreq)", "/proc/gpufreq", "", "", null, "", emptyList(), 0, top, 0, emptyList(), null, null)
        }
        return null
    }

    /** Cihazdaki CPU kümelerini ve (varsa) GPU'yu okur. */
    fun read(): PerfInfo {
        var dirs = ls("$CPU_BASE/cpufreq").filter { it.startsWith("policy") }.map { "$CPU_BASE/cpufreq/$it" }
        if (dirs.isEmpty()) {
            dirs = ls(CPU_BASE).filter { Regex("cpu\\d+").matches(it) }.map { "$CPU_BASE/$it/cpufreq" }
        }
        val files = listOf(
            "scaling_governor", "scaling_available_governors", "scaling_min_freq", "scaling_max_freq",
            "cpuinfo_min_freq", "cpuinfo_max_freq", "related_cpus"
        )
        val v = readAll(dirs.flatMap { d -> files.map { "$d/$it" } })
        val cpus = dirs.mapNotNull { d ->
            val gov = v["$d/scaling_governor"] ?: return@mapNotNull null
            val hwMax = num(v["$d/cpuinfo_max_freq"]).takeIf { it > 0 } ?: num(v["$d/scaling_max_freq"])
            CpuPolicy(
                d, v["$d/related_cpus"]?.trim() ?: "", gov, words(v["$d/scaling_available_governors"]),
                num(v["$d/scaling_min_freq"]), num(v["$d/scaling_max_freq"]), num(v["$d/cpuinfo_min_freq"]), hwMax
            )
        }.distinctBy { if (it.cores.isBlank()) it.dir else it.cores }

        val gpu = if (socFamily() == "MediaTek") (readMtk() ?: readNodes()) else (readNodes() ?: readMtk())
        return PerfInfo(cpus, gpu)
    }

    // ------------------------------------------------------------ Yazma

    /** Başarısız yazma sayısını döner. */
    private fun write(cmds: List<Pair<String, String>>): Int {
        if (cmds.isEmpty()) return 0
        val script = cmds.joinToString("\n") { (p, v) -> "echo $v > $p 2>/dev/null || echo FAIL" }
        val out = Root.run(script)?.second ?: return cmds.size
        return out.lines().count { it.trim() == "FAIL" }
    }

    /** Geri yüklenecek değerler (sıra önemli: governor, min, max). */
    private fun origList(info: PerfInfo): List<Pair<String, String>> {
        val l = ArrayList<Pair<String, String>>()
        info.cpus.forEach { c ->
            l += "${c.dir}/scaling_governor" to c.gov
            l += "${c.dir}/scaling_min_freq" to c.min.toString()
            l += "${c.dir}/scaling_max_freq" to c.max.toString()
        }
        info.gpu?.let { g ->
            when (g.backend) {
                "nodes" -> {
                    if (g.govFile != null && g.gov.isNotEmpty()) l += "${g.dir}/${g.govFile}" to g.gov
                    l += "${g.dir}/${g.minFile}" to g.min.toString()
                    l += "${g.dir}/${g.maxFile}" to g.max.toString()
                    if (g.pwrPath != null && g.pwrMin != null) l += g.pwrPath to g.pwrMin
                }
                "mtk1" -> l += MTK1_FIX to "0"     // 0 = sabitlemeyi kaldır
                "mtk2" -> l += MTK2_FIX to "-1"    // -1 = sabitlemeyi kaldır
            }
        }
        return l
    }

    /** 0 = Dengeli (eski değerler), 1 = Performans, 2 = Maksimum. Sonuç mesajı döner. */
    fun apply(ctx: Context, level: Int, info: PerfInfo): String {
        if (level == 0) return restore(ctx)
        val p = prefs(ctx)
        if (!p.contains("orig")) {
            p.edit().putString("orig", origList(info).joinToString("\n") { it.first + "\t" + it.second }).apply()
        }
        val w = ArrayList<Pair<String, String>>()
        for (c in info.cpus) {
            if (c.hwMax <= 0) continue
            w += "${c.dir}/scaling_max_freq" to c.hwMax.toString()   // önce üst sınırı aç
            if (level == 2 && "performance" in c.govs) w += "${c.dir}/scaling_governor" to "performance"
            val minT = if (level == 2) c.hwMax else (c.hwMax * 0.55).toLong().coerceAtLeast(c.hwMin)
            w += "${c.dir}/scaling_min_freq" to minT.toString()
        }
        var note = ""
        info.gpu?.let { g ->
            when (g.backend) {
                "nodes" -> {
                    // Değerler cihazın kendi listesinden seçilir, birim varsayılmaz
                    val top = g.freqs.maxOrNull() ?: g.max
                    w += "${g.dir}/${g.maxFile}" to top.toString()
                    if (level == 2 && g.govFile != null && "performance" in g.govs) w += "${g.dir}/${g.govFile}" to "performance"
                    val minT = if (level == 2) top else (g.freqs.firstOrNull { it >= top * 0.5 } ?: (top * 0.5).toLong())
                    w += "${g.dir}/${g.minFile}" to minT.toString()
                    if (level == 2 && g.pwrPath != null) w += g.pwrPath to "0"
                }
                "mtk1" -> if (level == 2 && g.max > 0) w += MTK1_FIX to g.max.toString()
                "mtk2" -> if (level == 2) w += MTK2_FIX to "0"
            }
            if (level == 1 && g.backend.startsWith("mtk")) note = " MediaTek GPU'ya Performans seviyesinde dokunulmaz, yalnızca Maksimum'da sabitlenir."
        }
        val fail = write(w)
        return when {
            w.isEmpty() -> "Bu cihazda ayarlanabilir CPU/GPU dosyası bulunamadı."
            fail >= w.size -> "Değerler yazılamadı. Sistem bu dosyalara yazmayı engelliyor olabilir."
            else -> {
                p.edit().putInt("level", level).apply()
                (if (fail == 0) "${names[level]} uygulandı." else "${names[level]} kısmen uygulandı ($fail değer yazılamadı).") + note
            }
        }
    }

    fun restore(ctx: Context): String {
        val p = prefs(ctx)
        val saved = p.getString("orig", null)
        if (saved != null) {
            val list = saved.lines().mapNotNull { l ->
                val i = l.indexOf('\t')
                if (i > 0) l.substring(0, i) to l.substring(i + 1) else null
            }
            val fail = write(list)
            if (fail < list.size) p.edit().remove("orig").apply()
        }
        p.edit().putInt("level", 0).apply()
        return "Dengeli: varsayılan değerler geri yüklendi"
    }
}
