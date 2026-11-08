package com.gamelauncher.app

import android.os.Build
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView

/** Şeffaf "cam" panel rengi (alttan açılan panellerde containerColor olarak kullanılır). */
@Composable
fun glassColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

/** Panelin arkasındaki her şeyi bulanıklaştırır (Android 12+; cihaz pencere bulanıklığını destekliyorsa). */
@Composable
fun GlassBlurBehind(radius: Int = 40) {
    val view = LocalView.current
    SideEffect {
        if (Build.VERSION.SDK_INT < 31) return@SideEffect
        view.post {
            runCatching {
                val root = view.rootView
                val lp = root.layoutParams as? WindowManager.LayoutParams ?: return@runCatching
                if (lp.blurBehindRadius != radius) {
                    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    lp.blurBehindRadius = radius
                    view.context.getSystemService(WindowManager::class.java).updateViewLayout(root, lp)
                }
            }
        }
    }
}
