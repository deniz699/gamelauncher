package com.gamelauncher.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/** Kenar paneli ayarları (SharedPreferences "prefs"). EdgeService de buradan okur. */
object EdgePrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    const val DEFAULT_LEN_DP = 56

    fun side(ctx: Context): Int = p(ctx).getInt("edge_side", 0)          // 0 sol, 1 sağ
    fun setSide(ctx: Context, v: Int) = p(ctx).edit().putInt("edge_side", v).apply()

    fun lenDp(ctx: Context): Int = p(ctx).getInt("edge_len", DEFAULT_LEN_DP)
    fun setLenDp(ctx: Context, v: Int) = p(ctx).edit().putInt("edge_len", v).apply()

    fun yFrac(ctx: Context): Float = p(ctx).getFloat("edge_y", 0.5f)      // çizginin ortası, ekran yüksekliğinin oranı
    fun setYFrac(ctx: Context, v: Float) = p(ctx).edit().putFloat("edge_y", v).apply()

    fun apps(ctx: Context): List<String> =
        (p(ctx).getString("edge_apps", "") ?: "").split(",").filter { it.isNotBlank() }
    fun setApps(ctx: Context, list: List<String>) = p(ctx).edit().putString("edge_apps", list.joinToString(",")).apply()
}

private data class PickApp(val pkg: String, val name: String)

private fun launchableApps(ctx: Context): List<PickApp> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { PickApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.name.lowercase() }
}

/** Ayarlar > Oyun içi kenar paneli altındaki konum / uzunluk / uygulama seçenekleri. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgePanelSettings() {
    val ctx = LocalContext.current
    var side by remember { mutableIntStateOf(EdgePrefs.side(ctx)) }
    var len by remember { mutableIntStateOf(EdgePrefs.lenDp(ctx)) }
    var y by remember { mutableFloatStateOf(EdgePrefs.yFrac(ctx)) }
    var apps by remember { mutableStateOf(EdgePrefs.apps(ctx)) }
    var picker by remember { mutableStateOf(false) }

    Text("Çizgi konumu", Modifier.padding(top = 14.dp, bottom = 6.dp), style = MaterialTheme.typography.bodyMedium)
    SegmentedChoice(listOf("Sol kenar", "Sağ kenar"), side) { side = it; EdgePrefs.setSide(ctx, it) }

    Text("Çizgi uzunluğu: $len dp", Modifier.padding(top = 12.dp))
    Slider(
        value = len.toFloat(),
        onValueChange = { len = it.roundToInt(); EdgePrefs.setLenDp(ctx, len) },
        valueRange = 28f..140f
    )

    Text("Yüksekliği: %${(y * 100).roundToInt()} (üst → alt)")
    Slider(
        value = y,
        onValueChange = { y = it; EdgePrefs.setYFrac(ctx, it) },
        valueRange = 0.05f..0.95f
    )
    Text(
        "Oyundayken çizgiye basılı tutup sürükleyerek de aşağı yukarı ya da öbür kenara taşıyabilirsin.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text("Panele uygulama ekle", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyMedium)
    Text(
        if (apps.isEmpty()) "Panelde yalnızca burada seçtiklerin görünür. Oyun, tarayıcı, mesajlaşma, not… istediğin uygulamayı ekleyebilirsin."
        else "${apps.size} uygulama panelde görünüyor.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SoftButton("Uygulama seç", { picker = true }, Modifier.padding(top = 8.dp))

    if (picker) {
        val all = remember { launchableApps(ctx) }
        val initial = remember { EdgePrefs.apps(ctx).toSet() }
        // Açılışta seçili olanlar üstte; seçim sırasında liste yerinde kalır
        val ordered = remember { all.sortedWith(compareByDescending<PickApp> { it.pkg in initial }.thenBy { it.name.lowercase() }) }
        var query by remember { mutableStateOf("") }
        val shown = ordered.filter { it.name.contains(query, ignoreCase = true) }

        ModalBottomSheet(onDismissRequest = { picker = false }) {
            Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
                Text("Panele eklenecek uygulamalar", style = MaterialTheme.typography.titleLarge)
                Text(
                    "İşaretlediklerin oyun içi kenar panelinde gösterilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Uygulama ara") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Temizle") }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                )
                LazyColumn {
                    items(shown, key = { it.pkg }) { a ->
                        val checked = a.pkg in apps
                        val icon = remember(a.pkg) {
                            runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                        }
                        fun toggle() {
                            apps = if (checked) apps - a.pkg else apps + a.pkg
                            EdgePrefs.setApps(ctx, apps)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (icon != null) Image(icon, null, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(a.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Checkbox(checked = checked, onCheckedChange = { toggle() })
                        }
                    }
                }
            }
        }
    }
}
