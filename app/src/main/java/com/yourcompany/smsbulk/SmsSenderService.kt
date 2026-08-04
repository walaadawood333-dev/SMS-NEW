package com.yourcompany.smsbulk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SmsSenderService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_sender_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.yourcompany.smsbulk.ACTION_START"
        const val ACTION_STOP = "com.yourcompany.smsbulk.ACTION_STOP"

        const val EXTRA_NAMES = "extra_names"
        const val EXTRA_NUMBERS = "extra_numbers"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"

        // بث تحديثات التقدم إلى الواجهة
        const val ACTION_PROGRESS = "com.yourcompany.smsbulk.PROGRESS"
        const val EXTRA_CURRENT = "extra_current"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_FINISHED = "extra_finished"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var sendingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSending()
            }
            ACTION_START -> {
                val names = intent.getStringArrayListExtra(EXTRA_NAMES) ?: arrayListOf()
                val numbers = intent.getStringArrayListExtra(EXTRA_NUMBERS) ?: arrayListOf()
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 10)
                startSending(names, numbers, message, delaySeconds)
            }
        }
        return START_NOT_STICKY
    }

    private fun startSending(
        names: List<String>,
        numbers: List<String>,
        message: String,
        delaySeconds: Int
    ) {
        val total = numbers.size
        startForeground(NOTIFICATION_ID, buildNotification(0, total))

        sendingJob = serviceScope.launch {
            val smsManager = SmsManager.getDefault()

            for (index in numbers.indices) {
                if (!isActive) break

                val name = names.getOrElse(index) { "" }
                val number = numbers[index]
                val personalizedMessage = message.replace("{name}", name)

                var statusText: String
                try {
                    val parts = smsManager.divideMessage(personalizedMessage)
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                    statusText = "تم الإرسال إلى: $name"
                } catch (e: Exception) {
                    statusText = "فشل الإرسال إلى: $name"
                }

                val current = index + 1
                updateNotification(current, total)
                broadcastProgress(current, total, statusText, finished = false)

                // لا تنتظر بعد آخر رسالة
                if (current < total) {
                    delay(delaySeconds * 1000L)
                }
            }

            broadcastProgress(total, total, "اكتمل الإرسال لجميع العملاء", finished = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopSending() {
        sendingJob?.cancel()
        broadcastProgress(0, 0, "تم إيقاف الإرسال بواسطة المستخدم", finished = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcastProgress(current: Int, total: Int, status: String, finished: Boolean) {
        val intent = Intent(ACTION_PROGRESS).apply {
            putExtra(EXTRA_CURRENT, current)
            putExtra(EXTRA_TOTAL, total)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_FINISHED, finished)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(current: Int, total: Int): Notification {
        val stopIntent = Intent(this, SmsSenderService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("جاري إرسال الرسائل")
            .setContentText("$current من $total")
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(total, current, false)
            .setOngoing(true)
            .addAction(0, "إيقاف", stopPendingIntent)
            .build()
    }

    private fun updateNotification(current: Int, total: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(current, total))
    }

    override fun onDestroy() {
        super.onDestroy()
        sendingJob?.cancel()
    }
}
