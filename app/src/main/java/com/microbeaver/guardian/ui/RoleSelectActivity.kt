package com.microbeaver.guardian.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.databinding.ActivityRoleSelectBinding

class RoleSelectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Role is chosen once. On the child device it can never be switched back.
        when (Prefs.getRole(this)) {
            Prefs.ROLE_PARENT -> { open(ParentActivity::class.java); return }
            Prefs.ROLE_CHILD -> { open(ChildSetupActivity::class.java); return }
        }

        val b = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnParent.setOnClickListener {
            Prefs.setRole(this, Prefs.ROLE_PARENT)
            open(ParentActivity::class.java)
        }
        b.btnChild.setOnClickListener {
            Prefs.setRole(this, Prefs.ROLE_CHILD)
            open(ChildSetupActivity::class.java)
        }
    }

    private fun open(cls: Class<*>) {
        startActivity(Intent(this, cls))
        finish()
    }
}
