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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var radar: MediaRadar
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var badge: TextView
    private lateinit var connection: TextView
    private lateinit var permissionAction: TextView
    private lateinit var devices: LinearLayout
    private var page = "home"
    private var discovery: Discovery? = null
    private var pairDialog: AlertDialog? = null
    private var activeDecision: ((Boolean) -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(applicationContext)
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
    private fun screen(): LinearLayout = BridgeDesign.column(this).apply {
        setPadding(dp(22), dp(16), dp(22), dp(18)); setBackgroundColor(BridgeDesign.background)
        BridgeDesign.systemInsets(this, dp(22), dp(16), dp(22), dp(18)); setContentView(this)
    }
    private fun showHome() {
        page = "home"; discovery?.close(); discovery = null
        val d = BridgeDesign; val content = screen(); val header = d.row(this)
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_bridge_foreground); background = d.shape(d.inset, 13f, d.line, context)
            contentDescription = "Hesewake Bridge"
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val brand = d.column(this)
        brand.addView(d.text(this, "Hesewake Bridge", 15f, d.white, true))
        brand.addView(d.text(this, "Media dari HP ke laptop", 11f, d.muted))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
        header.addView(iconAction("qr", "Scan kode QR") { requestScanner() })
        header.addView(iconAction("dots", "Opsi lainnya") { showOptions() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        content.addView(header); d.gap(content, 24)
        content.addView(d.text(this, "NOW PLAYING", 10f, d.primary, true).apply { letterSpacing = .12f })
        d.gap(content, 6)
        content.addView(d.text(this, "Musikmu, terhubung.", 25f, d.white, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        d.gap(content, 5)
        content.addView(d.text(this, "Media yang aktif di HP akan muncul di sini.", 12f, d.muted))
        radar = MediaRadar(this); content.addView(radar, LinearLayout.LayoutParams(-1, 0, 1f))
        badge = d.text(this, "", 10f, d.green, true).apply { gravity = Gravity.CENTER }
        content.addView(badge); d.gap(content, 7)
        title = d.text(this, "", 18f, d.white, true).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        content.addView(title); d.gap(content, 4)
        artist = d.text(this, "", 12f, d.muted).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        content.addView(artist); d.gap(content, 18)
        permissionAction = d.action(this, "Aktifkan akses notifikasi") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        content.addView(permissionAction, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val card = d.card(this, d.surface, d.line, 12); val row = d.row(this)
        row.addView(Glyph(this, "laptop", d.primary), LinearLayout.LayoutParams(dp(32), dp(32)))
        val labels = d.column(this)
        connection = d.text(this, "", 13f, d.white, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        labels.addView(connection); d.gap(labels, 3)
        labels.addView(d.text(this, "HP dan laptop di Wi-Fi yang sama", 11f, d.muted))
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        card.addView(row); content.addView(card); d.gap(content, 12)
        content.addView(d.action(this, "Cari laptop di sekitar", true) { showDevices() }, LinearLayout.LayoutParams(-1, -2))
        render()
    }
    private fun iconAction(kind: String, description: String, action: () -> Unit): View =
        Glyph(this, kind, BridgeDesign.white).apply {
            setPadding(dp(13), dp(13), dp(13), dp(13)); background = BridgeDesign.shape(BridgeDesign.inset, 14f, BridgeDesign.line, context)
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
        deviceCard.addView(d.rowAction(this, "Sambungkan ulang", "refresh") { Bridge.reconnectSaved(); showHome() })
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
        d.gap(content, 18); devices = d.column(this); content.addView(devices, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(d.action(this, "Cari ulang") { discover() }, LinearLayout.LayoutParams(-1, -2))
        d.gap(content, 8)
        content.addView(d.action(this, "Hubungkan lewat kode QR", true) { requestScanner() }, LinearLayout.LayoutParams(-1, -2))
        discover()
    }
    private fun render() {
        if (page != "home" || !::radar.isInitialized) return
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
        radar.update(Bridge.currentArtwork, allowed && Bridge.mediaActive)
        connection.text = Bridge.status
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
    override fun onResume() { super.onResume(); if (::radar.isInitialized) render(); if (page == "devices" && discovery == null) discover() }
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
        fun start() { Bridge.pair(endpoint); if (page != "home") showHome() }
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
