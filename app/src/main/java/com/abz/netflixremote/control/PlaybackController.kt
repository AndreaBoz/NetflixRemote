package com.abz.netflixremote.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.abz.netflixremote.Prefs
import com.abz.netflixremote.Prefs.backend
import com.abz.netflixremote.Prefs.skipX
import com.abz.netflixremote.Prefs.skipY
import com.abz.netflixremote.net.Protocol
import com.abz.netflixremote.service.NfxAccessibilityService
import com.abz.netflixremote.service.NfxNotificationListener

/**
 * Esegue i comandi sul tablet scegliendo la strategia migliore disponibile:
 *  1) MediaSession di Netflix (pulita, niente coordinate)
 *  2) tasti media di sistema
 *  3) gesture simulate sull'interfaccia Netflix
 */
class PlaybackController(private val ctx: Context) {

    private val audio get() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Ultimo tocco inviato: serve a sapere se i controlli Netflix sono ancora a schermo. */
    @Volatile private var lastTapAt = 0L
    private val controlsVisibleMs = 4500L

    fun exec(cmd: String, arg: String): String = when (cmd) {
        Protocol.PING -> "pong"
        Protocol.PLAYPAUSE -> playPause()
        Protocol.PLAY -> play()
        Protocol.PAUSE -> pause()
        Protocol.SEEK -> seek(arg.toIntOrNull() ?: 0)
        Protocol.SKIP -> skipButton()
        Protocol.VOL -> volume(arg)
        Protocol.NAV -> nav(arg)
        Protocol.LAUNCH -> launchNetflix()
        Protocol.STATE -> state()
        else -> throw IllegalArgumentException("comando sconosciuto: $cmd")
    }

    // ---------- selezione backend ----------

    private fun netflixSession(): MediaController? {
        if (ctx.backend == Prefs.BACKEND_GESTURE) return null
        if (!NfxNotificationListener.isEnabled(ctx)) return null
        return try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(NfxNotificationListener.component(ctx))
                .firstOrNull { it.packageName.contains("netflix", ignoreCase = true) }
        } catch (e: Exception) {
            null
        }
    }

    private fun gestures(): NfxAccessibilityService =
        NfxAccessibilityService.instance
            ?: throw IllegalStateException("servizio di accessibilita' non attivo sul tablet")

    private fun mediaKey(code: Int): Boolean = try {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        true
    } catch (e: Throwable) {
        false
    }

    // ---------- comandi ----------


    /**
     * PlaybackState.position e' una fotografia scattata a lastPositionUpdateTime: Netflix la
     * aggiorna a scatti, anche ogni decine di secondi. Va estrapolata, altrimenti ogni seek
     * parte da un punto vecchio e salta dove capita.
     */
    private fun livePosition(st: PlaybackState?): Long {
        if (st == null) return -1L
        val base = st.position
        if (base < 0L) return -1L
        if (st.state != PlaybackState.STATE_PLAYING) return base
        val elapsed = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
        // se il timestamp e' assurdo (orologio incoerente) meglio la posizione grezza
        if (elapsed < 0L || elapsed > 3_600_000L) return base
        val speed = if (st.playbackSpeed > 0f) st.playbackSpeed else 1f
        return base + (elapsed * speed).toLong()
    }

    private fun playPause(): String {
        netflixSession()?.let { c ->
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) c.transportControls.pause() else c.transportControls.play()
            return if (playing) "media:pause" else "media:play"
        }
        // Modalita' "solo media": tasto media di sistema, nessuna gesture (evita doppi comandi).
        if (ctx.backend == Prefs.BACKEND_MEDIA) {
            if (mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) return "mediakey:playpause"
            throw IllegalStateException("nessuna sessione media di Netflix disponibile")
        }
        val gs = gestures()
        // Se i controlli Netflix sono gia' a schermo il pulsante esiste davvero: premiamo quello.
        if (gs.clickNode(PLAY_PAUSE_LABELS)) {
            lastTapAt = System.currentTimeMillis()
            return "gesture:pulsante play/pausa"
        }
        // Altrimenti: un tocco al centro mostra i controlli, il secondo preme play/pausa.
        // Se abbiamo appena toccato, i controlli sono ancora a schermo: basta un tocco solo.
        val g = gestures()
        val s = g.screenSize()
        val cx = s.x / 2f
        val cy = s.y / 2f
        val now = System.currentTimeMillis()
        val controlsUp = now - lastTapAt < controlsVisibleMs
        val taps = if (controlsUp) listOf(Triple(cx, cy, 0L))
        else listOf(Triple(cx, cy, 0L), Triple(cx, cy, 450L))
        g.taps(taps)
        lastTapAt = now + if (controlsUp) 0L else 450L
        return if (controlsUp) "gesture:tap" else "gesture:tap-x2"
    }

    private fun play(): String {
        netflixSession()?.let { it.transportControls.play(); return "media:play" }
        if (ctx.backend == Prefs.BACKEND_MEDIA) {
            if (mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)) return "mediakey:play"
            throw IllegalStateException("nessuna sessione media di Netflix disponibile")
        }
        return playPause()
    }

    private fun pause(): String {
        netflixSession()?.let { it.transportControls.pause(); return "media:pause" }
        if (ctx.backend == Prefs.BACKEND_MEDIA) {
            if (mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)) return "mediakey:pause"
            throw IllegalStateException("nessuna sessione media di Netflix disponibile")
        }
        return playPause()
    }

    private fun seek(seconds: Int): String {
        if (seconds == 0) return "noop"
        netflixSession()?.let { c ->
            val pos = livePosition(c.playbackState)
            if (pos >= 0L) {
                val target = (pos + seconds * 1000L).coerceAtLeast(0L)
                c.transportControls.seekTo(target)
                return "media:seek$seconds (da ${pos / 1000}s a ${target / 1000}s)"
            }
        }
        if (ctx.backend == Prefs.BACKEND_MEDIA)
            throw IllegalStateException("nessuna sessione media di Netflix disponibile")
        val gz = gestures()
        val steps10 = (Math.abs(seconds) / 10).coerceAtLeast(1)
        if (gz.clickNode(if (seconds < 0) BACK_LABELS else FWD_LABELS, steps10)) {
            return "gesture:pulsante ${if (seconds < 0) "-" else "+"}${steps10 * 10}s"
        }
        // Altrimenti doppio tocco a sinistra/destra = -10s/+10s, ogni tocco extra aggiunge 10s.
        val g = gestures()
        val s = g.screenSize()
        val x = if (seconds < 0) s.x * 0.22f else s.x * 0.78f
        val y = s.y * 0.5f
        val steps = (Math.abs(seconds) / 10).coerceAtLeast(1)
        val points = (0..steps).map { i -> Triple(x, y, i * 190L) }
        g.taps(points)
        lastTapAt = 0L // il seek non lascia visibile la barra dei controlli
        return "gesture:seek$seconds (${points.size} tocchi)"
    }

    /** Preme dove Netflix mostra "Salta intro" / "Prossimo episodio". */
    private fun skipButton(): String {
        val g = gestures()
        // Prima si cerca il pulsante vero: la sua posizione cambia fra intro e fine episodio.
        if (g.clickNode(SKIP_LABELS)) return "gesture:pulsante salta"
        val s = g.screenSize()
        val x = s.x * (ctx.skipX / 100f)
        val y = s.y * (ctx.skipY / 100f)
        g.taps(listOf(Triple(x, y, 0L)))
        return "gesture:skip(${ctx.skipX}%,${ctx.skipY}%)"
    }

    private fun volume(arg: String): String {
        val dir = when (arg) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> throw IllegalArgumentException("volume: $arg")
        }
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return "vol:$arg=" + audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    private fun nav(arg: String): String {
        val action = when (arg) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> throw IllegalArgumentException("nav: $arg")
        }
        gestures().global(action)
        lastTapAt = 0L
        return "nav:$arg"
    }

    private fun launchNetflix(): String {
        val i = ctx.packageManager.getLaunchIntentForPackage(NETFLIX)
            ?: throw IllegalStateException("Netflix non installato sul tablet")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "launched"
    }

    fun state(): String {
        val acc = NfxAccessibilityService.instance != null
        val notif = NfxNotificationListener.isEnabled(ctx)
        val sess = netflixSession()
        val playing = sess?.playbackState?.state == PlaybackState.STATE_PLAYING
        return "acc=$acc notif=$notif session=${sess != null} playing=$playing backend=${ctx.backend}"
    }

    companion object {
        const val NETFLIX = "com.netflix.mediaclient"

        // Etichette dei comandi del player Netflix, in italiano e in inglese.
        private val PLAY_PAUSE_LABELS = listOf("pausa", "pause", "riproduci", "play")
        private val FWD_LABELS = listOf("avanti di 10", "forward 10", "fast forward")
        private val BACK_LABELS = listOf("indietro di 10", "back 10", "rewind")
        private val SKIP_LABELS = listOf("salta", "skip", "prossimo episodio", "episodio successivo", "next episode")
    }
}
