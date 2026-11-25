package com.abz.netflixremote.net

object Protocol {
    const val PORT = 8765
    const val SERVICE_TYPE = "_nfxremote._tcp."
    const val SERVICE_NAME = "NetflixRemote"
    const val SEP = "|"

    // comandi
    const val PING = "PING"
    const val PLAYPAUSE = "PLAYPAUSE"
    const val PLAY = "PLAY"
    const val PAUSE = "PAUSE"
    const val SEEK = "SEEK"          // arg: secondi con segno, es. -10 / 30
    const val SKIP = "SKIP"          // "Salta intro" / "Prossimo episodio"
    const val VOL = "VOL"            // arg: up | down | mute
    const val NAV = "NAV"            // arg: back | home | recents
    const val LAUNCH = "LAUNCH"      // apre Netflix sul tablet
    const val STATE = "STATE"

    const val OK = "OK"
    const val ERR = "ERR"

    fun encode(token: String, cmd: String, arg: String = ""): String =
        listOf(token, cmd, arg).joinToString(SEP)

    /** Ritorna (token, cmd, arg) oppure null se la riga e' malformata. */
    fun decode(line: String): Triple<String, String, String>? {
        val p = line.split(SEP)
        if (p.size < 2) return null
        return Triple(p[0], p[1], p.getOrElse(2) { "" })
    }
}
