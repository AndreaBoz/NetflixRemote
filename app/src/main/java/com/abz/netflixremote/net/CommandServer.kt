package com.abz.netflixremote.net

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Server TCP che gira sul tablet. Protocollo a righe: "<token>|<CMD>|<arg>".
 */
class CommandServer(
    private val port: Int = Protocol.PORT,
    private val token: () -> String,
    private val onCommand: (cmd: String, arg: String) -> String
) {
    private var server: ServerSocket? = null
    @Volatile private var running = false
    private val clients = Collections.synchronizedSet(HashSet<Socket>())

    var onClientsChanged: ((Int) -> Unit)? = null

    val clientCount: Int get() = clients.size
    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        thread(name = "nfx-server") {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(port))
                server = s
                while (running) {
                    val socket = try { s.accept() } catch (e: Exception) { break }
                    handle(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "server error", e)
            } finally {
                running = false
            }
        }
    }

    private fun handle(socket: Socket) {
        clients.add(socket)
        onClientsChanged?.invoke(clients.size)
        thread(name = "nfx-client") {
            try {
                socket.tcpNoDelay = true
                socket.soTimeout = 30_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                while (running) {
                    val line = reader.readLine() ?: break
                    val parsed = Protocol.decode(line.trim())
                    val reply = when {
                        parsed == null -> "${Protocol.ERR}|malformed"
                        parsed.first != token() -> "${Protocol.ERR}|bad-pin"
                        else -> try {
                            "${Protocol.OK}|" + onCommand(parsed.second, parsed.third)
                        } catch (e: Exception) {
                            "${Protocol.ERR}|" + (e.message ?: e.javaClass.simpleName)
                        }
                    }
                    writer.write(reply)
                    writer.write("\n")
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "client closed: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
                clients.remove(socket)
                onClientsChanged?.invoke(clients.size)
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        synchronized(clients) { clients.toList() }.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
        onClientsChanged?.invoke(0)
    }

    companion object { private const val TAG = "CommandServer" }
}
