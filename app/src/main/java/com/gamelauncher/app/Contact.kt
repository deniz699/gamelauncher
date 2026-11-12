package com.gamelauncher.app

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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

@Composable
fun ContactPage() {
    val cs = MaterialTheme.colorScheme
    var form by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    BackHandler(enabled = form) { form = false }

    if (!form) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Önerileriniz için:", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { sent = false; form = true }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Mesaj gönder")
            }
            if (sent) {
                Text(
                    "Mesajın gönderildi, teşekkürler!",
                    Modifier.padding(top = 12.dp), color = cs.primary, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    } else {
        ContactForm(onDone = { sent = true; form = false })
    }
}

@Composable
private fun ContactForm(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var captchaKey by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            title, { title = it.take(100) }, Modifier.fillMaxWidth(),
            label = { Text("Başlık") }, singleLine = true
        )
        OutlinedTextField(
            email, { email = it.take(120) }, Modifier.fillMaxWidth(),
            label = { Text("E-posta") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            msg, { msg = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
            label = { Text("Mesajın") }
        )
        if (error != null) {
            Text(error!!, color = cs.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val problem = when {
                    Contact.ENDPOINT.isBlank() -> "İletişim henüz ayarlanmadı."
                    title.isBlank() || msg.isBlank() -> "Başlık ve mesaj boş olamaz."
                    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Geçerli bir e-posta gir."
                    token.isBlank() -> "Lütfen aşağıdaki doğrulamayı tamamla."
                    else -> null
                }
                if (problem != null) {
                    error = problem
                } else {
                    error = null
                    busy = true
                    scope.launch {
                        val err = withContext(Dispatchers.IO) {
                            Contact.send(title.trim(), email.trim(), msg.trim(), token)
                        }
                        busy = false
                        if (err == null) {
                            onDone()
                        } else {
                            error = err
                            token = ""
                            captchaKey++   // doğrulama tek kullanımlık: yenile
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Gönderiliyor…" else "Gönder") }

        key(captchaKey) {
            if (Contact.ENDPOINT.isNotBlank()) CaptchaView { token = it }
        }
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
