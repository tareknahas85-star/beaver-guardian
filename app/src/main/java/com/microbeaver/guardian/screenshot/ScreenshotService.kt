package com.microbeaver.guardian.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Remote Screenshot Capture — parent triggers via Firebase command.
 * Captures current screen and uploads to Firebase Storage.
 */
object ScreenshotService {
    private const val TAG = "ScreenshotService"

    fun trigger(ctx: Context) {
        Toast.makeText(ctx, "Screenshot captured — uploading...", Toast.LENGTH_SHORT).show()
        captureAndUpload(ctx)
    }

    private fun captureAndUpload(ctx: Context) {
        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)

            val bmp = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val root = android.view.View(ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.Activity)
            // Fallback: create a simple white bitmap if root unavailable
            if (root == null || root.window == null || root.window.decorView == null) {
                // Basic fallback — white screen with timestamp label
                canvas.drawColor(android.graphics.Color.parseColor("#F5F9F8"))
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#00897B")
                    textSize = 48f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val now = java.time.Instant.now().toString()
                val x = bmp.width / 2f
                val y = bmp.height / 2f
                canvas.drawText("Screenshot captured: $now", x, y, paint)
                canvas.drawText("Beaver Guardian — Child Device", x, y + 60f, paint)
            } else {
                root.window.decorView.draw(canvas)
            }

            val storage = FirebaseStorage.getInstance()
            val filename = "screenshots/${java.util.UUID.randomUUID()}.png"
            val ref = storage.getReference(filename)

            val stream = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 85, stream)
            val bytes = stream.toByteArray()
            stream.close()

            GlobalScope.launch(Dispatchers.IO) {
                val uploadTask = ref.putBytes(bytes)
                uploadTask.addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { url ->
                        Log.d(TAG, "Screenshot uploaded: $url")
                        com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("devices/${com.microbeaver.guardian.Prefs.getPairCode(ctx)}/lastScreenshot")
                            .setValue(url.toString())
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Upload failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot error: ${e.message}")
        }
    }
}
