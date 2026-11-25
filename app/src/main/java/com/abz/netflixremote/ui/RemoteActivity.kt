package com.abz.netflixremote.ui

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abz.netflixremote.Prefs
import com.abz.netflixremote.Prefs.lastHost
import com.abz.netflixremote.R
import com.abz.netflixremote.databinding.ActivityRemoteBinding
import com.abz.netflixremote.net.CommandClient
import com.abz.netflixremote.net.NsdFinder
import com.abz.netflixremote.net.Protocol

/** Schermata del TELEFONO: il telecomando vero e proprio. */
class RemoteActivity : AppCompatActivity() {

    private lateinit var b: ActivityRemoteBinding
    private var client: CommandClient? = null
    private var finder: NsdFinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRemoteBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        b.etHost.setText(lastHost)
        b.etPin.setText(Prefs.token(this).takeIf { lastHost.isNotEmpty() } ?: "")

        b.btnConnect.setOnClickListener { connect() }
        b.btnScan.setOnClickListener { scan() }
        b.btnRole.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_CHOOSER, true))
            finish()
        }

        wire(b.btnPlayPause, Protocol.PLAYPAUSE)
        wire(b.btnBack10, Protocol.SEEK, "-10")
        wire(b.btnFwd10, Protocol.SEEK, "10")
        wire(b.btnBack30, Protocol.SEEK, "-30")
        wire(b.btnFwd30, Protocol.SEEK, "30")
        wire(b.btnSkip, Protocol.SKIP)
        wire(b.btnNav, Protocol.NAV, "back")
        wire(b.btnVolUp, Protocol.VOL, "up")
        wire(b.btnVolDown, Protocol.VOL, "down")
        wire(b.btnMute, Protocol.VOL, "mute")
        wire(b.btnLaunch, Protocol.LAUNCH)

        if (lastHost.isNotEmpty()) connect()
    }

    private fun wire(v: View, cmd: String, arg: String = "") {
        v.setOnClickListener {
            val c = client
            if (c == null) {
                Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            } else {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                c.send(cmd, arg)
            }
        }
    }

    private fun connect() {
        val host = b.etHost.text.toString().trim()
        val pin = b.etPin.text.toString().trim()
        if (host.isEmpty() || pin.length != 6) {
            Toast.makeText(this, R.string.need_host_pin, Toast.LENGTH_LONG).show()
            return
        }
        client?.stop()
        lastHost = host
        Prefs.setToken(this, pin)
        val c = CommandClient(host, Protocol.PORT, pin)
        c.onState = { st, msg ->
            b.tvStatus.text = msg
            b.tvStatus.setTextColor(
                getColor(
                    when (st) {
                        CommandClient.State.CONNECTED -> R.color.ok
                        CommandClient.State.CONNECTING -> R.color.warn
                        else -> R.color.err
                    }
                )
            )
            b.controls.alpha = if (st == CommandClient.State.CONNECTED) 1f else 0.4f
        }
        c.onReply = { line ->
            if (line.startsWith(Protocol.ERR)) {
                Toast.makeText(this, line.removePrefix("${Protocol.ERR}|"), Toast.LENGTH_SHORT).show()
            }
        }
        c.start()
        client = c
    }

    private fun scan() {
        b.tvStatus.setText(R.string.scanning)
        finder?.stop()
        finder = NsdFinder(this).also { f ->
            f.start { host, _ ->
                b.etHost.setText(host)
                f.stop()
                Toast.makeText(this, getString(R.string.found, host), Toast.LENGTH_SHORT).show()
            }
        }
        b.root.postDelayed({ finder?.stop() }, 8000)
    }

    override fun onDestroy() {
        client?.stop()
        finder?.stop()
        super.onDestroy()
    }
}
