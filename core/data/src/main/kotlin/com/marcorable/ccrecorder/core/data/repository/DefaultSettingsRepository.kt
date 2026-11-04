package com.marcorable.ccrecorder.core.data.repository

import com.marcorable.ccrecorder.core.data.SettingsRepository
import com.marcorable.ccrecorder.core.datastore.SettingsDataStore
import com.marcorable.ccrecorder.core.model.DisplayMode
import com.marcorable.ccrecorder.core.model.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : SettingsRepository {
    override val preferences: Flow<UserPreferences> = settingsDataStore.preferences

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        settingsDataStore.setOnboardingCompleted(completed)

    override suspend fun setDisplayMode(mode: DisplayMode) = settingsDataStore.setDisplayMode(mode)

    override suspend fun setSkipIntervalSeconds(seconds: Int) =
        settingsDataStore.setSkipIntervalSeconds(seconds)

    override suspend fun setKeepScreenAwake(keepAwake: Boolean) =
        settingsDataStore.setKeepScreenAwake(keepAwake)
}
