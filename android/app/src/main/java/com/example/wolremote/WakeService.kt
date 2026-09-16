package com.example.wolremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager

class WakeService : Service() {

    companion object {
        private const val CHANNEL_ID = "wol_service"
        private const val NOTIF_ID = 1
        private const val CONFIRM_TIMEOUT_MS = 150_000L
    }

    private val volunlock = "wol"
    private var running = false
    private var thread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Serviciu WOL Remote",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:$volunlock")
            wakeLock?.setReferenceCounted(false)
        }
        wakeLock?.acquire()

        if (!running) {
            running = true
            thread = Thread({ pollingLoop() }, "wol-polling").apply {
                isDaemon = true
                start()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("WOL Remote activ")
            .setContentText("Monitorizează comenzile de wake de la distanță")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun pollingLoop() {
        val prefs = Prefs.get(this)
        while (running) {
            try {
                val intervalSeconds = prefs.getString(Prefs.KEY_INTERVAL, "5")
                    ?.toLongOrNull()?.coerceIn(1L, 60L) ?: 5L
                val server = prefs.getString(Prefs.KEY_SERVER, "")?.trim() ?: ""

                if (server.isNotBlank()) {
                    val result = Http.poll(server)
                    if (result.pending) {
                        val mac = prefs.getString(Prefs.KEY_MAC, "")?.trim() ?: ""
                        if (mac.isEmpty()) {
                            recordEvent("Comandă detectată dar MAC nu e configurat")
                        } else {
                            val broadcast = prefs.getString(Prefs.KEY_BROADCAST, "")?.trim() ?: ""
                            val target = WakeOnLan.send(
                                mac,
                                broadcast,
                                applicationContext
                            )
                            val targetIp = prefs.getString(Prefs.KEY_TARGET_IP, "")?.trim() ?: ""
                            val confirmed = targetIp.isNotBlank() && confirmBoot(targetIp)
                            Http.ack(server, if (confirmed) "up" else "sent")
                            recordEvent(if (confirmed) {
                                "Laptop pornit ✓ pachet trimis → $target"
                            } else {
                                "Wake trimis → $target, dar nu s-a confirmat pornirea"
                            })
                        }
                    }
                    Thread.sleep(intervalSeconds * 1000L)
                } else {
                    recordEvent("Serverul nu e configurat")
                    Thread.sleep(5000L)
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                recordEvent("Eroare: ${e.message ?: e.javaClass.simpleName}")
                Thread.sleep(5000L)
            }
        }
    }

    /**
     * Așteaptă ~2,5 minute ca laptopul să răspundă pe rețea, din 5 în 5 secunde.
     */
    private fun confirmBoot(targetIp: String): Boolean {
        if (targetIp.isBlank()) return true
        val deadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS
        while (running && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            if (HostCheck.isReachable(targetIp)) return true
        }
        return false
    }

    private fun recordEvent(message: String) {
        Prefs.get(this).edit().putString(Prefs.KEY_LAST_EVENT, message).apply()
    }
}