package com.gamelauncher.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Tek bir widget'ın renk / saydamlık ayarı.
 * Ana ekranda widget'a basılı tutunca çıkan "Ayarlar" düğmesi bunu açar (reconfigurable widget).
 * Widget ilk eklendiğinde de bir kez açılır.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Geri tuşuyla çıkılsa bile widget eklenmiş kalsın
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        // İlk ekleme: sistem güncelleme göndermez, widget'ı biz çizeriz
        WidgetUi.updateOne(this, widgetId)

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        setContent {
            LauncherTheme(prefs.getInt("theme", 0), prefs.getInt("accent", 0), prefs.getBoolean("dynamic", false)) {
                ConfigScreen(widgetId, provider) { finish() }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetUi.updateOne(this, widgetId)
    }
}

private fun kindName(provider: String?) = when (provider) {
    GamesWidget::class.java.name -> "Oyunlar ve süreler"
    TempWidget::class.java.name -> "Sıcaklıklar"
    InfoWidget::class.java.name -> "Telefon özeti"
    else -> "Widget"
}

@Composable
private fun ConfigScreen(id: Int, provider: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var custom by remember { mutableStateOf(WidgetStyle.hasCustom(ctx, id)) }
    var st by remember { mutableStateOf(WidgetStyle.load(ctx, id)) }

    // Kaydırıcıyı sürüklerken her karede widget'ı yeniden çizmek menüyü kasıyordu:
    // renk hemen saklanır (hafif), ana ekrandaki widget ise kısa bir bekleme sonrası bir kez güncellenir.
    var rev by remember { mutableIntStateOf(0) }
    LaunchedEffect(rev) {
        if (rev > 0) {
            delay(250)
            WidgetUi.updateOne(ctx, id)
        }
    }
    val preview = remember { WidgetUi.previewData(ctx) }

    fun change(n: WStyle) {
        st = n
        custom = true
        WidgetStyle.saveFor(ctx, id, n)
        rev++
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text("Widget'ı düzenle", style = MaterialTheme.typography.titleLarge)
            Text(
                kindName(provider),
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (custom) "Bu widget kendi renklerini kullanıyor."
                else "Şu an uygulamadaki varsayılan renkleri kullanıyor; bir şeyi değiştirince yalnızca bu widget etkilenir.",
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Önizleme
            Text("Önizleme", Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
            val bmp = remember(st) { WidgetUi.previewBitmap(provider, st, preview) }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(), null,
                        Modifier.fillMaxWidth(if (provider == InfoWidget::class.java.name) 1f else 0.7f)
                            .aspectRatio(bmp.width.toFloat() / bmp.height)
                    )
                } else {
                    // Oyun listesi: basit örnek kart
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(Color(st.bgWithAlpha)).padding(14.dp)
                    ) {
                        Text("Oyunlarım • Bugün 1sa 20dk", color = Color(st.text), style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Örnek oyun", Modifier.weight(1f), color = Color(st.text))
                            Text("12sa 05dk", color = Color(st.accent))
                        }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Başka oyun", Modifier.weight(1f), color = Color(st.text))
                            Text("3sa 40dk", color = Color(st.accent))
                        }
                    }
                }
            }

            StyleEditor(st, showTempColors = provider == TempWidget::class.java.name) { change(it) }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(
                    "Varsayılana sıfırla",
                    {
                        WidgetStyle.clearFor(ctx, id)
                        custom = false
                        st = WidgetStyle.load(ctx, id)
                        WidgetUi.updateOne(ctx, id)
                    },
                    Modifier.weight(1f)
                )
                PlayButton(
                    onClick = {
                        WidgetUi.updateOne(ctx, id)
                        onDone()
                    },
                    modifier = Modifier.weight(1f),
                    label = "Tamam"
                )
            }
            Text(
                "\"Varsayılana sıfırla\" bu widget'a verdiğin rengi siler; widget, Ayarlar'daki varsayılan renklere döner.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
