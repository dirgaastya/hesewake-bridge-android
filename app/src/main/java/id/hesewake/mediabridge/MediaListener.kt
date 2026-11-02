package id.hesewake.mediabridge

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Base64
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Reads only media sessions; other notification contents are never inspected or transmitted. */
class MediaListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var controller: MediaController? = null
    private val artworkWorker = Executors.newSingleThreadExecutor()
    private var artworkVersion = 0L
    private lateinit var manager: MediaSessionManager
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { cachedArtworkKey = null; publish() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { refresh(); publish() }
        override fun onSessionDestroyed() = refresh()
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
    private val tick = object : Runnable {
        override fun run() { publish(); handler.postDelayed(this, 1000) }
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Bridge.init(applicationContext)
        manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        runCatching { manager.addOnActiveSessionsChangedListener(sessions, ComponentName(this, MediaListener::class.java), handler) }
        refresh()
        handler.removeCallbacks(tick); handler.post(tick)
    }
    override fun onListenerDisconnected() { cleanup(); super.onListenerDisconnected() }
    override fun onDestroy() { cleanup(); artworkWorker.shutdownNow(); super.onDestroy() }
    private fun cleanup() {
        handler.removeCallbacks(tick)
        artworkVersion++; cachedArtworkKey = null; cachedArtwork = null
        if (::manager.isInitialized) runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        controller?.unregisterCallback(callback); controller = null
        if (instance === this) instance = null
        Bridge.mediaUnavailable()
    }
    private fun refresh() {
        if (!::manager.isInitialized) return
        val list = runCatching { manager.getActiveSessions(ComponentName(this, MediaListener::class.java)) }.getOrDefault(emptyList())
        val chosen = list.firstOrNull { it.packageName == selectedPackage }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        Bridge.sources = list.map { it.packageName }.distinct()
        if (controller?.sessionToken != chosen?.sessionToken) {
            controller?.unregisterCallback(callback)
            artworkVersion++; cachedArtworkKey = null; cachedArtwork = null
            controller = chosen
            chosen?.registerCallback(callback, handler)
        }
        publish()
    }
    fun select(packageName: String?) { selectedPackage = packageName; refresh() }
    private fun publish() {
        val target = controller ?: run { artworkVersion++; cachedArtworkKey = null; cachedArtwork = null; Bridge.mediaUnavailable(); return }
        val metadata = target.metadata
        val state = target.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        val speed = if (playing) state?.playbackSpeed?.coerceIn(0f, 4f) ?: 1f else 0f
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
        val rawPos = state?.position?.coerceAtLeast(0) ?: 0
        val age = if (playing && (state?.lastPositionUpdateTime ?: 0) > 0) (SystemClock.elapsedRealtime() - state!!.lastPositionUpdateTime).coerceAtLeast(0) else 0
        val position = (rawPos + (age * speed).toLong()).let { if (duration != null) it.coerceAtMost(duration) else it }
        val flags = state?.actions ?: 0
        val actions = JSONArray()
        if (flags and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) != 0L) actions.put("play")
        if (flags and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) != 0L) actions.put("pause")
        if (flags and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) actions.put("next")
        if (flags and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) actions.put("previous")
        if (flags and PlaybackState.ACTION_SEEK_TO != 0L) actions.put("seek")
        val songKey = listOf(target.packageName, metadata?.getString(MediaMetadata.METADATA_KEY_TITLE), metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST), metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)).joinToString("|")
        if (songKey != cachedArtworkKey) {
            cachedArtworkKey = songKey
            cachedArtwork = null
            Bridge.updateDisplayArtwork(null)
            val version = ++artworkVersion
            val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val uri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            artworkWorker.execute {
                val source = runCatching { readArtworkUri(uri) }.getOrNull() ?: bitmap
                val encoded = runCatching { encodeArtwork(source) }.getOrNull()
                val display = runCatching { source?.let { scaleArtwork(it, 1600) } }.getOrNull() ?: source
                handler.post {
                    if (instance === this && artworkVersion == version && cachedArtworkKey == songKey) {
                        cachedArtwork = encoded; Bridge.updateDisplayArtwork(display); publish()
                    }
                }
                val remote = runCatching { readRemoteArtwork(uri)?.let { scaleArtwork(it, 1600) } }.getOrNull()
                if (remote != null) handler.post {
                    if (instance === this && artworkVersion == version && cachedArtworkKey == songKey) Bridge.updateDisplayArtwork(remote)
                }
            }
        }
        val json = JSONObject().put("active", true).put("source", target.packageName)
            .put("title", metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().take(1000))
            .put("artist", metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().take(1000))
            .put("album", metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty().take(1000))
            .put("artwork_mime", if (cachedArtwork == null) JSONObject.NULL else "image/jpeg")
            .put("artwork_base64", cachedArtwork ?: JSONObject.NULL).put("position_ms", position)
            .put("duration_ms", duration ?: JSONObject.NULL).put("playing", playing).put("speed", speed.toDouble()).put("actions", actions)
        Bridge.updateMedia(json)
    }
    private fun readArtworkUri(value: String?): Bitmap? {
        val uri = value?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        if (uri.scheme != "content") return null
        val data = contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                total += count; if (total > 8 * 1024 * 1024) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    private fun readRemoteArtwork(value: String?): Bitmap? {
        val address = ArtworkUrl.higherResolution(value) ?: return null
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            if (connection.responseCode != 200 || connection.contentType?.startsWith("image/", ignoreCase = true) != true) return null
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
                while (true) { val count = input.read(buffer); if (count < 0) break; total += count; if (total > 8 * 1024 * 1024) return null; output.write(buffer, 0, count) }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally { connection.disconnect() }
    }
    private fun scaleArtwork(bitmap: Bitmap, maximum: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maximum) return bitmap
        val scale = maximum.toDouble() / maxSide
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }
    private fun encodeArtwork(bitmap: Bitmap?): String? {
        bitmap ?: return null
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 0) return null
        val scale = minOf(1.0, 512.0 / maxSide)
        val thumb = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        return try {
            val out = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 75, out)
            val bytes = out.toByteArray()
            if (bytes.size <= 262144) Base64.encodeToString(bytes, Base64.NO_WRAP) else null
        } finally { if (thumb !== bitmap) thumb.recycle() }
    }
    fun execute(action: String, position: Long?): Boolean {
        val c = controller ?: return false
        val flags = c.playbackState?.actions ?: return false
        return runCatching {
            when (action) {
                "play" -> {
                    if (flags and PlaybackState.ACTION_PLAY != 0L) c.transportControls.play()
                    else if (flags and PlaybackState.ACTION_PLAY_PAUSE != 0L) {
                        if (c.playbackState?.state != PlaybackState.STATE_PLAYING) toggle(c)
                    } else return false
                }
                "pause" -> {
                    if (flags and PlaybackState.ACTION_PAUSE != 0L) c.transportControls.pause()
                    else if (flags and PlaybackState.ACTION_PLAY_PAUSE != 0L) {
                        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) toggle(c)
                    } else return false
                }
                "next" -> { if (flags and PlaybackState.ACTION_SKIP_TO_NEXT == 0L) return false; c.transportControls.skipToNext() }
                "previous" -> { if (flags and PlaybackState.ACTION_SKIP_TO_PREVIOUS == 0L) return false; c.transportControls.skipToPrevious() }
                "seek" -> {
                    if (flags and PlaybackState.ACTION_SEEK_TO == 0L || position == null || position < 0) return false
                    val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
                    c.transportControls.seekTo(if (duration != null) position.coerceAtMost(duration) else position)
                }
                else -> return false
            }; true
        }.getOrDefault(false)
    }
    private fun toggle(c: MediaController) {
        check(c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)))
        c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
    companion object {
        @Volatile var instance: MediaListener? = null
            private set
        private var selectedPackage: String? = null
        private var cachedArtworkKey: String? = null
        private var cachedArtwork: String? = null
    }
}
