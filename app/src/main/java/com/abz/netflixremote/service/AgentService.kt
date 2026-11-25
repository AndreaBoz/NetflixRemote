package com.abz.netflixremote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import com.abz.netflixremote.Prefs
import com.abz.netflixremote.R
import com.abz.netflixremote.control.PlaybackController
import com.abz.netflixremote.net.CommandServer
import com.abz.netflixremote.net.NetUtils
import com.abz.netflixremote.net.NsdRegistrar
import com.abz.netflixremote.net.Protocol
import com.abz.netflixremote.ui.MainActivity

/** Servizio in primo piano sul tablet: tiene aperto il server dei comandi. */
class AgentService : Service() {

    private var server: CommandServer? = null
    private var nsd: NsdRegistrar? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Il lock Wi-Fi impedisce alla radio di entrare in risparmio energetico: serve solo
        // mentre un telecomando e' collegato, altrimenti sarebbe consumo a vuoto tutta la notte.
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nfxremote").apply {
            setReferenceCounted(false)
        }

        val controller = PlaybackController(this)
        val s = CommandServer(
            port = Protocol.PORT,
            token = { Prefs.token(this) },
            onCommand = { cmd, arg -> controller.exec(cmd, arg) }
        )
        s.onClientsChanged = { n ->
            clients = n
            updateWifiLock(n)
            notifyStatus()
            updateNotification()
        }
        s.start()
        server = s

        nsd = NsdRegistrar(this).also { it.register(Protocol.PORT) }

        running = true
        notifyStatus()
    }

    /** Tiene il Wi-Fi sveglio solo con almeno un telecomando collegato. */
    private fun updateWifiLock(clients: Int) {
        val l = wifiLock ?: return
        try {
            if (clients > 0 && !l.isHeld) l.acquire()
            else if (clients == 0 && l.isHeld) l.release()
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop(); server = null
        nsd?.unregister(); nsd = null
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
        wifiLock = null
        running = false
        clients = 0
        notifyStatus()
        super.onDestroy()
    }

    // ---------- notifica ----------

    private fun channel(): String {
        val id = "agent"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(
                NotificationChannel(id, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        return id
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = getString(R.string.notif_text, NetUtils.localIp(), Protocol.PORT, clients)
        return Notification.Builder(this, channel())
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    private fun updateNotification() {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {}
    }

    private fun notifyStatus() {
        onStatusChanged?.invoke()
    }

    companion object {
        private const val NOTIF_ID = 42

        @Volatile var running = false; private set
        @Volatile var clients = 0; private set

        /** Callback per aggiornare la schermata del tablet. */
        var onStatusChanged: (() -> Unit)? = null

        fun start(c: Context) {
            val i = Intent(c, AgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
        }

        fun stop(c: Context) = c.stopService(Intent(c, AgentService::class.java))
    }
}
