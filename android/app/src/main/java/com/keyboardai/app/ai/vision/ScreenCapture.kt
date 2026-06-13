package com.keyboardai.app.ai.vision

import android.content.Context
import java.io.File

/** Shared names/paths for the screenshot capture pipeline. */
object ScreenCapture {
    const val ACTION_CAPTURE_READY = "com.keyboardai.app.CAPTURE_READY"
    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_RESULT_DATA = "result_data"

    /** Where a captured frame is written for the keyboard to pick up. */
    fun captureFile(context: Context): File =
        File(context.applicationContext.filesDir, "capture.png")
}
