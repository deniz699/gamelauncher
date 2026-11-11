package com.gamelauncher.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/** Görünmez ara ekran: sistemin "ekran kaydı izni" penceresini açar, izin gelirse kaydı başlatan hizmeti çalıştırır. */
class ScreenRecordActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ScreenRecordService.recording) { finish(); return }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ScreenRecordService::class.java)
                .putExtra(ScreenRecordService.EXTRA_CODE, resultCode)
                .putExtra(ScreenRecordService.EXTRA_DATA, data)
            ContextCompat.startForegroundService(this, i)
        }
        finish()
    }

    private companion object {
        const val REQ = 77
    }
}
