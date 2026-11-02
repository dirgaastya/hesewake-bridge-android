package id.hesewake.mediabridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** Keeps a user-started bridge session visible while Android allows it to run. */
class BridgeService : Service() {
    private var lastText = ""
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Koneksi Bridge", NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, notification())
        Bridge.onServiceChanged = { refresh() }
        Bridge.init(applicationContext)
        refresh()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { Bridge.disconnect(); stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BridgeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val detail = if (Bridge.connectionState == "connected" && Bridge.mediaActive) "${Bridge.currentMedia} · ${Bridge.currentArtist}" else Bridge.status
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Hesewake Bridge").setContentText(detail).setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(true).addAction(Notification.Action.Builder(null, "Putuskan", stop).build())
            .apply { if (Build.VERSION.SDK_INT >= 36) addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) }) }.build()
    }
    private fun refresh() {
        val text = "${Bridge.connectionState}|${Bridge.status}|${Bridge.currentMedia}|${Bridge.currentArtist}|${Bridge.mediaActive}"
        if (text == lastText) return
        lastText = text
        getSystemService(NotificationManager::class.java).notify(ID, notification())
    }
    override fun onDestroy() { Bridge.onServiceChanged = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "bridge_connection"
        private const val ID = 7401
        private const val ACTION_STOP = "id.hesewake.mediabridge.STOP"
        fun start(context: Context) { context.startForegroundService(Intent(context, BridgeService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, BridgeService::class.java)) }
    }
}
