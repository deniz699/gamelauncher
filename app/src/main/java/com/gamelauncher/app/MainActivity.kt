package com.gamelauncher.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState
    private var resumed = false

    // Telefon teması değişince simgeyi eşitle. Uygulama ekrandayken yapılırsa Android uygulamayı kapatır; o yüzden sadece arka plandayken
    private val themeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!resumed) IconSwitcher.sync(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = AppState(applicationContext)
        setContent {
            LauncherTheme(state.themeMode, state.accent, state.dynamic) { App(state) }
        }
        ContextCompat.registerReceiver(
            this, themeReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        state.sync()
    }

    override fun onStop() {
        resumed = false
        // Uygulamadan çıkılırken simgeyi güncelle (ayar değiştiyse ya da telefon teması değiştiyse)
        IconSwitcher.sync(applicationContext)
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(themeReceiver) }
        super.onDestroy()
    }
}
