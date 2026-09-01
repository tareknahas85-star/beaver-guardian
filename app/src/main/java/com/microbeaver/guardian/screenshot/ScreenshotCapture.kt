package com.microbeaver.guardian.screenshot

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.ui.ParentActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screenshot Capture Service — captures the child's screen on demand.
 * 
 * Flow:
 * 1. Parent sends "screenshot" command via Firebase → Child receives via FCM
 * 2. This service starts the MediaProjection (shows the system permission dialog)
 * 3. Screenshot is captured, uploaded to Firebase Storage (or base64 in DB for small images)
 * 4. Parent receives notification with screenshot preview
 * 
 * Requires: READ_EXTERNAL_STORAGE (API < 29) or none (API 29+)
 * Requires: Manifest.permission.FOREGROUND_SERVICE
 */
class ScreenshotCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    companion object {
        const val TAG = "ScreenshotCapture"
        const val ACTION_REQUEST_SCREENSHOT = "com.microbeaver.guardian.REQUEST_SCREENSHOT"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        
        private var pendingCallback: ((Bitmap?) -> Unit)? = null

        fun requestScreenshot(context: Context, callback: ((Bitmap?) -> Unit)? = null) {
            pendingCallback = callback
            val intent = Intent(context, ScreenshotCaptureService::class.java).apply {
                action = ACTION_REQUEST_SCREENSHOT
            }
            context.startService(intent)
        }

        fun stop() {
            pendingCallback = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REQUEST_SCREENSHOT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != -1 && resultData != null) {
                    startCapture(resultCode, resultData)
                } else {
                    // Request permission from activity
                    requestProjectionPermission()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun requestProjectionPermission() {
        // This will be called by the activity that started the service
        stopSelf()
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val ctx = applicationContext
        
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release()
            }
        }, null)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "BeaverScreenshot",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        // Wait for image to be ready
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                
                // Crop to actual screen size
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                
                // Upload
                uploadScreenshot(cropped)
                
                image.close()
                release()
            }
        }, null)
    }

    private fun uploadScreenshot(bitmap: Bitmap) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val childId = prefs.childId ?: return

        // Save locally first
        try {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "BeaverGuardian")
            if (!dir.exists()) dir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "screenshot_$timestamp.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            // Convert to base64 for Firebase (limited to ~1MB in Realtime DB)
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream) // 50% quality for smaller size
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            // Store in Firebase under childId/screenshots/
            val ref = FirebaseDatabase.getInstance()
                .getReference("families")
                .child(prefs.familyCode)
                .child("children")
                .child(childId)
                .child("screenshots")
                .push()

            val screenshotData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "imageBase64" to base64,
                "localPath" to file.absolutePath
            )
            ref.setValue(screenshotData)

            // Notify parent
            val notifyRef = FirebaseDatabase.getInstance()
                .getReference("families")
                .child(prefs.familyCode)
                .child("notifications")
                .push()
            notifyRef.setValue(mapOf(
                "type" to "screenshot_received",
                "childId" to childId,
                "timestamp" to System.currentTimeMillis(),
                "title" to "Screenshot Received",
                "body" to "Screenshot captured from child's device"
            ))

            pendingCallback?.invoke(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            pendingCallback?.invoke(null)
        }
    }

    private fun release() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        mediaProjection = null
        stopSelf()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }
}
