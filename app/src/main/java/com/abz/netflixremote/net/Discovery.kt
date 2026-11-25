package com.abz.netflixremote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

/** Annuncia il tablet sulla rete locale via mDNS. */
class NsdRegistrar(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun register(port: Int) {
        if (listener != null) return
        val info = NsdServiceInfo().apply {
            serviceName = Protocol.SERVICE_NAME
            serviceType = Protocol.SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(s: NsdServiceInfo?, code: Int) { Log.w(TAG, "reg failed $code") }
            override fun onUnregistrationFailed(s: NsdServiceInfo?, code: Int) {}
            override fun onServiceRegistered(s: NsdServiceInfo?) { Log.i(TAG, "registrato") }
            override fun onServiceUnregistered(s: NsdServiceInfo?) {}
        }
        listener = l
        try { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { listener = null }
    }

    fun unregister() {
        listener?.let { try { nsd.unregisterService(it) } catch (_: Exception) {} }
        listener = null
    }

    companion object { private const val TAG = "NsdRegistrar" }
}

/** Cerca il tablet dal telefono. */
class NsdFinder(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val ui = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null

    fun start(onFound: (host: String, port: Int) -> Unit) {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(t: String?, c: Int) {}
            override fun onStopDiscoveryFailed(t: String?, c: Int) {}
            override fun onDiscoveryStarted(t: String?) {}
            override fun onDiscoveryStopped(t: String?) {}
            override fun onServiceLost(s: NsdServiceInfo?) {}
            override fun onServiceFound(s: NsdServiceInfo?) {
                if (s == null || s.serviceName != Protocol.SERVICE_NAME) return
                @Suppress("DEPRECATION")
                nsd.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo?, code: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo?) {
                        val h = si?.host?.hostAddress ?: return
                        ui.post { onFound(h, si.port) }
                    }
                })
            }
        }
        listener = l
        try { nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { listener = null }
    }

    fun stop() {
        listener?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        listener = null
    }
}

object NetUtils {
    /** Primo IPv4 non-loopback: quello da digitare a mano sul telefono. */
    fun localIp(): String {
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress ?: continue
                }
            }
        } catch (_: Exception) {}
        return "?"
    }
}
