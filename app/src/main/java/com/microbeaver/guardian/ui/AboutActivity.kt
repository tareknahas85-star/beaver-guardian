package com.microbeaver.guardian.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.gcacace.signaturepad.views.SignaturePad
import com.google.firebase.auth.FirebaseAuth
import com.microbeaver.guardian.BuildConfig
import com.microbeaver.guardian.R
import com.microbeaver.guardian.databinding.ActivityAboutBinding
import java.io.ByteArrayOutputStream

/**
 * About / حول
 *
 * Displays:
 *  • Firebase Auth display name (or "ولي الأمر" for anonymous users)
 *  • E-signature — Tarek's official signature is bundled as
 *    res/drawable-nodpi/signature_tarek.png and shown by default.
 *    "تعديل / Edit" reveals a SignaturePad so a different signature can be drawn;
 *    a drawn signature is stored as Base64 in SharedPreferences and takes priority.
 *    "مسح / Clear" discards the drawn one and restores the bundled default.
 *  • Contact info (email, GitHub, LinkedIn, phone)
 *  • App name + version
 *
 * NOTE: the signature never leaves the device — it is not uploaded anywhere.
 *
 * IMPORTANT: google-services.json contains placeholder values.
 *            Replace it with the real file from your Firebase Console
 *            before building a production APK.
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var b: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)

        // The app theme is *.NoActionBar, so the layout supplies its own toolbar.
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "About / حول"
        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // ── User info from Firebase Auth ──────────────────────────────────────
        val user = FirebaseAuth.getInstance().currentUser
        b.tvDisplayName.text = user?.displayName?.takeIf { it.isNotBlank() } ?: "ولي الأمر"
        val email = user?.email?.takeIf { it.isNotBlank() }
        b.tvRole.text = if (email != null) "$email — ولي الأمر" else "Tarek Nahas — ولي الأمر"

        // ── App version ───────────────────────────────────────────────────────
        b.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        // ── Signature: custom drawn one if present, otherwise the bundled default
        val custom = loadSignature(this)
        if (custom != null) showPreview(custom) else showDefaultSignature()

        b.signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() { b.tvSignatureHint.visibility = View.GONE }
            override fun onSigned()       { b.btnSaveSignature.isEnabled = true }
            override fun onClear() {
                b.btnSaveSignature.isEnabled = false
                b.tvSignatureHint.visibility = View.VISIBLE
            }
        })

        // ── Edit: reveal the drawing pad ──────────────────────────────────────
        b.btnEditSignature.setOnClickListener {
            b.signaturePad.clear()
            showPad()
        }

        // ── Save the drawn signature ──────────────────────────────────────────
        b.btnSaveSignature.setOnClickListener {
            if (b.signaturePad.isEmpty) {
                toast("ارسم توقيعك أولاً / Please draw your signature first")
                return@setOnClickListener
            }
            val bmp = b.signaturePad.transparentSignatureBitmap
            saveSignature(this, bitmapToBase64(bmp))
            showPreview(bmp)
            toast("تم حفظ التوقيع ✓")
        }

        // ── Clear: drop the custom signature, restore the bundled default ─────
        b.btnClearSignature.setOnClickListener {
            clearSignature(this)
            b.signaturePad.clear()
            showDefaultSignature()
            toast("تمت استعادة التوقيع الأصلي / Default signature restored")
        }

        // ── Contact rows ──────────────────────────────────────────────────────
        b.rowLinkedIn.setOnClickListener { openUrl(LINKEDIN_URL) }
    }

    // ── Display modes ─────────────────────────────────────────────────────────

    /** Drawing mode: pad + hint visible, Save/Clear shown, Edit hidden. */
    private fun showPad() {
        b.signaturePad.visibility       = View.VISIBLE
        b.tvSignatureHint.visibility    = View.VISIBLE
        b.ivSignaturePreview.visibility = View.GONE

        b.btnEditSignature.visibility   = View.GONE
        b.btnClearSignature.visibility  = View.VISIBLE
        b.btnSaveSignature.visibility   = View.VISIBLE
        b.btnSaveSignature.isEnabled    = false
    }

    /** Preview mode: the given bitmap is shown, only Edit is offered. */
    private fun showPreview(bmp: Bitmap) {
        b.ivSignaturePreview.setImageBitmap(bmp)
        enterPreviewMode()
    }

    /** Preview mode showing the bundled official signature. */
    private fun showDefaultSignature() {
        b.ivSignaturePreview.setImageResource(R.drawable.signature_tarek)
        enterPreviewMode()
    }

    private fun enterPreviewMode() {
        b.signaturePad.visibility       = View.GONE
        b.tvSignatureHint.visibility    = View.GONE
        b.ivSignaturePreview.visibility = View.VISIBLE

        b.btnEditSignature.visibility   = View.VISIBLE
        b.btnClearSignature.visibility  = View.GONE
        b.btnSaveSignature.visibility   = View.GONE
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast("لا يوجد تطبيق لفتح الرابط / No app can open this link")
        }
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
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed(); return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val PREFS   = "about_prefs"
        private const val KEY_SIG = "e_signature_base64"
        private const val LINKEDIN_URL = "https://www.linkedin.com/in/tarek-nahas-669322382/"
    }
}
