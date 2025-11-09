package com.example.batteryanalyzer.domain

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.ArrayMap
import android.util.Log
import com.example.batteryanalyzer.data.local.AppUsageStatus
import com.example.batteryanalyzer.data.local.TrackedAppDao
import com.example.batteryanalyzer.data.local.TrackedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class UsageAnalyzer(
    private val context: Context,
    private val trackedAppDao: TrackedAppDao
) {

    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    suspend fun evaluateUsage(): UsageEvaluation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = trackedAppDao.getAll().associateBy { it.packageName }
        val installedApps = queryUserInstalledApps()

        val lastUsedMap = queryLastUsedTimestamps(now)

        val updates = mutableListOf<TrackedAppEntity>()
        val packagesToRemove = existing.keys.toMutableSet()
        val appsToNotify = mutableListOf<TrackedAppEntity>()
        val appsToDisable = mutableListOf<TrackedAppEntity>()

        for (appInfo in installedApps) {
            val packageName = appInfo.packageName
            packagesToRemove.remove(packageName)

            val label = packageManager.getApplicationLabel(appInfo).toString()
            val existingEntity = existing[packageName]
            val lastUsed = resolveLastUsed(packageName, lastUsedMap, existingEntity, appInfo)
            val isDisabled = isPackageDisabled(packageName)

            val status = when {
                isDisabled -> AppUsageStatus.DISABLED
                lastUsed != null && now - lastUsed <= RECENT_THRESHOLD -> AppUsageStatus.RECENT
                else -> AppUsageStatus.RARE
            }

            var scheduledDisableAt: Long? = null
            var notifiedAt = existingEntity?.notifiedAt

            if (!isDisabled) {
                val disableAt = lastUsed?.let { it + DISABLE_THRESHOLD }
                val notifyAt = lastUsed?.let { it + WARNING_THRESHOLD }
                if (disableAt != null) {
                    scheduledDisableAt = disableAt
                    if (now >= disableAt) {
                        appsToDisable += TrackedAppEntity(
                            packageName = packageName,
                            appLabel = label,
                            lastUsedAt = lastUsed,
                            status = AppUsageStatus.DISABLED,
                            isDisabled = true,
                            scheduledDisableAt = null,
                            notifiedAt = null
                        )
                    } else if (notifyAt != null && now >= notifyAt && (notifiedAt == null || notifiedAt < notifyAt)) {
                        val warningEntity = TrackedAppEntity(
                            packageName = packageName,
                            appLabel = label,
                            lastUsedAt = lastUsed,
                            status = status,
                            isDisabled = false,
                            scheduledDisableAt = disableAt,
                            notifiedAt = now
                        )
                        appsToNotify += warningEntity
                        notifiedAt = now
                    }
                } else {
                    notifiedAt = null
                }
            } else {
                notifiedAt = null
            }

            val entity = TrackedAppEntity(
                packageName = packageName,
                appLabel = label,
                lastUsedAt = lastUsed ?: 0L,
                status = status,
                isDisabled = isDisabled,
                scheduledDisableAt = scheduledDisableAt,
                notifiedAt = notifiedAt
            )
            updates += entity
        }

        UsageEvaluation(
            updates = updates,
            packagesToRemove = packagesToRemove.toList(),
            appsToNotify = appsToNotify,
            appsToDisable = appsToDisable
        )
    }

    private fun queryUserInstalledApps(): List<ApplicationInfo> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
        }
        val apps = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(flags)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(flags)
            }
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to query installed applications", throwable)
            emptyList()
        }

        return apps.filter { appInfo ->
            appInfo.packageName != context.packageName &&
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }
    }

    private fun queryLastUsedTimestamps(now: Long): Map<String, Long> {
        val map = ArrayMap<String, Long>()
        val startTime = now - TimeUnit.DAYS.toMillis(30)
        val events = usageStatsManager?.queryEvents(startTime, now) ?: return emptyMap()
        val usageEvent = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(usageEvent)
            if (usageEvent.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                usageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                val previous = map[usageEvent.packageName] ?: 0L
                if (usageEvent.timeStamp > previous) {
                    map[usageEvent.packageName] = usageEvent.timeStamp
                }
            }
        }
        return map
    }

    private fun resolveLastUsed(
        packageName: String,
        lastUsedMap: Map<String, Long>,
        existing: TrackedAppEntity?,
        appInfo: ApplicationInfo
    ): Long? {
        val fromUsage = lastUsedMap[packageName]
        if (fromUsage != null) return fromUsage

        val previous = existing?.lastUsedAt?.takeIf { it > 0 }
        if (previous != null) return previous

        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            maxOf(packageInfo.firstInstallTime, packageInfo.lastUpdateTime)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to get fallback timestamp for $packageName", t)
            null
        }
    }

    private fun isPackageDisabled(packageName: String): Boolean {
        return runCatching {
            packageManager.getApplicationEnabledSetting(packageName) in disabledStates
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "UsageAnalyzer"
        private val RECENT_THRESHOLD = TimeUnit.DAYS.toMillis(2)
        private val DISABLE_THRESHOLD = TimeUnit.DAYS.toMillis(4)
        private val WARNING_THRESHOLD = TimeUnit.DAYS.toMillis(3)
        private val disabledStates = setOf(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        )
    }
}
