package com.abz.netflixremote

import android.content.Context
import kotlin.random.Random

object Prefs {
    private const val FILE = "nfxremote"

    const val ROLE_NONE = ""
    const val ROLE_AGENT = "agent"
    const val ROLE_REMOTE = "remote"

    const val BACKEND_AUTO = "auto"
    const val BACKEND_MEDIA = "media"
    const val BACKEND_GESTURE = "gesture"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var Context.role: String
        get() = sp(this).getString("role", ROLE_NONE) ?: ROLE_NONE
        set(v) { sp(this).edit().putString("role", v).apply() }

    /** PIN condiviso: generato una volta sul tablet, digitato sul telefono. */
    fun token(c: Context): String {
        val s = sp(c)
        var t = s.getString("token", null)
        if (t == null) {
            t = (100000 + Random.nextInt(900000)).toString()
            s.edit().putString("token", t).apply()
        }
        return t
    }

    fun setToken(c: Context, t: String) = sp(c).edit().putString("token", t).apply()

    /** Se true il servizio si riavvia da solo all'avvio del tablet e dopo un aggiornamento. */
    var Context.autostart: Boolean
        get() = sp(this).getBoolean("autostart", true)
        set(v) { sp(this).edit().putBoolean("autostart", v).apply() }

    var Context.backend: String
        get() = sp(this).getString("backend", BACKEND_AUTO) ?: BACKEND_AUTO
        set(v) { sp(this).edit().putString("backend", v).apply() }

    var Context.lastHost: String
        get() = sp(this).getString("host", "") ?: ""
        set(v) { sp(this).edit().putString("host", v).apply() }

    /** Posizione (in % di schermo) del pulsante "Salta intro" / "Prossimo episodio". */
    var Context.skipX: Int
        get() = sp(this).getInt("skipX", 86)
        set(v) { sp(this).edit().putInt("skipX", v).apply() }

    var Context.skipY: Int
        get() = sp(this).getInt("skipY", 86)
        set(v) { sp(this).edit().putInt("skipY", v).apply() }
}
