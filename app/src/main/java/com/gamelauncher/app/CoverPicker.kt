package com.gamelauncher.app

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private class CoverCandidate(val bytes: ByteArray, val thumb: ImageBitmap)

private fun decodeThumb(bytes: ByteArray): ImageBitmap? = runCatching {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
    var sample = 1
    while (b.outWidth / sample > 400) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}.getOrNull()

/**
 * Oyun istatistik sayfasındaki kapak seçici.
 * Açılınca internetten otomatik öneri getirir; galeriden seçme ve sıfırlama da burada.
 */
@Composable
fun CoverPickerCard(s: AppState, g: Game) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var cands by remember(g.pkg) { mutableStateOf<List<CoverCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) s.setCover(g.pkg, uri)
    }

    LaunchedEffect(g.pkg, tick) {
        loading = true
        msg = null
        val res = withContext(Dispatchers.IO) {
            runCatching {
                val urls = CoverFetcher.suggestions(g.name, g.pkg)
                coroutineScope {
                    urls.map { u ->
                        async {
                            CoverFetcher.download(u)?.let { bytes ->
                                decodeThumb(bytes)?.let { CoverCandidate(bytes, it) }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            }
        }
        loading = false
        res.onSuccess { list ->
            cands = list
            if (list.isEmpty()) msg = "Bu oyun için öneri bulunamadı. Galeriden seçebilirsin."
        }.onFailure {
            msg = "İnternete bağlanılamadı. Galeriden seçebilirsin."
        }
    }

    FlatCard(pad = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kapak seç", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { tick++ }, enabled = !loading) {
                Icon(Icons.Default.Refresh, "Önerileri yenile")
            }
        }
        if (loading) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("İnternetten öneriler aranıyor…", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }
        msg?.let { Text(it, Modifier.padding(top = 8.dp), fontSize = 12.sp, color = cs.onSurfaceVariant) }
        if (cands.isNotEmpty()) {
            Text(
                "Önerilen görseller — dokununca kapak olur",
                Modifier.padding(top = 8.dp, bottom = 10.dp), fontSize = 12.sp, color = cs.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cands) { c ->
                    Image(
                        c.thumb, "Kapak önerisi",
                        Modifier.size(104.dp).flatClick {
                            val ok = s.setCoverBytes(g.pkg, c.bytes)
                            Toast.makeText(ctx, if (ok) "Kapak güncellendi" else "Kapak kaydedilemedi", Toast.LENGTH_SHORT).show()
                        }.clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Galeriden seç", { picker.launch("image/*") }, Modifier.weight(1f), icon = Icons.Default.ImageIcon)
            if (g.cover != null) SoftButton("Sıfırla", { s.clearCover(g.pkg) }, Modifier.weight(1f))
        }
    }
}
