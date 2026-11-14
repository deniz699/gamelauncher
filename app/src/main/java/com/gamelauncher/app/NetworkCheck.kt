package com.gamelauncher.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

// ------------------------------------------------------------------ Ölçüm motoru

/**
 * Ağ ölçümleri. Hiçbir şeyi değiştirmez: yalnızca gecikmeyi ölçer.
 * Wi-Fi ile 4G/5G aynı DNS sunucusuna (1.1.1.1) aynı anda sorgu atılarak karşılaştırılır;
 * her sorgu ilgili ağa özel olarak bağlanır (Network.bindSocket), varsayılan ağ değişmez.
 */
object NetCheck {
    private const val DNS_SERVER = "1.1.1.1"

    /** Ölçüm sonucu bu değerse cevap gelmemiş demektir. */
    const val FAIL = -1L

    private fun dnsPacket(id: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id shr 8); out.write(id and 0xFF)
        out.write(0x01); out.write(0x00)          // bayraklar: recursion desired
        out.write(0); out.write(1)                // 1 soru
        repeat(6) { out.write(0) }                // answer / authority / additional = 0
        for (label in "www.google.com".split(".")) {
            val b = label.toByteArray()
            out.write(b.size)
            out.write(b, 0, b.size)
        }
        out.write(0)
        out.write(0); out.write(1)                // tip A
        out.write(0); out.write(1)                // sınıf IN
        return out.toByteArray()
    }

    /** Tek bir DNS gidiş-dönüş süresi (ms). [net] null ise varsayılan ağ. Cevap yoksa FAIL. */
    fun dnsPing(net: Network?, timeoutMs: Int = 1500): Long {
        val sock = try { DatagramSocket() } catch (e: Exception) { return FAIL }
        return try {
            net?.bindSocket(sock)
            sock.soTimeout = timeoutMs
            val id = Random.nextInt(0x10000)
            val q = dnsPacket(id)
            val addr = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
            val t0 = System.nanoTime()
            sock.send(DatagramPacket(q, q.size, addr, 53))
            val buf = ByteArray(512)
            val p = DatagramPacket(buf, buf.size)
            while (true) {
                sock.receive(p)
                val rid = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                if (p.length >= 2 && rid == id) break
            }
            ((System.nanoTime() - t0) / 1_000_000).coerceAtLeast(1L)
        } catch (e: Exception) {
            FAIL
        } finally {
            runCatching { sock.close() }
        }
    }

    /** Bir sunucuya TCP bağlantı süresi (ms). DNS çözümü süreye dahil değil. */
    fun tcpPing(host: String, port: Int = 443, timeoutMs: Int = 2500): Long {
        val s = Socket()
        return try {
            val addr = InetSocketAddress(host, port)   // adı burada çözer
            if (addr.isUnresolved) return FAIL
            val t0 = System.nanoTime()
            s.connect(addr, timeoutMs)
            ((System.nanoTime() - t0) / 1_000_000).coerceAtLeast(1L)
        } catch (e: Exception) {
            FAIL
        } finally {
            runCatching { s.close() }
        }
    }

    private const val SPEED_HOST = "https://speed.cloudflare.com"

    private fun downWorker(bytes: AtomicLong, endNs: Long) {
        val buf = ByteArray(64 * 1024)
        while (System.nanoTime() < endNs) {
            val c = URL("$SPEED_HOST/__down?bytes=25000000").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 4000; c.readTimeout = 4000
                c.inputStream.use { ins ->
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        bytes.addAndGet(n.toLong())
                        if (System.nanoTime() >= endNs) break
                    }
                }
            } finally {
                c.disconnect()
            }
        }
    }

    private fun upWorker(bytes: AtomicLong, endNs: Long) {
        val chunk = ByteArray(1_000_000)
        while (System.nanoTime() < endNs) {
            val c = URL("$SPEED_HOST/__up").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 4000; c.readTimeout = 6000
                c.requestMethod = "POST"
                c.doOutput = true
                c.setFixedLengthStreamingMode(chunk.size)
                c.outputStream.use { it.write(chunk) }
                if (c.responseCode !in 200..299) return
                c.inputStream.close()
                bytes.addAndGet(chunk.size.toLong())
            } finally {
                c.disconnect()
            }
        }
    }

    /**
     * Süre dolana kadar paralel bağlantılarla indirir ya da yükler; ortalama hızı Mbit/s döner.
     * [onMbps] ölçüm sürerken yaklaşık 5 kez/sn anlık hızı bildirir. Varsayılan ağ kullanılır.
     */
    suspend fun transfer(download: Boolean, durationMs: Long, threads: Int, onMbps: (Double) -> Unit): Double =
        coroutineScope {
            val bytes = AtomicLong(0)
            val t0 = System.nanoTime()
            val end = t0 + durationMs * 1_000_000
            val jobs = (1..threads).map {
                async(Dispatchers.IO) {
                    runCatching { if (download) downWorker(bytes, end) else upWorker(bytes, end) }
                }
            }
            while (jobs.any { it.isActive }) {
                delay(200)
                val el = (System.nanoTime() - t0) / 1e9
                if (el > 0.4) onMbps(bytes.get() * 8 / el / 1e6)
            }
            val el = (System.nanoTime() - t0) / 1e9
            bytes.get() * 8 / el / 1e6
        }

    class Handle(val net: Network, private val cm: ConnectivityManager, private val cb: ConnectivityManager.NetworkCallback) {
        fun release() { runCatching { cm.unregisterNetworkCallback(cb) } }
    }

    /** İstenen taşıyıcıya (Wi-Fi / hücresel) ait ağı ister. Yoksa ya da kapalıysa null. */
    suspend fun acquire(ctx: Context, transport: Int): Handle? {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val req = NetworkRequest.Builder()
            .addTransportType(transport)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        return suspendCancellableCoroutine { cont ->
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (cont.isActive) cont.resume(Handle(network, cm, this))
                }
                override fun onUnavailable() {
                    runCatching { cm.unregisterNetworkCallback(this) }
                    if (cont.isActive) cont.resume(null)
                }
            }
            try {
                cm.requestNetwork(req, cb, 4000)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
        }
    }

    /** Şu an varsayılan olan ağın adı. */
    fun activeLabel(ctx: Context): String {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return "Bağlantı yok"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Bağlantı yok"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "4G/5G"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Ağ"
        }
    }
}

// ------------------------------------------------------------------ Yardımcılar

private fun okOf(l: List<Long>) = l.filter { it >= 0 }
private fun avgOf(l: List<Long>): Long = okOf(l).let { if (it.isEmpty()) NetCheck.FAIL else it.average().roundToLong() }
private fun variationOf(l: List<Long>): Long = okOf(l).let { if (it.size < 2) 0L else (it.maxOrNull() ?: 0L) - (it.minOrNull() ?: 0L) }
private fun jitterOf(l: List<Long>): Long {
    val o = okOf(l)
    if (o.size < 2) return 0L
    return o.zipWithNext { a, b -> abs(b - a) }.average().roundToLong()
}

private val Good = Color(0xFF4CAF50)
private val Mid = Color(0xFFFFB300)
private val Bad = Color(0xFFE53935)

private fun msColor(ms: Long): Color = when {
    ms < 0 -> Bad
    ms < 60 -> Good
    ms < 110 -> Mid
    else -> Bad
}

private class Side {
    var avail by mutableStateOf<Boolean?>(null)   // null: henüz bakılmadı
    val samples = mutableStateListOf<Long>()
}

private class SpeedState {
    var running by mutableStateOf(false)
    var done by mutableStateOf(false)
    var phase by mutableStateOf("")
    var label by mutableStateOf("")
    var ping by mutableStateOf(-1L)
    var jitter by mutableStateOf(-1L)
    var down by mutableStateOf(-1.0)
    var up by mutableStateOf(-1.0)
    var error by mutableStateOf<String?>(null)
}

private class MeasureState {
    var running by mutableStateOf(false)
    var label by mutableStateOf("")
    val samples = mutableStateListOf<Long>()
}

private class CompareState {
    var running by mutableStateOf(false)
    var done by mutableStateOf(false)
    val wifi = Side()
    val cell = Side()
}

private class RegionState {
    var running by mutableStateOf(false)
    val results = mutableStateMapOf<String, Long>()
}

private const val MEASURE_N = 20
private const val COMPARE_N = 10

private val regions = listOf(
    "Frankfurt" to "ec2.eu-central-1.amazonaws.com",
    "Milano" to "ec2.eu-south-1.amazonaws.com",
    "İrlanda" to "ec2.eu-west-1.amazonaws.com",
    "Londra" to "ec2.eu-west-2.amazonaws.com",
    "Stockholm" to "ec2.eu-north-1.amazonaws.com",
    "Orta Doğu (BAE)" to "ec2.me-central-1.amazonaws.com",
    "Mumbai" to "ec2.ap-south-1.amazonaws.com",
    "Singapur" to "ec2.ap-southeast-1.amazonaws.com",
    "Tokyo" to "ec2.ap-northeast-1.amazonaws.com",
    "ABD Doğu" to "ec2.us-east-1.amazonaws.com",
    "ABD Batı" to "ec2.us-west-2.amazonaws.com",
    "São Paulo" to "ec2.sa-east-1.amazonaws.com"
)

// ------------------------------------------------------------------ Ölçümleri çalıştıranlar

private fun runSpeed(ctx: Context, st: SpeedState, scope: CoroutineScope) {
    if (st.running) return
    st.running = true
    st.done = false
    st.error = null
    st.ping = -1; st.jitter = -1; st.down = -1.0; st.up = -1.0
    st.label = NetCheck.activeLabel(ctx)
    scope.launch {
        try {
            st.phase = "Ping ölçülüyor…"
            val p = ArrayList<Long>()
            repeat(10) {
                p.add(withContext(Dispatchers.IO) { NetCheck.dnsPing(null) })
                delay(100)
            }
            st.ping = avgOf(p)
            st.jitter = jitterOf(p)

            st.phase = "İndirme ölçülüyor…"
            st.down = NetCheck.transfer(true, 8000, 4) { st.down = it }
            if (st.down < 0.01) {
                st.error = "Hız testi sunucusuna ulaşılamadı. İnternet bağlantını kontrol et."
                st.down = -1.0
                return@launch
            }

            st.phase = "Yükleme ölçülüyor…"
            st.up = NetCheck.transfer(false, 8000, 3) { st.up = it }
            if (st.up < 0.01) st.up = 0.0
            st.done = true
        } finally {
            st.running = false
        }
    }
}

private fun runMeasure(ctx: Context, st: MeasureState, scope: CoroutineScope) {
    if (st.running) return
    st.running = true
    st.samples.clear()
    st.label = NetCheck.activeLabel(ctx)
    scope.launch {
        try {
            repeat(MEASURE_N) {
                st.samples.add(withContext(Dispatchers.IO) { NetCheck.dnsPing(null) })
                delay(150)
            }
        } finally {
            st.running = false
        }
    }
}

private fun runCompare(ctx: Context, st: CompareState, scope: CoroutineScope) {
    if (st.running) return
    st.running = true
    st.done = false
    st.wifi.samples.clear(); st.cell.samples.clear()
    st.wifi.avail = null; st.cell.avail = null
    scope.launch {
        try {
            coroutineScope {
                listOf(
                    st.wifi to NetworkCapabilities.TRANSPORT_WIFI,
                    st.cell to NetworkCapabilities.TRANSPORT_CELLULAR
                ).map { (side, transport) ->
                    async {
                        val h = NetCheck.acquire(ctx, transport)
                        if (h == null) {
                            side.avail = false
                        } else {
                            side.avail = true
                            try {
                                repeat(COMPARE_N) {
                                    side.samples.add(withContext(Dispatchers.IO) { NetCheck.dnsPing(h.net) })
                                    delay(250)
                                }
                            } finally {
                                h.release()
                            }
                        }
                    }
                }.awaitAll()
            }
            st.done = true
        } finally {
            st.running = false
        }
    }
}

private fun runRegions(st: RegionState, scope: CoroutineScope) {
    if (st.running) return
    st.running = true
    st.results.clear()
    scope.launch {
        try {
            coroutineScope {
                regions.map { (name, host) ->
                    async {
                        val best = withContext(Dispatchers.IO) {
                            (1..3).map { NetCheck.tcpPing(host) }.filter { it >= 0 }.minOrNull() ?: NetCheck.FAIL
                        }
                        st.results[name] = best
                    }
                }.awaitAll()
            }
        } finally {
            st.running = false
        }
    }
}

// ------------------------------------------------------------------ Ekran

@Composable
fun NetworkPage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val speed = remember { SpeedState() }
    val measure = remember { MeasureState() }
    val compare = remember { CompareState() }
    val region = remember { RegionState() }

    BackHandler(onBack = onClose)
    PageSurface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTopBar("Ağ kontrolü", onClose)
            Box(Modifier.padding(horizontal = 16.dp)) {
                SegmentedChoice(listOf("Hız", "Ping", "Wi-Fi/4G", "Bölge"), tab) { tab = it }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    0 -> SpeedTab(speed) { runSpeed(ctx, speed, scope) }
                    1 -> MeasureTab(measure) { runMeasure(ctx, measure, scope) }
                    2 -> CompareTab(compare) { runCompare(ctx, compare, scope) }
                    else -> RegionsTab(region) { runRegions(region, scope) }
                }
                Text(
                    "Bu uygulama bağlantını değiştirmez, yalnızca ölçer.",
                    Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StartButton(running: Boolean, idle: String, busy: String, onClick: () -> Unit) {
    if (running) {
        SoftButton(label = busy, onClick = {}, modifier = Modifier.fillMaxWidth())
    } else {
        PlayButton(onClick, Modifier.fillMaxWidth(), label = idle)
    }
}

// ---- Hız

private fun mbps(v: Double): String = if (v < 0) "—" else if (v >= 100) "%.0f".format(v) else "%.1f".format(v)

@Composable
private fun SpeedStat(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.glassSurface(RoundedCornerShape(18.dp), cs.surfaceContainerHigh).padding(14.dp)) {
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(" $unit", Modifier.padding(bottom = 5.dp), fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpeedTab(st: SpeedState, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val started = st.running || st.done || st.error != null
    FlatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, null, Modifier.size(22.dp), tint = cs.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                if (st.label.isBlank()) "İnternet hız testi" else "İnternet hız testi · ${st.label}",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            if (st.running) st.phase else "Ping, indirme ve yükleme hızını ölçer. Yaklaşık 20 saniye sürer ve biraz veri kullanır (mobil veride dikkat).",
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        if (started) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeedStat("İNDİRME", mbps(st.down), "Mbps", if (st.down >= 0) cs.primary else cs.onSurfaceVariant, Modifier.weight(1f))
                SpeedStat("YÜKLEME", mbps(st.up), "Mbps", if (st.up >= 0) cs.primary else cs.onSurfaceVariant, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeedStat("PING", if (st.ping >= 0) "${st.ping}" else "—", "ms", if (st.ping >= 0) msColor(st.ping) else cs.onSurfaceVariant, Modifier.weight(1f))
                SpeedStat("DALGALANMA", if (st.jitter >= 0) "${st.jitter}" else "—", "ms", cs.onSurface, Modifier.weight(1f))
            }
        }
        st.error?.let { Text(it, Modifier.padding(top = 14.dp), color = Bad, fontWeight = FontWeight.SemiBold) }
    }
    StartButton(st.running, if (st.done || st.error != null) "Tekrar test et" else "Hız testini başlat", st.phase, onStart)
}

// ---- Ölç

@Composable
private fun MeasureTab(st: MeasureState, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val avg = avgOf(st.samples)
    val jitter = jitterOf(st.samples)
    val lost = st.samples.count { it < 0 }
    val has = st.samples.isNotEmpty()

    FlatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NetworkCheck, null, Modifier.size(22.dp), tint = cs.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                if (st.label.isBlank()) "Şu an kullandığın ağ" else "Şu an: ${st.label}",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                if (avg >= 0) "$avg" else "—",
                fontSize = 56.sp, fontWeight = FontWeight.ExtraBold,
                color = if (avg >= 0) msColor(avg) else cs.onSurfaceVariant
            )
            Text(" ms", Modifier.padding(bottom = 10.dp), color = cs.onSurfaceVariant)
        }
        if (has) {
            SampleBars(st.samples, MEASURE_N, Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("dalgalanma $jitter ms", color = cs.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    "cevapsız $lost/${st.samples.size}",
                    color = if (lost > 0) Bad else cs.onSurfaceVariant, fontSize = 13.sp
                )
            }
        }
    }
    if (has && !st.running) {
        val (text, color) = gradeText(avg, jitter, lost)
        FlatCard { Text(text, color = color, fontWeight = FontWeight.SemiBold) }
    }
    StartButton(st.running, "Ölçümü başlat", "Ölçülüyor ${st.samples.size}/$MEASURE_N", onStart)
}

private fun gradeText(avg: Long, jitter: Long, lost: Int): Pair<String, Color> = when {
    avg < 0 -> "Hiç cevap gelmedi. İnternet bağlantını kontrol et." to Bad
    lost >= 3 || jitter > 40 -> "Bağlantı dengesiz: paket kaybı ya da sıçramalar var. Online oyunda takılma olabilir." to Bad
    avg < 40 -> "Çok iyi. Rekabetçi oyunlar için ideal." to Good
    avg < 70 -> "İyi. Çoğu online oyun sorunsuz çalışır." to Good
    avg < 120 -> "Orta. Hızlı tempolu oyunlarda gecikme hissedilebilir." to Mid
    else -> "Yüksek gecikme. Online oyunlarda belirgin gecikme olur." to Bad
}

// ---- Karşılaştır

@Composable
private fun CompareTab(st: CompareState, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val started = st.wifi.avail != null || st.cell.avail != null

    FlatCard {
        Text("Wi-Fi mi 4G/5G mi?", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Text(
            "İkisine aynı DNS sunucusuna (1.1.1.1) aynı anda $COMPARE_N sorgu atılır. Bağlantıyı değiştirmez, sadece ölçer.",
            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        if (st.done) {
            val (text, color) = compareVerdict(st)
            Text(text, Modifier.padding(top = 14.dp), color = color, fontWeight = FontWeight.Bold)
            val w = avgOf(st.wifi.samples); val c = avgOf(st.cell.samples)
            if (w >= 0 && c >= 0) {
                Text(
                    "Wi-Fi $w ms · 4G/5G $c ms · her biri $COMPARE_N örnek, birlikte ölçüldü",
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
    }
    if (started) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideCard("Wi-Fi", Icons.Default.Wifi, st.wifi, Modifier.weight(1f))
            SideCard("4G/5G", Icons.Default.SignalCellularAlt, st.cell, Modifier.weight(1f))
        }
    }
    StartButton(st.running, if (st.done) "Tekrar ölç" else "Karşılaştır", "Ölçülüyor…", onStart)
}

private fun scoreOf(s: Side): Long {
    val a = avgOf(s.samples)
    if (a < 0) return Long.MAX_VALUE
    return a + variationOf(s.samples) / 2 + s.samples.count { it < 0 } * 80L
}

private fun compareVerdict(st: CompareState): Pair<String, Color> {
    val wOk = st.wifi.avail == true && okOf(st.wifi.samples).isNotEmpty()
    val cOk = st.cell.avail == true && okOf(st.cell.samples).isNotEmpty()
    return when {
        !wOk && !cOk -> "İki ağdan da cevap alınamadı." to Bad
        wOk && !cOk -> (if (st.cell.avail == false) "4G/5G ölçülemedi (mobil veri kapalı ya da SIM yok). Wi-Fi kullan."
        else "4G/5G'den cevap gelmedi. Wi-Fi kullan.") to Mid
        !wOk && cOk -> (if (st.wifi.avail == false) "Wi-Fi'a bağlı değilsin. 4G/5G ile oynayabilirsin."
        else "Wi-Fi'dan cevap gelmedi. 4G/5G kullan.") to Mid
        else -> {
            val w = scoreOf(st.wifi); val c = scoreOf(st.cell)
            when {
                abs(w - c) < 10 -> "İki ağ da benzer, hangisiyle oynasan olur." to Good
                w < c -> "Wi-Fi şu an oynamak için daha iyi." to Good
                else -> "4G/5G şu an oynamak için daha iyi." to Good
            }
        }
    }
}

@Composable
private fun SideCard(title: String, icon: ImageVector, side: Side, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    FlatCard(modifier = modifier, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = cs.primary)
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        if (side.avail == false) {
            Text(
                "Bağlı değil ya da kapalı", Modifier.padding(top = 14.dp),
                color = cs.onSurfaceVariant, fontSize = 14.sp
            )
            return@FlatCard
        }
        val avg = avgOf(side.samples)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                if (avg >= 0) "$avg" else "…", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                color = if (avg >= 0) msColor(avg) else cs.onSurfaceVariant
            )
            Text(" ms", Modifier.padding(bottom = 6.dp), color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
        SampleBars(side.samples, COMPARE_N, Modifier.padding(top = 8.dp))
        Text(
            "değişim ${variationOf(side.samples)} ms", Modifier.padding(top = 10.dp),
            color = cs.onSurfaceVariant, fontSize = 12.sp
        )
        val lost = side.samples.count { it < 0 }
        Text(
            "cevapsız $lost/$COMPARE_N", color = if (lost > 0) Bad else cs.onSurfaceVariant, fontSize = 12.sp
        )
    }
}

@Composable
private fun SampleBars(samples: List<Long>, total: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val maxV = (okOf(samples).maxOrNull() ?: 1L).coerceAtLeast(1L)
    val full = 56f
    Row(
        modifier.fillMaxWidth().height(full.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until total) {
            val v = samples.getOrNull(i)
            val h = if (v == null || v < 0) 3f else (full * v.toFloat() / maxV).coerceAtLeast(8f)
            val c = when {
                v == null -> cs.outlineVariant
                v < 0 -> Bad
                else -> msColor(v)
            }
            Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(3.dp)).background(c))
        }
    }
}

// ---- Bölgeler

@Composable
private fun RegionsTab(st: RegionState, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val rows = regions.map { (name, _) -> name to st.results[name] }
    val sorted = rows.sortedWith(compareBy({ it.second == null }, { (it.second ?: 0L).let { v -> if (v < 0) Long.MAX_VALUE else v } }))
    val best = sorted.firstOrNull()?.takeIf { (it.second ?: -1L) >= 0 }

    FlatCard {
        Text("Hangi bölgeye ne kadar yakınsın?", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Text(
            "Büyük bulut bölgelerine bağlantı süresini ölçer. Oyun sunucusu seçeneği olan oyunlarda en düşük süreli bölgeyi seç.",
            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        if (best != null && !st.running) {
            Text(
                "En yakın: ${best.first} (${best.second} ms)", Modifier.padding(top = 14.dp),
                color = Good, fontWeight = FontWeight.Bold
            )
        }
    }
    if (st.results.isNotEmpty() || st.running) {
        FlatCard(pad = 6.dp) {
            sorted.forEach { (name, ms) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    when {
                        ms == null -> Text("…", color = cs.onSurfaceVariant)
                        ms < 0 -> Text("cevap yok", color = Bad, fontSize = 13.sp)
                        else -> Text("$ms ms", color = msColor(ms), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    StartButton(st.running, if (st.results.isEmpty()) "Bölgeleri ölç" else "Tekrar ölç", "Ölçülüyor…", onStart)
}
