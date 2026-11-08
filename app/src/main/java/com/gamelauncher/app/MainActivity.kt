package com.gamelauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = AppState(applicationContext)
        setContent {
            val sysDark = isSystemInDarkTheme()
            LaunchedEffect(state.iconMode, state.themeMode, sysDark) { state.applyIcon(sysDark) }
            LauncherTheme(state.themeMode, state.accent, state.dynamic) { App(state) }
        }
    }

    override fun onResume() {
        super.onResume()
        state.sync()
    }
}
