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
        UpdateWorker.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        state = AppState(applicationContext)
        setContent {
            LauncherTheme(state.themeMode, state.accent, state.dynamic, state.fontStep) { App(state) }
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
