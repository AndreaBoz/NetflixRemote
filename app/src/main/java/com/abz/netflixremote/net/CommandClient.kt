package com.abz.netflixremote.net

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Client TCP del telefono, con riconnessione automatica e coda comandi.
 */
class CommandClient(
    private val host: String,
    private val port: Int,
    private val token: String
) {
    enum class State { CONNECTING, CONNECTED, ERROR }

    private val queue = LinkedBlockingQueue<String>()
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null

    var onState: ((State, String) -> Unit)? = null
    var onReply: ((String) -> Unit)? = null

    fun start() {
        if (running) return
        running = true
        thread(name = "nfx-client-loop") {
            while (running) {
                post(State.CONNECTING, "Connessione a $host...")
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 4000)
                    s.tcpNoDelay = true
                    socket = s
                    val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))

                    // verifica PIN subito
                    queue.clear()
                    writer.write(Protocol.encode(token, Protocol.PING)); writer.write("\n"); writer.flush()
                    val hello = reader.readLine()
                    if (hello == null || hello.startsWith(Protocol.ERR)) {
                        post(State.ERROR, if (hello?.contains("bad-pin") == true) "PIN errato" else "Handshake fallito")
                        s.close(); socket = null
                        Thread.sleep(2000); continue
                    }
                    post(State.CONNECTED, "Collegato a $host")

                    // lettore risposte
                    thread(name = "nfx-reader") {
                        try {
                            while (true) {
                                val line = reader.readLine() ?: break
                                ui.post { onReply?.invoke(line) }
                            }
                        } catch (_: Exception) {}
                    }

                    while (running && !s.isClosed) {
                        val msg = queue.poll(10, java.util.concurrent.TimeUnit.SECONDS)
                            ?: Protocol.encode(token, Protocol.PING) // heartbeat
                        writer.write(msg); writer.write("\n"); writer.flush()
                    }
                } catch (e: Exception) {
                    post(State.ERROR, "Non raggiungibile (${e.javaClass.simpleName})")
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                    if (running) Thread.sleep(2500)
                }
            }
        }
    }

    fun send(cmd: String, arg: String = "") {
        queue.offer(Protocol.encode(token, cmd, arg))
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun post(s: State, msg: String) = ui.post { onState?.invoke(s, msg) }
}
