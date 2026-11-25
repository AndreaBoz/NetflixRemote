package com.abz.netflixremote.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abz.netflixremote.Prefs
import com.abz.netflixremote.Prefs.autostart
import com.abz.netflixremote.Prefs.backend
import com.abz.netflixremote.Prefs.skipX
import com.abz.netflixremote.Prefs.skipY
import com.abz.netflixremote.R
import com.abz.netflixremote.control.PlaybackController
import com.abz.netflixremote.databinding.ActivityAgentBinding
import com.abz.netflixremote.net.NetUtils
import com.abz.netflixremote.net.Protocol
import com.abz.netflixremote.service.AgentService
import com.abz.netflixremote.service.NfxAccessibilityService
import com.abz.netflixremote.service.NfxNotificationListener

/** Schermata del TABLET: permessi, PIN, indirizzo, avvio servizio. */
class AgentActivity : AppCompatActivity() {

    private lateinit var b: ActivityAgentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAgentBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvPin.text = Prefs.token(this)

        b.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        b.btnNotifications.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        b.btnToggle.setOnClickListener {
            if (AgentService.running) {
                autostart = false
                AgentService.stop(this)
            } else {
                autostart = true
                AgentService.start(this)
            }
            b.root.postDelayed({ refresh() }, 400)
        }
        b.btnNetflix.setOnClickListener {
            try {
                PlaybackController(this).exec(Protocol.LAUNCH, "")
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
        b.btnRole.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_CHOOSER, true))
            finish()
        }

        b.rgBackend.setOnCheckedChangeListener { _, id ->
            backend = when (id) {
                R.id.rbMedia -> Prefs.BACKEND_MEDIA
                R.id.rbGesture -> Prefs.BACKEND_GESTURE
                else -> Prefs.BACKEND_AUTO
            }
        }

        b.btnSaveSkip.setOnClickListener {
            val x = b.etSkipX.text.toString().toIntOrNull()?.coerceIn(0, 100)
            val y = b.etSkipY.text.toString().toIntOrNull()?.coerceIn(0, 100)
            if (x == null || y == null) {
                Toast.makeText(this, R.string.invalid_percent, Toast.LENGTH_SHORT).show()
            } else {
                skipX = x; skipY = y
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
        }

        askNotificationPermission()
        AgentService.onStatusChanged = { runOnUiThread { refresh() } }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (autostart && !AgentService.running) AgentService.start(this)
        b.etSkipX.setText(skipX.toString())
        b.etSkipY.setText(skipY.toString())
        b.rgBackend.check(
            when (backend) {
                Prefs.BACKEND_MEDIA -> R.id.rbMedia
                Prefs.BACKEND_GESTURE -> R.id.rbGesture
                else -> R.id.rbAuto
            }
        )
        refresh()
    }

    override fun onDestroy() {
        AgentService.onStatusChanged = null
        super.onDestroy()
    }

    private fun refresh() {
        val acc = NfxAccessibilityService.isEnabled(this) && NfxAccessibilityService.instance != null
        val notif = NfxNotificationListener.isEnabled(this)
        b.tvAccessibility.text = getString(R.string.st_accessibility, mark(acc))
        b.tvNotifications.text = getString(R.string.st_notifications, mark(notif))
        b.tvAddress.text = getString(R.string.st_address, NetUtils.localIp(), Protocol.PORT)
        b.tvService.text = getString(
            R.string.st_service,
            if (AgentService.running) getString(R.string.on) else getString(R.string.off),
            AgentService.clients
        )
        b.btnToggle.setText(if (AgentService.running) R.string.stop_service else R.string.start_service)
        b.tvDetail.text = PlaybackController(this).state()
    }

    private fun mark(ok: Boolean) = if (ok) "OK" else getString(R.string.missing)
}
