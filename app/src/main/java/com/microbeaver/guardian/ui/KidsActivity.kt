package com.microbeaver.guardian.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.databinding.ActivityKidsBinding

class KidsActivity : AppCompatActivity() {
    private lateinit var b: ActivityKidsBinding
    private var code = ""
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        code = Prefs.getPairCode(this) ?: ""
        if (code.isEmpty()) { finish(); return }

        FirebaseRepo.listenChild(code) { name, age, gender, notes ->
            if (!loaded) {
                loaded = true
                runOnUiThread {
                    if (name.isNotEmpty()) b.etName.setText(name)
                    if (age.isNotEmpty()) b.etAge.setText(age)
                    if (gender.isNotEmpty()) b.etGender.setText(gender)
                    if (notes.isNotEmpty()) b.etNotes.setText(notes)
                }
            }
        }
        FirebaseRepo.listenDeviceModel(code) { m -> runOnUiThread { b.tvDevice.text = m } }

        b.btnSave.setOnClickListener {
            FirebaseRepo.setChild(
                code,
                b.etName.text.toString(),
                b.etAge.text.toString(),
                b.etGender.text.toString(),
                b.etNotes.text.toString()
            )
            Toast.makeText(this, "تم الحفظ ✔", Toast.LENGTH_SHORT).show()
        }
    }
}
