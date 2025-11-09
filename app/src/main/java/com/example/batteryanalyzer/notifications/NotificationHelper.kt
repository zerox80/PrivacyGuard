package com.example.batteryanalyzer.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.batteryanalyzer.R
import com.example.batteryanalyzer.work.UsageSyncWorker

object NotificationHelper {

    fun showPendingDisableNotification(context: Context, appLabel: String) {
        val builder = NotificationCompat.Builder(context, UsageSyncWorker.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notification_disable_title))
            .setContentText(context.getString(R.string.notification_disable_text, appLabel))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(appLabel.hashCode(), builder.build())
    }
}
