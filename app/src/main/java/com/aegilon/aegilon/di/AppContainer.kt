package com.aegilon.aegilon.di

import android.content.Context
import com.aegilon.aegilon.data.local.AppDatabase
import com.aegilon.aegilon.data.repository.UsageRepository
import com.aegilon.aegilon.domain.ApplicationManager
import com.aegilon.aegilon.domain.UsageAnalyzer
import com.aegilon.aegilon.domain.UsagePolicy
import com.aegilon.aegilon.firewall.FirewallController
import com.aegilon.aegilon.firewall.FirewallPreferencesDataSource
import com.aegilon.aegilon.settings.SettingsPreferencesDataSource

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val database: AppDatabase = AppDatabase.getInstance(appContext)
    private val trackedAppDao = database.trackedAppDao()

    private val usagePolicy: UsagePolicy = UsagePolicy()

    val usageAnalyzer: UsageAnalyzer = UsageAnalyzer(appContext, trackedAppDao, usagePolicy)
    val applicationManager: ApplicationManager = ApplicationManager(appContext, trackedAppDao)
    val usageRepository: UsageRepository = UsageRepository(trackedAppDao)

    private val firewallPreferences: FirewallPreferencesDataSource = FirewallPreferencesDataSource(appContext)
    val firewallController: FirewallController = FirewallController(appContext, firewallPreferences)

    val settingsPreferences: SettingsPreferencesDataSource = SettingsPreferencesDataSource(appContext)
}
