package com.keyboardai.app.ai.vision

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * A transparent, no-UI activity whose only job is to show the system
 * MediaProjection consent dialog — an [android.inputmethodservice.InputMethodService]
 * can't do that itself. On consent it hands the result to
 * [ScreenCaptureService] and finishes, returning the user to the app they were
 * in so the service can grab that frame.
 */
class CaptureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCapture.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ScreenCapture.EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(this, service)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val REQUEST_CAPTURE = 7011
    }
}
