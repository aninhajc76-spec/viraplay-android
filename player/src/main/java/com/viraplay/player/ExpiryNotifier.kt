package com.viraplay.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.viraplay.shared.XtreamAccountInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

object ExpiryNotifier {
    private const val CHANNEL_ID = "viraplay_access"
    private const val NOTIFICATION_ID = 3102
    private const val FIVE_DAYS_MS = 5L * 24L * 60L * 60L * 1000L

    fun notice(info: XtreamAccountInfo?): String? {
        val epoch = info?.expiresAtEpochSeconds ?: return null
        val remaining = epoch * 1000L - System.currentTimeMillis()
        return when {
            remaining <= 0L -> "Seu acesso venceu. Renove pelo Suporte ViraPlay."
            remaining <= 6L * 60L * 60L * 1000L -> {
                val mins = ceil(remaining / 60_000.0).toLong().coerceAtLeast(1)
                val h = mins / 60
                val m = mins % 60
                if (h > 0) "Seu acesso vence em ${h}h ${m}min." else "Seu acesso vence em ${m} minutos."
            }
            remaining <= 24L * 60L * 60L * 1000L -> "Seu acesso vence hoje."
            remaining <= FIVE_DAYS_MS -> {
                val days = ceil(remaining / 86_400_000.0).toInt().coerceAtLeast(1)
                "Seu acesso vence em $days dia(s)."
            }
            else -> null
        }
    }

    fun displayDate(info: XtreamAccountInfo?): String? {
        val epoch = info?.expiresAtEpochSeconds ?: return null
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(epoch * 1000L))
    }

    fun notifyIfNeeded(context: Context, info: XtreamAccountInfo?) {
        val message = notice(info) ?: return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val prefs = context.getSharedPreferences("viraplay_expiry_notifications", Context.MODE_PRIVATE)
        val bucket = notificationBucket(info)
        if (prefs.getString("last_bucket", null) == bucket) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Avisos ViraPlay", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ViraPlay")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        prefs.edit().putString("last_bucket", bucket).apply()
    }

    private fun notificationBucket(info: XtreamAccountInfo?): String {
        val epoch = info?.expiresAtEpochSeconds ?: return "none"
        val remaining = epoch * 1000L - System.currentTimeMillis()
        return when {
            remaining <= 0L -> "expired-$epoch"
            remaining <= 6L * 60L * 60L * 1000L -> "hours-$epoch-${remaining / 3_600_000L}"
            remaining <= 24L * 60L * 60L * 1000L -> "today-$epoch"
            else -> "days-$epoch-${remaining / 86_400_000L}"
        }
    }
}
