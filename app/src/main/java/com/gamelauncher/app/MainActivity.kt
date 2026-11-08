package com.gamelauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = AppState(applicationContext)
        setContent {
            LauncherTheme(state.themeMode, state.accent, state.dynamic) { App(state) }
        }
    }

    override fun onResume() {
        super.onResume()
        state.sync()
    }

    override fun onStop() {
        // Uygulamadan çıkılırken simgeyi güncelle (ayar değiştiyse ya da telefon teması değiştiyse)
        IconSwitcher.sync(applicationContext)
        super.onStop()
    }

}
