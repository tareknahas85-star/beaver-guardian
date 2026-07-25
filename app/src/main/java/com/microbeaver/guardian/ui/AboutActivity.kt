package com.microbeaver.guardian.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.gcacace.signaturepad.views.SignaturePad
import com.google.firebase.auth.FirebaseAuth
import com.microbeaver.guardian.BuildConfig
import com.microbeaver.guardian.databinding.ActivityAboutBinding
import java.io.ByteArrayOutputStream

/**
 * About / حول
 *
 * Displays:
 *  • Firebase Auth display name (or "ولي الأمر" for anonymous users)
 *  • E-signature pad — draw with finger, save as Base64 in SharedPreferences
 *  • Contact info (email, GitHub, phone)
 *  • App name + version
 *
 * NOTE: Signature is persisted locally; it is never uploaded.
 *
 * IMPORTANT: google-services.json contains placeholder values.
 *            Replace it with the real file from your Firebase Console
 *            before building a production APK.
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var b: ActivityAboutBinding
    private var signatureMode = SignatureMode.DRAWING   // DRAWING | PREVIEW

    private enum class SignatureMode { DRAWING, PREVIEW }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Back arrow in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "About / حول"

        // ── User info from Firebase Auth ──────────────────────────────────────
        val user = FirebaseAuth.getInstance().currentUser
        val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "ولي الأمر"
        val email      = user?.email?.takeIf { it.isNotBlank() }
        b.tvDisplayName.text = displayName
        b.tvRole.text = if (email != null) "$email — ولي الأمر" else "Tarek Nahas — ولي الأمر"

        // ── App version ───────────────────────────────────────────────────────
        b.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        // ── Signature: load saved or show pad ────────────────────────────────
        val saved = loadSignature(this)
        if (saved != null) {
            showPreview(saved)
        } else {
            showPad()
        }

        // ── SignaturePad listener ─────────────────────────────────────────────
        b.signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {}
            override fun onSigned() { b.btnSaveSignature.isEnabled = true }
            override fun onClear() { b.btnSaveSignature.isEnabled = false }
        })
        b.btnSaveSignature.isEnabled = false

        // ── Save button ───────────────────────────────────────────────────────
        b.btnSaveSignature.setOnClickListener {
            if (b.signaturePad.isEmpty) {
                Toast.makeText(this, "ارسم توقيعك أولاً / Please draw your signature first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val bmp = b.signaturePad.transparentSignatureBitmap
            val b64 = bitmapToBase64(bmp)
            saveSignature(this, b64)
            showPreview(bmp)
            Toast.makeText(this, "تم حفظ التوقيع ✓", Toast.LENGTH_SHORT).show()
        }

        // ── Clear button ──────────────────────────────────────────────────────
        b.btnClearSignature.setOnClickListener {
            clearSignature(this)
            b.signaturePad.clear()
            showPad()
        }
    }

    // ── Mode helpers ──────────────────────────────────────────────────────────

    private fun showPad() {
        signatureMode = SignatureMode.DRAWING
        b.signaturePad.visibility  = View.VISIBLE
        b.ivSignaturePreview.visibility = View.GONE
    }

    private fun showPreview(bmp: Bitmap) {
        signatureMode = SignatureMode.PREVIEW
        b.ivSignaturePreview.setImageBitmap(bmp)
        b.signaturePad.visibility  = View.GONE
        b.ivSignaturePreview.visibility = View.VISIBLE
        b.btnSaveSignature.isEnabled = false
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun bitmapToBase64(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    }

    private fun base64ToBitmap(b64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun saveSignature(c: Context, base64: String) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SIG, base64).apply()

    private fun clearSignature(c: Context) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_SIG).apply()

    private fun loadSignature(c: Context): Bitmap? {
        val b64 = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SIG, null) ?: return null
        return base64ToBitmap(b64)
    }

    // ── Back navigation ───────────────────────────────────────────────────────

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { onBackPressedDispatcher.onBackPressed(); return true }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val PREFS   = "about_prefs"
        private const val KEY_SIG = "e_signature_base64"
    }
}
