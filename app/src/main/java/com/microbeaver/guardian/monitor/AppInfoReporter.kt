package com.microbeaver.guardian.monitor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import com.microbeaver.guardian.data.FirebaseRepo
import java.io.ByteArrayOutputStream

/**
 * Uploads the label + launcher icon of each USED app to Firebase once, so the
 * parent dashboard can show real app icons and names next to usage.
 */
object AppInfoReporter {
    private val uploaded = HashSet<String>()

    fun reportUsedApps(ctx: Context, code: String, usage: Map<String, Int>) {
        val pm = ctx.packageManager
        for (pkg in usage.keys) {
            if (uploaded.contains(pkg)) continue
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(ai).toString()
                val icon = pm.getApplicationIcon(ai)
                FirebaseRepo.reportApp(code, pkg, label, drawableToBase64(icon))
                uploaded.add(pkg)
            } catch (_: Exception) {
            }
        }
    }

    private fun drawableToBase64(d: Drawable): String {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
}
