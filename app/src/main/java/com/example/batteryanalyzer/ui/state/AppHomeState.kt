package com.example.batteryanalyzer.ui.state

import com.example.batteryanalyzer.model.AppUsageInfo

data class AppHomeState(
    val usagePermissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val recentApps: List<AppUsageInfo> = emptyList(),
    val rareApps: List<AppUsageInfo> = emptyList(),
    val disabledApps: List<AppUsageInfo> = emptyList()
)
