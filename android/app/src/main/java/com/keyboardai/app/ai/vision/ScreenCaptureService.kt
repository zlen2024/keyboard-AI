package com.keyboardai.app.ai.vision

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.FileOutputStream

/**
 * Foreground service (type=mediaProjection) that captures a single frame of the
 * screen and writes it to [ScreenCapture.captureFile], then broadcasts
 * [ScreenCapture.ACTION_CAPTURE_READY]. Started by [CaptureActivity] once the
 * user has granted consent.
 */
class ScreenCaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()

        val resultCode = intent?.getIntExtra(ScreenCapture.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(ScreenCapture.EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(ScreenCapture.EXTRA_RESULT_DATA)
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)?.also {
            it.registerCallback(object : MediaProjection.Callback() {}, handler)
        }
        if (projection == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Let the previous app and keyboard return to the foreground first.
        handler.postDelayed({ capture() }, CAPTURE_DELAY_MS)
        return START_NOT_STICKY
    }

    private fun capture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = projection?.createVirtualDisplay(
            "kbai-capture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler,
        )

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = imageToBitmap(image, width, height)
                FileOutputStream(ScreenCapture.captureFile(this)).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                sendBroadcast(Intent(ScreenCapture.ACTION_CAPTURE_READY).setPackage(packageName))
            } catch (_: Throwable) {
                // best-effort capture; keyboard simply won't attach an image
            } finally {
                image.close()
                cleanup()
                stopSelf()
            }
        }, handler)
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888,
        )
        padded.copyPixelsFromBuffer(plane.buffer)
        if (padded.width == width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }

    private fun cleanup() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        projection?.stop(); projection = null
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun startInForeground() {
        val channelId = "screen_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId, "Screenshot", NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Keyboard AI")
            .setContentText("Capturing screen…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private companion object {
        const val NOTIF_ID = 42
        const val CAPTURE_DELAY_MS = 450L
    }
}
