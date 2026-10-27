package ar.com.argentas.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Keeps the control process important while the tablet screen is off.
 * The TCP/UDP listeners are still owned by MainActivity in this incremental,
 * low-risk change; moving their ownership into this service is a separate refactor.
 */
class ControlKeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ARGENTAS · Control remoto",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Mantiene disponible el control del hotspot con la pantalla apagada."
                    setShowBadge(false)
                }
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("ARGENTAS · Control activo")
            .setContentText("Preparado para recibir órdenes aunque la pantalla esté apagada.")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        try {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ARGENTAS:ControlKeepAlive")
                .apply { setReferenceCounted(false); acquire() }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo adquirir el bloqueo parcial de energía", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando el bloqueo de energía", e)
        }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ArgentasKeepAlive"
        private const val CHANNEL_ID = "argentas_control_keepalive"
        private const val NOTIFICATION_ID = 38471
    }
}
