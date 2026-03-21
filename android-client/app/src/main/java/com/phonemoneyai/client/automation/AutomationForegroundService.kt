package com.phonemoneyai.client.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phonemoneyai.client.R

class AutomationForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification(intent.getStringExtra(EXTRA_STATUS) ?: getString(R.string.status_running_local)))
            }
            ACTION_UPDATE -> {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(intent.getStringExtra(EXTRA_STATUS) ?: getString(R.string.status_running_local)))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.foreground_channel_description)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "automation_runner"
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_STATUS = "extra_status"
        const val ACTION_START = "com.phonemoneyai.client.automation.START"
        const val ACTION_UPDATE = "com.phonemoneyai.client.automation.UPDATE"
        const val ACTION_STOP = "com.phonemoneyai.client.automation.STOP"

        fun start(context: Context, status: String) {
            context.startForegroundService(
                Intent(context, AutomationForegroundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_STATUS, status),
            )
        }

        fun update(context: Context, status: String) {
            context.startService(
                Intent(context, AutomationForegroundService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_STATUS, status),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AutomationForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
