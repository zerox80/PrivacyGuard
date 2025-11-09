package com.example.batteryanalyzer.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.batteryanalyzer.BatteryAnalyzerApp
import com.example.batteryanalyzer.notifications.NotificationHelper
import com.example.batteryanalyzer.util.UsagePermissionChecker

class UsageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as BatteryAnalyzerApp
        val container = app.container
        val context = container.appContext

        if (!UsagePermissionChecker.isUsageAccessGranted(context)) {
            return Result.retry()
        }

        return runCatching {
            val evaluation = container.usageAnalyzer.evaluateUsage()
            container.usageRepository.applyEvaluation(evaluation)

            evaluation.appsToNotify.forEach { info ->
                NotificationHelper.showPendingDisableNotification(context, info.appLabel)
            }

            evaluation.appsToDisable.forEach { info ->
                container.applicationManager.disablePackage(info.packageName)
            }

            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "usage_sync_worker"
        const val NOTIFICATION_CHANNEL_ID = "usage_status_channel"
    }
}
