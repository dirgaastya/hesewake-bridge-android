package id.hesewake.mediabridge

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.SeekBar
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var radar: MediaRadar
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var badge: TextView
    private lateinit var connection: TextView
    private lateinit var permissionAction: TextView
    private lateinit var devices: LinearLayout
    private lateinit var discoveryRadar: DiscoveryRadar
    private lateinit var backdrop: ArtworkBackdrop
    private lateinit var seek: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var duration: TextView
    private lateinit var previous: PlayerButton
    private lateinit var play: PlayerButton
    private lateinit var next: PlayerButton
    private lateinit var reconnect: TextView
    private var seeking = false
    private var page = "home"
    private var discovery: Discovery? = null
    private var pairDialog: AlertDialog? = null
    private var activeDecision: ((Boolean) -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(applicationContext)
        if (Bridge.hasTrustedDevice() && getSharedPreferences("session", MODE_PRIVATE).getBoolean("active", true)) runCatching { BridgeService.start(this) }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        window.statusBarColor = BridgeDesign.background
        window.navigationBarColor = BridgeDesign.background
        showHome()
        Bridge.onChanged = { render() }
        Bridge.onPairCode = { code, decide ->
            pairDialog?.dismiss(); activeDecision = decide
            pairDialog = AlertDialog.Builder(this).setTitle("Cocokkan kode")
                .setMessage("Pastikan kode di laptop sama: $code")
                .setNegativeButton("Tolak") { _, _ -> activeDecision = null; decide(false) }
                .setPositiveButton("Sama, setujui") { _, _ -> activeDecision = null; decide(true) }
                .setOnCancelListener { activeDecision = null; decide(false) }.show()
        }
        render()
    }
    private fun screen(): LinearLayout {
        val root = FrameLayout(this)
        backdrop = ArtworkBackdrop(this, showCover = false)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        val content = BridgeDesign.column(this).apply { setPadding(dp(22), dp(16), dp(22), dp(18)) }
        BridgeDesign.systemInsets(content, dp(22), dp(16), dp(22), dp(18))
        root.addView(content, FrameLayout.LayoutParams(-1, -1)); setContentView(root)
        backdrop.update(Bridge.displayArtwork)
        return content
    }
    private fun showHome() {
        page = "home"; discovery?.close(); discovery = null
        val d = BridgeDesign
        val root = FrameLayout(this); backdrop = ArtworkBackdrop(this); root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val content = d.column(this).apply { setPadding(dp(24), dp(16), dp(24), dp(22)) }
        d.systemInsets(content, dp(24), dp(16), dp(24), dp(22)); scroll.addView(content); root.addView(scroll); setContentView(root)
        val header = d.row(this)
        header.addView(d.text(this, "HESEWAKE  /  BRIDGE", 11f, d.white, true).apply { letterSpacing = .12f }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconAction("qr", "Scan kode QR") { requestScanner() })
        header.addView(iconAction("dots", "Opsi lainnya") { showOptions() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        content.addView(header)
        content.addView(View(this), LinearLayout.LayoutParams(1, dp(200), 1f))
        badge = d.text(this, "", 10f, 0xffc8d8ff.toInt(), true).apply { letterSpacing = .12f }; content.addView(badge); d.gap(content, 10)
        title = d.text(this, "", 28f, d.white, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }; content.addView(title); d.gap(content, 5)
        artist = d.text(this, "", 16f, 0xffd4d5df.toInt()); content.addView(artist); d.gap(content, 18)
        seek = SeekBar(this).apply {
            max = 1000; contentDescription = "Posisi lagu"
            val track = d.shape(0x66ffffff, 4f, c = context)
            val fill = ClipDrawable(d.shape(0xfff6f6fa.toInt(), 4f, c = context), Gravity.LEFT, ClipDrawable.HORIZONTAL)
            progressDrawable = LayerDrawable(arrayOf(track, fill)).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
            thumb = ColorDrawable(Color.TRANSPARENT); maxHeight = dp(5); minHeight = dp(5); setPadding(0, 0, 0, 0)
        }; content.addView(seek, LinearLayout.LayoutParams(-1, dp(48)))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) elapsed.text = formatTime(Bridge.durationMs * value / 1000) }
            override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(bar: SeekBar?) { seeking = false; MediaListener.instance?.execute("seek", Bridge.durationMs * (bar?.progress ?: 0) / 1000) }
        })
        val times = d.row(this); elapsed = d.text(this, "0:00", 11f, d.muted); duration = d.text(this, "--:--", 11f, d.muted)
        times.addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f)); times.addView(duration); content.addView(times); d.gap(content, 12)
        val controls = d.row(this).apply { gravity = Gravity.CENTER }
        previous = PlayerButton(this, "previous").apply { contentDescription = "Lagu sebelumnya"; setOnClickListener { MediaListener.instance?.execute("previous", null) } }
        play = PlayerButton(this, "play").apply { contentDescription = "Putar"; setOnClickListener { MediaListener.instance?.execute(if (Bridge.mediaPlaying) "pause" else "play", null) } }
        next = PlayerButton(this, "next").apply { contentDescription = "Lagu berikutnya"; setOnClickListener { MediaListener.instance?.execute("next", null) } }
        controls.addView(previous, LinearLayout.LayoutParams(0, dp(64), 1f)); controls.addView(play, LinearLayout.LayoutParams(0, dp(76), 1f)); controls.addView(next, LinearLayout.LayoutParams(0, dp(64), 1f)); content.addView(controls); d.gap(content, 16)
        permissionAction = d.action(this, "Aktifkan akses notifikasi") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }; content.addView(permissionAction); d.gap(content, 8)
        val connectionCard = d.card(this, padding = 14); val connectionRow = d.row(this)
        connectionRow.addView(Glyph(this, "laptop", d.white), LinearLayout.LayoutParams(dp(24), dp(24)))
        connection = d.text(this, "", 12f, d.white, true); connectionRow.addView(connection, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) }); connectionCard.addView(connectionRow); content.addView(connectionCard); d.gap(content, 8)
        reconnect = d.action(this, "Sambungkan ulang") { runCatching { BridgeService.start(this) }; Bridge.reconnectSaved() }; content.addView(reconnect); d.gap(content, 8)
        content.addView(d.action(this, "Cari desktop di sekitar") { showDevices() })
        render()
    }
    private fun formatTime(ms: Long): String { val seconds = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(seconds / 60, seconds % 60) }
    private fun iconAction(kind: String, description: String, action: () -> Unit): View =
        Glyph(this, kind, BridgeDesign.white).apply {
            setPadding(dp(13), dp(13), dp(13), dp(13)); background = GlassSurface.background(this, 24f)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)); contentDescription = description
            isClickable = true; isFocusable = true; setOnClickListener { action() }
        }
    private fun showOptions() {
        page = "options"; discovery?.close(); discovery = null
        val d = BridgeDesign; val content = screen(); val header = d.row(this)
        header.addView(d.action(this, "‹") { showHome() }, LinearLayout.LayoutParams(dp(50), dp(50)))
        val heading = d.column(this)
        heading.addView(d.text(this, "Pengaturan", 18f, d.white, true))
        heading.addView(d.text(this, "Atur media dan perangkat", 11f, d.muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
        content.addView(header); d.gap(content, 28)
        content.addView(d.text(this, "MEDIA", 10f, d.primary, true).apply { letterSpacing = .12f }); d.gap(content, 10)
        val mediaCard = d.card(this, d.surface, d.line, 8)
        mediaCard.addView(d.rowAction(this, "Pilih aplikasi media", "music") { chooseSource() })
        mediaCard.addView(d.rowAction(this, "Akses notifikasi", "shield") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        content.addView(mediaCard); d.gap(content, 24)
        content.addView(d.text(this, "KONEKSI LAPTOP", 10f, d.primary, true).apply { letterSpacing = .12f }); d.gap(content, 10)
        val deviceCard = d.card(this, d.surface, d.line, 8)
        deviceCard.addView(d.rowAction(this, "Sambungkan ulang", "refresh") { runCatching { BridgeService.start(this) }; Bridge.reconnectSaved(); showHome() })
        deviceCard.addView(d.rowAction(this, "Putuskan koneksi", "disconnect") { Bridge.disconnect(); showHome() })
        deviceCard.addView(d.rowAction(this, "Lupakan laptop", "laptop") {
            AlertDialog.Builder(this).setTitle("Lupakan laptop?")
                .setMessage("Pairing harus dilakukan ulang. Untuk menghapus izin sepenuhnya, lupakan HP ini juga di laptop.")
                .setNegativeButton("Batal", null).setPositiveButton("Lupakan") { _, _ -> Bridge.forget(); showHome() }.show()
        })
        content.addView(deviceCard)
    }
    private fun showDevices() {
        page = "devices"
        val d = BridgeDesign; val content = screen(); val header = d.row(this)
        header.addView(d.action(this, "‹") { showHome() }, LinearLayout.LayoutParams(dp(50), dp(50)))
        val labels = d.column(this)
        labels.addView(d.text(this, "Cari laptop", 18f, d.white, true))
        labels.addView(d.text(this, "Perangkat di jaringan yang sama", 11f, d.muted))
        header.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        content.addView(header); d.gap(content, 25)
        content.addView(d.text(this, "PERANGKAT DI SEKITAR", 10f, d.primary, true).apply { letterSpacing = .12f })
        d.gap(content, 8); content.addView(d.text(this, "Pilih laptopmu.", 24f, d.white, true)); d.gap(content, 8)
        content.addView(d.text(this, "Pastikan Notch terbuka dan memakai Wi-Fi yang sama.", 13f, d.muted))
        d.gap(content, 12); discoveryRadar = DiscoveryRadar(this); content.addView(discoveryRadar, LinearLayout.LayoutParams(-1, dp(180)))
        devices = d.column(this); content.addView(devices, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(d.action(this, "Cari ulang") { discover() }, LinearLayout.LayoutParams(-1, -2))
        d.gap(content, 8)
        content.addView(d.action(this, "Hubungkan lewat kode QR", true) { requestScanner() }, LinearLayout.LayoutParams(-1, -2))
        discover()
    }
    private fun render() {
        if (!::backdrop.isInitialized) return
        backdrop.update(Bridge.displayArtwork)
        if (page != "home") return
        val allowed = MediaListener.instance != null
        permissionAction.visibility = if (allowed) View.GONE else View.VISIBLE
        badge.text = when {
            !allowed -> "AKSES MEDIA DIPERLUKAN"
            !Bridge.mediaActive -> "MENUNGGU MEDIA"
            Bridge.mediaPlaying -> "MEDIA TERDETEKSI"
            else -> "MEDIA DIJEDA"
        }
        title.text = if (!allowed) "Aktifkan akses media" else Bridge.currentMedia
        artist.text = when {
            !allowed -> "Izinkan akses notifikasi untuk membaca lagu"
            !Bridge.mediaActive -> "Putar lagu di aplikasi musikmu"
            Bridge.currentArtist.isNotBlank() -> Bridge.currentArtist
            else -> "Media dari aplikasi di HP"
        }
        connection.text = Bridge.status
        seek.isEnabled = "seek" in Bridge.mediaActions && Bridge.durationMs > 0
        if (!seeking) seek.progress = if (Bridge.durationMs > 0) (Bridge.positionMs.coerceAtMost(Bridge.durationMs) * 1000 / Bridge.durationMs).toInt() else 0
        if (!seeking) elapsed.text = formatTime(Bridge.positionMs)
        duration.text = if (Bridge.durationMs > 0) formatTime(Bridge.durationMs) else "--:--"
        play.playing = Bridge.mediaPlaying; play.contentDescription = if (Bridge.mediaPlaying) "Jeda" else "Putar"
        listOf(previous to "previous", next to "next", play to if (Bridge.mediaPlaying) "pause" else "play").forEach { (button, action) -> button.isEnabled = action in Bridge.mediaActions; button.alpha = if (button.isEnabled) 1f else .35f }
        reconnect.visibility = if (Bridge.hasTrustedDevice() && Bridge.connectionState != "connected" && Bridge.connectionState != "rejected") View.VISIBLE else View.GONE
    }
    private fun chooseSource() {
        val list = Bridge.sources
        if (list.isEmpty()) { Toast.makeText(this, "Belum ada sesi media aktif", Toast.LENGTH_SHORT).show(); return }
        val labels = arrayOf("Otomatis") + list.toTypedArray()
        AlertDialog.Builder(this).setTitle("Sumber media").setItems(labels) { _, index ->
            MediaListener.instance?.select(if (index == 0) null else list[index - 1])
        }.show()
    }
    private fun discover() {
        discovery?.close(); devices.removeAllViews()
        devices.addView(BridgeDesign.text(this, "Mencari laptop di jaringan lokal…", 13f, BridgeDesign.muted))
        var latestFound = emptyList<Endpoint>()
        lateinit var current: Discovery
        current = Discovery(this) { found -> runOnUiThread {
            if (isFinishing || isDestroyed || discovery !== current || page != "devices") return@runOnUiThread
            latestFound = found; devices.removeAllViews()
            discoveryRadar.update(found.map { it.name })
            discoveryRadar.onDevice = { index -> found.getOrNull(index)?.let(::confirmPair) }
            if (found.isEmpty()) devices.addView(BridgeDesign.text(this, "Belum ada laptop ditemukan. Buka Notch lalu cari ulang.", 13f, BridgeDesign.muted))
            else {
                fun pick(endpoint: Endpoint) { confirmPair(endpoint) }
                found.take(3).forEach { endpoint ->
                    val card = BridgeDesign.card(this, BridgeDesign.surface, BridgeDesign.line, 6)
                    card.addView(BridgeDesign.rowAction(this, endpoint.name, "laptop") { pick(endpoint) })
                    devices.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                }
                if (found.size > 3) devices.addView(BridgeDesign.action(this, "Lihat semua laptop") {
                    AlertDialog.Builder(this).setTitle("Pilih laptop").setItems(found.map { it.name }.toTypedArray()) { _, index -> pick(found[index]) }.show()
                })
            }
        } }
        discovery = current; current.start()
        devices.postDelayed({
            if (discovery === current && latestFound.isEmpty() && page == "devices" && !isFinishing && !isDestroyed) {
                devices.removeAllViews()
                devices.addView(BridgeDesign.text(this, "Belum ada laptop ditemukan. Buka Notch lalu cari ulang.", 13f, BridgeDesign.muted))
            }
        }, 8000)
    }
    override fun onResume() { super.onResume(); render(); if (page == "devices" && discovery == null) discover() }
    override fun onPause() { discovery?.close(); discovery = null; super.onPause() }
    @Deprecated("Android callback") override fun onBackPressed() { if (page != "home") showHome() else super.onBackPressed() }
    private fun requestScanner() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openScanner()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
    }
    private fun openScanner() { @Suppress("DEPRECATION") startActivityForResult(Intent(this, QrScannerActivity::class.java), 11) }
    @Deprecated("Android callback") override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 11 || resultCode != RESULT_OK) return
        val endpoint = runCatching {
            val raw = data?.getStringExtra("qr") ?: error("missing")
            require(raw.startsWith("hesewake-media:1:") && raw.length <= 2048)
            val json = JSONObject(String(Wire.unb64(raw.removePrefix("hesewake-media:1:")), Charsets.UTF_8))
            val host = json.getString("host"); val port = json.getInt("port"); val fp = json.getString("fingerprint")
            require(host.length in 1..255 && port in 1..65535 && fp.matches(Regex("[0-9a-f]{64}")))
            Endpoint("Laptop", host, port, fp)
        }.getOrNull()
        if (endpoint == null) Toast.makeText(this, "QR tidak valid", Toast.LENGTH_SHORT).show()
        else confirmPair(endpoint)
    }
    private fun confirmPair(endpoint: Endpoint) {
        fun start() { getSharedPreferences("session", MODE_PRIVATE).edit().putBoolean("active", true).apply(); runCatching { BridgeService.start(this) }; Bridge.pair(endpoint); if (page != "home") showHome() }
        if (!Bridge.hasTrustedDevice()) { start(); return }
        AlertDialog.Builder(this).setTitle("Ganti laptop tepercaya?")
            .setMessage("Bridge menyimpan satu laptop. Pairing baru akan menggantikan laptop sebelumnya setelah berhasil.")
            .setNegativeButton("Batal", null).setPositiveButton("Lanjutkan") { _, _ -> start() }.show()
    }
    @Deprecated("Android callback") override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openScanner()
        else if (requestCode == 10) Toast.makeText(this, "Izin kamera diperlukan untuk scan QR", Toast.LENGTH_SHORT).show()
    }
    override fun onDestroy() {
        activeDecision?.invoke(false); activeDecision = null; discovery?.close(); pairDialog?.dismiss()
        Bridge.onChanged = null; Bridge.onPairCode = null; super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
