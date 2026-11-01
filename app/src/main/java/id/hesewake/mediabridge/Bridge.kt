package id.hesewake.mediabridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

internal data class Endpoint(val name: String, val host: String, val port: Int, val expectedFingerprint: String? = null)

/** Socket work stays off the main thread. A generation invalidates a replaced connection. */
internal object Bridge {
    private class AuthRejected : Exception()
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val writer = Executors.newSingleThreadScheduledExecutor()
    private val commands = Executors.newSingleThreadExecutor()
    private val artworkPolicy = ArtworkPolicy()
    @Volatile private var artworkDelta = false
    @Volatile private var forgetSupported = false
    @Volatile private var forgetWaiter: CountDownLatch? = null
    @Volatile private var forgetAccepted = false
    @Volatile private var outputGeneration = 0
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var output: DataOutputStream? = null
    @Volatile private var authenticated = false
    @Volatile private var latest: String? = null
    @Volatile private var context: Context? = null
    @Volatile private var userStopped = false
    @Volatile private var pairDecision: ((Boolean) -> Unit)? = null
    @Volatile var status: String = "Belum terhubung"; private set
    @Volatile var currentMedia: String = "Tidak ada media aktif"; private set
    @Volatile var currentArtist: String = ""; private set
    @Volatile var currentArtwork: String? = null; private set
    @Volatile var mediaActive: Boolean = false; private set
    @Volatile var mediaPlaying: Boolean = false; private set
    @Volatile var sources: List<String> = emptyList()
    @Volatile var onChanged: (() -> Unit)? = null
    @Volatile var onPairCode: ((String, (Boolean) -> Unit) -> Unit)? = null
    private var started = false
    private var seq = 0L
    private var heartbeatTick = 0
    @Synchronized fun init(c: Context) {
        context = c.applicationContext
        if (started) return
        started = true
        writer.scheduleAtFixedRate({
            val out = output
            val outputRun = outputGeneration
            if (authenticated && out != null && generation.get() == outputRun) {
                try {
                    val snapshot = latest?.let(::JSONObject) ?: JSONObject().put("active", false)
                    val active = snapshot.optBoolean("active")
                    val artwork = snapshot.optString("artwork_base64").takeUnless { it.isBlank() || it == "null" }
                    synchronized(artworkPolicy) {
                        if (generation.get() != outputRun || out !== output) return@scheduleAtFixedRate
                        if (artworkPolicy.unchanged(artworkDelta, active, artwork)) {
                            snapshot.remove("artwork_mime"); snapshot.remove("artwork_base64")
                            snapshot.put("artwork_same", true)
                        }
                        snapshot.put("v", 1).put("type", "snapshot").put("seq", seq++)
                        Wire.send(out, snapshot)
                        artworkPolicy.sent(active, artwork)
                    }
                    if (++heartbeatTick >= 10) { heartbeatTick = 0; Wire.send(out, Wire.message("ping").put("id", UUID.randomUUID().toString())) }
                } catch (_: Exception) { runCatching { socket?.close() } }
            }
        }, 0, 1, TimeUnit.SECONDS)
        TrustedStore(c).load()?.let { reconnect(it) }
    }
    private fun state(value: String) { status = value; main.post { onChanged?.invoke() } }
    fun updateMedia(snapshot: JSONObject?) {
        latest = snapshot?.toString()
        currentMedia = snapshot?.optString("title")?.takeIf { it.isNotBlank() } ?: "Tidak ada media aktif"
        currentArtist = snapshot?.optString("artist").orEmpty()
        currentArtwork = snapshot?.optString("artwork_base64")?.takeIf { it.isNotBlank() && it != "null" }
        mediaActive = snapshot?.optBoolean("active") == true
        mediaPlaying = snapshot?.optBoolean("playing") == true
        main.post { onChanged?.invoke() }
    }
    fun mediaUnavailable() = updateMedia(null)
    fun hasTrustedDevice(): Boolean = context?.let { TrustedStore(it).load() } != null
    private fun deviceName(): String {
        val name = context?.let { runCatching { Settings.Global.getString(it.contentResolver, "device_name") }.getOrNull() }
        return (name?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Android").take(200)
    }
    private fun supports(result: JSONObject, capability: String): Boolean {
        val capabilities = result.optJSONArray("capabilities") ?: return false
        return (0 until capabilities.length()).any { capabilities.optString(it) == capability }
    }
    fun pair(endpoint: Endpoint) {
        userStopped = false
        val run = generation.incrementAndGet(); pairDecision?.invoke(false); closeSocket()
        Thread({ connectOnce(endpoint, null, run) }, "Bridge-pair").start()
    }
    private fun reconnect(device: TrustedDevice) {
        userStopped = false
        val run = generation.incrementAndGet(); pairDecision?.invoke(false); closeSocket()
        Thread({
            var delay = 1L
            var current = device
            var candidateIndex = 0
            while (!userStopped && generation.get() == run) {
                state("Menghubungkan kembali ke ${current.name}…")
                val ok = connectOnce(Endpoint(current.name, current.host, current.port, current.fingerprint), current, run)
                if (userStopped || generation.get() != run) break
                if (ok) delay = 1
                // Rediscover after a failure: the trusted computer's IP or port can change.
                val ctx = context
                if (ctx != null) {
                    val latch = CountDownLatch(1)
                    val candidates = java.util.concurrent.atomic.AtomicReference<List<Endpoint>>(emptyList())
                    val discovery = Discovery(ctx) { found ->
                        candidates.set(found.sortedByDescending { it.name == device.name })
                    }
                    try { discovery.start(); latch.await(6, TimeUnit.SECONDS) } finally { discovery.close() }
                    val discovered = candidates.get()
                    if (discovered.isNotEmpty()) {
                        val next = discovered[candidateIndex++ % discovered.size]
                        current = current.copy(host = next.host, port = next.port)
                    }
                }
                Thread.sleep(delay * 1000); delay = (delay * 2).coerceAtMost(30)
            }
        }, "Bridge-reconnect").start()
    }
    fun reconnectSaved() { context?.let { TrustedStore(it).load() }?.let(::reconnect) ?: state("Belum ada laptop tepercaya") }
    private fun connectOnce(endpoint: Endpoint, trusted: TrustedDevice?, run: Int): Boolean {
        var connected = false
        var negotiatedDelta = false
        var negotiatedForget = false
        try {
            require(endpoint.port in 1..65535 && endpoint.host.length in 1..255)
            state(if (trusted == null) "Menghubungkan ke ${endpoint.name}…" else "Menghubungkan kembali…")
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) { require(!chain.isNullOrEmpty()) }
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            val raw = Socket(); raw.connect(InetSocketAddress(endpoint.host, endpoint.port), 10_000)
            val peer = ssl.socketFactory.createSocket(raw, endpoint.host, endpoint.port, true) as SSLSocket
            if (generation.get() != run) { peer.close(); return false }
            socket = peer; peer.soTimeout = 10_000; peer.startHandshake()
            val cert = peer.session.peerCertificates.first() as X509Certificate
            val fingerprintBytes = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            val fingerprint = Wire.hex(fingerprintBytes)
            endpoint.expectedFingerprint?.let { require(MessageDigest.isEqual(it.lowercase().toByteArray(Charsets.US_ASCII), fingerprint.toByteArray(Charsets.US_ASCII))) { "Fingerprint mismatch" } }
            val input = DataInputStream(peer.inputStream); val out = DataOutputStream(peer.outputStream)
            if (trusted == null) {
                val deadline = android.os.SystemClock.elapsedRealtime() + 120_000L
                fun remaining(): Int {
                    val left = deadline - android.os.SystemClock.elapsedRealtime()
                    require(left > 0) { "Pairing expired" }
                    return left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
                val client = Wire.bytes(32)
                Wire.send(out, Wire.message("pair_hello").put("client_nonce", Wire.b64(client)).put("device_name", deviceName()))
                peer.soTimeout = remaining()
                val challenge = Wire.read(input); require(challenge.getString("type") == "pair_challenge")
                val server = Wire.unb64(challenge.getString("server_nonce")); require(server.size == 32)
                val code = Wire.pairingCode(fingerprintBytes, client, server)
                val latch = CountDownLatch(1)
                val settled = java.util.concurrent.atomic.AtomicBoolean(false)
                val approved = java.util.concurrent.atomic.AtomicBoolean(false)
                val decide: (Boolean) -> Unit = { yes -> if (settled.compareAndSet(false, true)) { approved.set(yes); latch.countDown() } }
                pairDecision = decide
                state("Cocokkan kode pairing di kedua perangkat")
                main.post {
                    val prompt = onPairCode
                    if (prompt == null || generation.get() != run) decide(false)
                    else prompt(code) { yes -> if (generation.get() == run) decide(yes) }
                }
                val answered = latch.await(remaining().toLong(), TimeUnit.MILLISECONDS)
                if (pairDecision === decide) pairDecision = null
                if (generation.get() != run) return false
                Wire.send(out, Wire.message("pair_decision").put("approved", answered && approved.get()))
                if (!answered || !approved.get()) { state("Pairing dibatalkan"); return false }
                peer.soTimeout = remaining()
                val remoteDecision = Wire.read(input); require(remoteDecision.getString("type") == "pair_decision" && remoteDecision.getBoolean("approved"))
                peer.soTimeout = remaining()
                val result = Wire.read(input); require(result.getString("type") == "pair_result" && result.getBoolean("approved"))
                negotiatedDelta = supports(result, "artwork_delta_v1")
                negotiatedForget = supports(result, "forget_v1")
                val token = result.getString("token"); require(Wire.unb64(token).size == 32)
                TrustedStore(context!!).save(TrustedDevice(endpoint.name, endpoint.host, endpoint.port, fingerprint, token))
            } else {
                Wire.send(out, Wire.message("auth").put("token", trusted.token).put("device_name", deviceName()))
                val result = Wire.read(input); require(result.getString("type") == "auth_result")
                if (!result.getBoolean("approved")) throw AuthRejected()
                negotiatedDelta = supports(result, "artwork_delta_v1")
                negotiatedForget = supports(result, "forget_v1")
                if (trusted.host != endpoint.host || trusted.port != endpoint.port) {
                    TrustedStore(context!!).save(trusted.copy(host = endpoint.host, port = endpoint.port))
                }
            }
            if (generation.get() != run) return false
            synchronized(artworkPolicy) { artworkPolicy.reset(); artworkDelta = negotiatedDelta; forgetSupported = negotiatedForget; seq = 0; heartbeatTick = 0; outputGeneration = run; output = out; authenticated = true }
            peer.soTimeout = 35_000; connected = true
            state("Terhubung ke ${endpoint.name}")
            val seen = linkedMapOf<String, Boolean>()
            while (generation.get() == run) {
                val msg = Wire.read(input)
                when (msg.getString("type")) {
                    "ping" -> {
                        val id = msg.getString("id"); require(id.length in 1..100)
                        Wire.send(out, Wire.message("pong").put("id", id))
                    }
                    "pong" -> { val id = msg.getString("id"); require(id.length in 1..100) }
                    "forget_result" -> {
                        require(forgetSupported && forgetWaiter != null)
                        forgetAccepted = msg.optBoolean("approved", false); forgetWaiter?.countDown()
                    }
                    "command" -> {
                        val id = msg.getString("id"); require(id.length == 36 && runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false))
                        val action = msg.getString("action"); val pos = if (action == "seek" && msg.has("position_ms")) msg.getLong("position_ms") else null
                        commands.execute {
                            val accepted = seen[id] ?: (generation.get() == run && authenticated && socket === peer && MediaListener.instance?.execute(action, pos) == true).also {
                                seen[id] = it
                                if (seen.size > 256) seen.remove(seen.keys.first())
                            }
                            val ack = Wire.message("ack").put("id", id).put("accepted", accepted)
                            if (!accepted) ack.put("reason", "unavailable")
                            writer.execute { if (generation.get() == run && authenticated) runCatching { Wire.send(out, ack) }.onFailure { runCatching { peer.close() } } }
                        }
                    }
                    else -> error("Unsupported message")
                }
            }
        } catch (failure: Exception) {
            if (generation.get() == run) {
                if (failure is AuthRejected) { userStopped = true; state("Laptop tidak lagi mempercayai perangkat ini. Pairing ulang.") }
                else state(if (trusted == null && !connected) "Pairing gagal atau ditolak" else "Koneksi terputus")
            }
        } finally {
            if (generation.get() == run) { authenticated = false; output = null; closeSocket() }
        }
        // A fresh successful pairing becomes reconnectable without asking again.
        if (trusted == null && connected && generation.get() == run && !userStopped) {
            context?.let { TrustedStore(it).load() }?.let(::reconnect)
        }
        return connected
    }
    private fun closeSocket() { runCatching { socket?.close() }; socket = null; authenticated = false; output = null }
    fun disconnect() { userStopped = true; generation.incrementAndGet(); pairDecision?.invoke(false); closeSocket(); state("Terputus") }
    fun forget() {
        context?.let { TrustedStore(it).clear() }
        val run = generation.get(); val out = output
        if (!authenticated || !forgetSupported || out == null) {
            disconnect(); state("Dilupakan di HP. Lupakan HP ini juga di laptop."); return
        }
        userStopped = true
        val waiter = CountDownLatch(1); forgetAccepted = false; forgetWaiter = waiter
        Thread({
            runCatching { Wire.send(out, Wire.message("forget")) }
                .onSuccess { waiter.await(3, TimeUnit.SECONDS) }
            val accepted = forgetAccepted
            if (forgetWaiter === waiter) forgetWaiter = null
            if (generation.get() == run) {
                disconnect()
                state(if (accepted) "Perangkat dilupakan di HP dan laptop" else "Dilupakan di HP. Lupakan HP ini juga di laptop.")
            }
        }, "Bridge-forget").start()
    }
}
