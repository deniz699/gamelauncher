package com.gamelauncher.app

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mail
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * İletişim formu: mesaj, Cloudflare Worker üzerinden gizli bir GitHub deposuna Issue olarak düşer.
 * GitHub anahtarı uygulamada DEĞİL, Worker'da saklanır (bkz. worker.js).
 */
object Contact {
    /** Worker adresin, sonunda "/" olmadan. Örn: "https://gamelauncher-mesaj.kullanici.workers.dev" */
    const val ENDPOINT = "https://gamelauncher-mesaj.oyunhesabi1724.workers.dev"

    /** Ağ işlemi: arka plan iş parçacığında çağır. Başarıda null, hatada kullanıcıya gösterilecek metin döner. */
    fun send(title: String, email: String, message: String, token: String): String? {
        val c = URL("$ENDPOINT/send").openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject().put("title", title).put("email", email)
                .put("message", message).put("token", token)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val o = runCatching { JSONObject(stream?.bufferedReader()?.readText().orEmpty()) }.getOrNull()
            if (o?.optBoolean("ok") == true) null
            else when (o?.optString("error")) {
                "captcha" -> "Doğrulama başarısız oldu, tekrar dene."
                "invalid" -> "Alanları kontrol et."
                else -> "Mesaj gönderilemedi. Daha sonra tekrar dene."
            }
        } catch (e: java.io.IOException) {
            "İnternet bağlantısı kurulamadı."
        } finally {
            c.disconnect()
        }
    }
}

private enum class Phase { Intro, Form, Sending, Done }

private class FormState {
    var title by mutableStateOf("")
    var email by mutableStateOf("")
    var msg by mutableStateOf("")
    var token by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var captchaKey by mutableIntStateOf(0)
}

@Composable
fun ContactPage() {
    var phase by remember { mutableStateOf(Phase.Intro) }
    val f = remember { FormState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = phase == Phase.Form || phase == Phase.Done) { phase = Phase.Intro }
    BackHandler(enabled = phase == Phase.Sending) { }   // gönderirken geri tuşu yok sayılır

    fun submit() {
        val problem = when {
            Contact.ENDPOINT.isBlank() -> "İletişim henüz ayarlanmadı."
            f.title.isBlank() || f.msg.isBlank() -> "Başlık ve mesaj boş olamaz."
            !Patterns.EMAIL_ADDRESS.matcher(f.email.trim()).matches() -> "Geçerli bir e-posta gir."
            f.token.isBlank() -> "Lütfen aşağıdaki doğrulamayı tamamla."
            else -> null
        }
        if (problem != null) { f.error = problem; return }
        f.error = null
        phase = Phase.Sending
        scope.launch {
            val t0 = System.currentTimeMillis()
            val err = withContext(Dispatchers.IO) {
                Contact.send(f.title.trim(), f.email.trim(), f.msg.trim(), f.token)
            }
            val wait = 900 - (System.currentTimeMillis() - t0)   // dönen animasyon en az ~1 sn görünsün
            if (wait > 0) delay(wait)
            f.token = ""
            f.captchaKey++   // doğrulama tek kullanımlık
            if (err == null) {
                f.title = ""; f.email = ""; f.msg = ""
                phase = Phase.Done
            } else {
                f.error = err
                phase = Phase.Form
            }
        }
    }

    AnimatedContent(
        targetState = phase,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 14 }) togetherWith fadeOut(tween(150))
        },
        label = "contact"
    ) { p ->
        when (p) {
            Phase.Intro -> ContactIntro { phase = Phase.Form }
            Phase.Form -> ContactForm(f) { submit() }
            Phase.Sending -> ContactSending()
            Phase.Done -> ContactDone { if (phase == Phase.Done) phase = Phase.Intro }
        }
    }
}

@Composable
private fun ContactIntro(onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }

    Column(Modifier.fillMaxWidth().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(120.dp).scale(pop.value).clip(CircleShape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Mail, null, Modifier.size(58.dp), tint = cs.onPrimaryContainer) }
        Spacer(Modifier.height(32.dp))
        Text("Önerileriniz için:", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Fikirlerini, isteklerini ve karşılaştığın sorunları bize yazabilirsin.",
            Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp),
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(0.9f).height(64.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("Mesaj gönder", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ContactForm(f: FormState, onSend: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mesaj gönder", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            f.title, { f.title = it.take(100) }, Modifier.fillMaxWidth(),
            label = { Text("Başlık") }, singleLine = true
        )
        OutlinedTextField(
            f.email, { f.email = it.take(120) }, Modifier.fillMaxWidth(),
            label = { Text("E-posta") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            f.msg, { f.msg = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
            label = { Text("Mesajın") }
        )
        if (f.error != null) {
            Text(f.error!!, color = cs.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onSend, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Gönder", style = MaterialTheme.typography.titleMedium)
        }
        key(f.captchaKey) {
            if (Contact.ENDPOINT.isNotBlank()) CaptchaView { f.token = it }
        }
    }
}

@Composable
private fun ContactSending() {
    Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(96.dp), strokeWidth = 7.dp)
        Text("Gönderiliyor…", Modifier.padding(top = 28.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ContactDone(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { delay(3500); onClose() }
    Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SentCheck(128.dp, cs.primary)
        Text("Gönderildi!", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineMedium)
        Text(
            "Mesajın bize ulaştı, teşekkürler.",
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant, textAlign = TextAlign.Center
        )
        TextButton(onClick = onClose, modifier = Modifier.padding(top = 20.dp)) { Text("Tamam") }
    }
}

/** Daire çizilir, ardından tik işareti çizilerek belirir. */
@Composable
private fun SentCheck(size: androidx.compose.ui.unit.Dp, color: Color) {
    val circle = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        circle.animateTo(1f, tween(450))
        tick.animateTo(1f, tween(350))
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.07f
        drawArc(
            color = color, startAngle = -90f, sweepAngle = 360f * circle.value, useCenter = false,
            topLeft = Offset(sw, sw), size = Size(w - 2 * sw, h - 2 * sw),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        val path = Path().apply {
            moveTo(w * 0.29f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.67f)
            lineTo(w * 0.72f, h * 0.36f)
        }
        val seg = Path()
        val pm = PathMeasure().apply { setPath(path, false) }
        pm.getSegment(0f, pm.length * tick.value, seg, true)
        drawPath(seg, color, style = Stroke(sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Cloudflare Turnstile: Worker'ın /captcha sayfasını gösterir; doğrulanınca token'ı geri verir. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaView(onResult: (String) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(90.dp),
        factory = { c ->
            WebView(c).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onToken(t: String) {
                        Handler(Looper.getMainLooper()).post { onResult(t) }
                    }
                }, "Android")
                loadUrl("${Contact.ENDPOINT}/captcha")
            }
        },
        onRelease = { it.destroy() }
    )
}
