package com.example.batteryanalyzer.di

import android.content.Context
import com.example.batteryanalyzer.data.local.AppDatabase
import com.example.batteryanalyzer.data.repository.UsageRepository
import com.example.batteryanalyzer.domain.ApplicationManager
import com.example.batteryanalyzer.domain.UsageAnalyzer

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val database: AppDatabase = AppDatabase.getInstance(appContext)
    private val trackedAppDao = database.trackedAppDao()

    val usageAnalyzer: UsageAnalyzer = UsageAnalyzer(appContext, trackedAppDao)
    val applicationManager: ApplicationManager = ApplicationManager(appContext, trackedAppDao)
    val usageRepository: UsageRepository = UsageRepository(trackedAppDao)
}
