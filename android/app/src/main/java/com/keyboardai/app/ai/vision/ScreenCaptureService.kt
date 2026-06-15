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
 * Foreground service (type=mediaProjection) that holds a single MediaProjection
 * for the keyboard session and grabs one screen frame per request. Started by
 * [CaptureActivity] with [ScreenCapture.ACTION_START] once the user consents;
 * thereafter the keyboard sends [ScreenCapture.ACTION_CAPTURE] for each shot
 * (no new consent), and [ScreenCapture.ACTION_STOP] to release everything.
 *
 * Each capture spins up a fresh one-shot [VirtualDisplay]/[ImageReader] (so a
 * frame is produced even when the screen is static) and releases it afterwards,
 * while the projection itself stays alive.
 */
class ScreenCaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ScreenCapture.ACTION_START -> handleStart(intent)
            ScreenCapture.ACTION_CAPTURE -> handleCapture()
            ScreenCapture.ACTION_STOP -> { teardown(); stopSelf() }
            else -> if (projection == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val resultCode = intent.getIntExtra(ScreenCapture.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(ScreenCapture.EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(ScreenCapture.EXTRA_RESULT_DATA)
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            broadcast(false); stopSelf(); return
        }

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)?.also {
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    ScreenCapture.projectionActive = false
                    projection = null
                }
            }, handler)
        }
        if (projection == null) {
            ScreenCapture.projectionActive = false
            broadcast(false); stopSelf(); return
        }
        ScreenCapture.projectionActive = true
        // Let the consent dialog disappear and the underlying app re-show first.
        handler.postDelayed({ captureOnce() }, START_CAPTURE_DELAY_MS)
    }

    private fun handleCapture() {
        if (projection == null) { broadcast(false); return }
        handler.postDelayed({ captureOnce() }, CAPTURE_DELAY_MS)
    }

    private fun captureOnce() {
        val proj = projection ?: run { broadcast(false); return }
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var display: VirtualDisplay? = null
        var handled = false

        reader.setOnImageAvailableListener({ r ->
            if (handled) {
                runCatching { r.acquireLatestImage()?.close() }
                return@setOnImageAvailableListener
            }
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            handled = true
            val ok = runCatching {
                val bitmap = imageToBitmap(image, width, height)
                FileOutputStream(ScreenCapture.captureFile(this)).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }.isSuccess
            image.close()
            display?.release()
            r.close()
            broadcast(ok)
        }, handler)

        display = runCatching {
            proj.createVirtualDisplay(
                "kbai-capture", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler,
            )
        }.getOrNull()

        if (display == null) {
            reader.close()
            broadcast(false)
        }
    }

    private fun broadcast(success: Boolean) {
        sendBroadcast(
            Intent(ScreenCapture.ACTION_CAPTURE_READY)
                .setPackage(packageName)
                .putExtra(ScreenCapture.EXTRA_SUCCESS, success)
        )
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

    private fun teardown() {
        ScreenCapture.projectionActive = false
        projection?.stop()
        projection = null
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun startInForeground() {
        val channelId = "screen_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId, "Screen vision", NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Keyboard AI")
            .setContentText("Vision is on — reading your screen on prompts")
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
        const val START_CAPTURE_DELAY_MS = 450L
        const val CAPTURE_DELAY_MS = 150L
    }
}
