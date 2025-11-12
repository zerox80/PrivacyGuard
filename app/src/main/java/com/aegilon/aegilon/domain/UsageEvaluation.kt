package com.aegilon.aegilon.domain

import com.aegilon.aegilon.data.local.TrackedAppEntity

data class UsageEvaluation(
    val updates: List<TrackedAppEntity>,
    val packagesToRemove: List<String>,
    val appsToNotify: List<TrackedAppEntity>,
    val appsForDisableRecommendation: List<TrackedAppEntity>
)
