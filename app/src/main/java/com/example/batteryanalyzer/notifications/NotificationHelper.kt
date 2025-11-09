package com.example.batteryanalyzer.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.batteryanalyzer.R
import com.example.batteryanalyzer.work.UsageSyncWorker

object NotificationHelper {

    fun showPendingDisableNotification(context: Context, appLabel: String, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return
        }

        val builder = NotificationCompat.Builder(context, UsageSyncWorker.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notification_disable_title))
            .setContentText(context.getString(R.string.notification_disable_text, appLabel))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        manager.notify(packageName.hashCode(), builder.build())
    }
}
