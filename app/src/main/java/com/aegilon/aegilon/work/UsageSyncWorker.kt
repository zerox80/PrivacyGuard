package com.aegilon.aegilon.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aegilon.aegilon.AegilonApp
import com.aegilon.aegilon.util.UsagePermissionChecker

class UsageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as AegilonApp
        val container = app.container
        val context = container.appContext

        if (!UsagePermissionChecker.isUsageAccessGranted(context)) {
            return Result.retry()
        }

        return runCatching {
            val evaluation = container.usageAnalyzer.evaluateUsage()
            container.usageRepository.applyEvaluation(evaluation)

            Result.success()
        }.getOrElse { throwable ->
            Log.e(TAG, "Usage sync failed", throwable)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "usage_sync_worker"
        const val NOTIFICATION_CHANNEL_ID = "usage_status_channel"
        private const val TAG = "UsageSyncWorker"
    }
}
