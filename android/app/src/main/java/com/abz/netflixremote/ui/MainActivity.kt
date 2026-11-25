package com.abz.netflixremote.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.abz.netflixremote.Prefs
import com.abz.netflixremote.Prefs.role
import com.abz.netflixremote.databinding.ActivityMainBinding

/** Prima schermata: si sceglie se questo dispositivo e' il tablet o il telecomando. */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val forceChooser = intent.getBooleanExtra(EXTRA_CHOOSER, false)
        if (!forceChooser) {
            when (role) {
                Prefs.ROLE_AGENT -> { open(AgentActivity::class.java); return }
                Prefs.ROLE_REMOTE -> { open(RemoteActivity::class.java); return }
            }
        }

        b.btnAgent.setOnClickListener {
            role = Prefs.ROLE_AGENT
            open(AgentActivity::class.java)
        }
        b.btnRemote.setOnClickListener {
            role = Prefs.ROLE_REMOTE
            open(RemoteActivity::class.java)
        }
    }

    private fun open(cls: Class<*>) {
        startActivity(Intent(this, cls))
        finish()
    }

    companion object { const val EXTRA_CHOOSER = "chooser" }
}
