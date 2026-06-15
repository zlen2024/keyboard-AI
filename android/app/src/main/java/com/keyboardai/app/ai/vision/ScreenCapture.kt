package com.keyboardai.app.ai.vision

import android.content.Context
import java.io.File

/**
 * Shared names/paths and the live state of the screenshot capture pipeline.
 *
 * The keyboard reasons over the screen on every prompt. To avoid asking for the
 * system MediaProjection consent on each shot, [ScreenCaptureService] keeps one
 * projection alive for the session and grabs a fresh frame on demand;
 * [projectionActive] lets the keyboard know whether it still needs consent.
 */
object ScreenCapture {
    /** Start the projection (carries the consent result) and grab a first frame. */
    const val ACTION_START = "com.keyboardai.app.CAPTURE_START"
    /** Grab a fresh frame using the already-granted projection. */
    const val ACTION_CAPTURE = "com.keyboardai.app.CAPTURE_NOW"
    /** Tear the projection down (drops the system "casting" indicator). */
    const val ACTION_STOP = "com.keyboardai.app.CAPTURE_STOP"
    /** Broadcast back to the keyboard once a frame is written (or capture failed). */
    const val ACTION_CAPTURE_READY = "com.keyboardai.app.CAPTURE_READY"

    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_RESULT_DATA = "result_data"
    const val EXTRA_SUCCESS = "success"

    /** True while a MediaProjection is held, so further captures need no consent. */
    @Volatile
    var projectionActive: Boolean = false

    /** Where a captured frame is written for the keyboard to pick up. */
    fun captureFile(context: Context): File =
        File(context.applicationContext.filesDir, "capture.png")
}
