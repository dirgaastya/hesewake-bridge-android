package id.hesewake.mediabridge

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

/** Discovery runs only while the screen is open and never advertises credentials. */
internal class Discovery(context: Context, private val onDevices: (List<Endpoint>) -> Unit) : AutoCloseable {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val multicast = wifi.createMulticastLock("hesewake-media-discovery").apply { setReferenceCounted(false) }
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val found = linkedMapOf<String, Endpoint>()
    private var resolving = false
    @Volatile private var running = false
    private val discovery = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(type: String, code: Int) { close() }
        override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        override fun onDiscoveryStarted(type: String) = Unit
        override fun onDiscoveryStopped(type: String) = Unit
        override fun onServiceFound(service: NsdServiceInfo) {
            synchronized(pending) { if (!running) return; pending.addLast(service) }
            resolveNext()
        }
        override fun onServiceLost(service: NsdServiceInfo) {
            synchronized(pending) { found.remove(service.serviceName); onDevices(found.values.toList()) }
        }
    }
    fun start() {
        if (running) return
        running = true
        runCatching { multicast.acquire(); nsd.discoverServices("_hesewake-media._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery) }.onFailure { close() }
    }
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val item = synchronized(pending) {
            if (!running || resolving || pending.isEmpty()) return
            resolving = true; pending.removeFirst()
        }
        try {
            nsd.resolveService(item, object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, code: Int) { finished() }
                override fun onServiceResolved(service: NsdServiceInfo) {
                    val host = service.host?.hostAddress
                    synchronized(pending) {
                        if (running && host != null && service.port in 1..65535) {
                            found[service.serviceName] = Endpoint(service.serviceName, host, service.port)
                            onDevices(found.values.toList())
                        }
                    }
                    finished()
                }
                private fun finished() { synchronized(pending) { resolving = false }; resolveNext() }
            })
        } catch (_: Exception) { synchronized(pending) { resolving = false }; resolveNext() }
    }
    override fun close() {
        val wasRunning = running; running = false
        if (wasRunning) runCatching { nsd.stopServiceDiscovery(discovery) }
        if (multicast.isHeld) runCatching { multicast.release() }
        synchronized(pending) { pending.clear(); found.clear() }
    }
}
