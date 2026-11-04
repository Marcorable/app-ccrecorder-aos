package com.marcorable.ccrecorder.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.marcorable.ccrecorder.core.model.DisplayMode
import com.marcorable.ccrecorder.core.model.UserPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val reportInvalidValue: (String) -> Unit = {},
) {
    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                reportInvalidValue("Unable to read user preferences: ${error.message}")
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map(::decode)

    suspend fun setOnboardingCompleted(completed: Boolean) {
        normalizedEdit { it[PreferenceKeys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDisplayMode(displayMode: DisplayMode) {
        normalizedEdit { it[PreferenceKeys.DISPLAY_MODE] = displayMode.name }
    }

    suspend fun setSkipIntervalSeconds(seconds: Int) {
        require(seconds in UserPreferences.VALID_SKIP_INTERVAL_SECONDS) {
            "Unsupported skip interval: $seconds"
        }
        normalizedEdit { it[PreferenceKeys.SKIP_INTERVAL_SECONDS] = seconds }
    }

    suspend fun setKeepScreenAwake(keepAwake: Boolean) {
        normalizedEdit { it[PreferenceKeys.KEEP_SCREEN_AWAKE] = keepAwake }
    }

    private suspend fun normalizedEdit(change: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { mutable ->
            val normalized = decode(mutable)
            mutable[PreferenceKeys.ONBOARDING_COMPLETED] = normalized.onboardingCompleted
            mutable[PreferenceKeys.DISPLAY_MODE] = normalized.displayMode.name
            mutable[PreferenceKeys.SKIP_INTERVAL_SECONDS] = normalized.skipIntervalSeconds
            mutable[PreferenceKeys.KEEP_SCREEN_AWAKE] = normalized.keepScreenAwake
            change(mutable)
        }
    }

    private fun decode(preferences: Preferences): UserPreferences {
        val rawDisplayMode = preferences[PreferenceKeys.DISPLAY_MODE]
        val displayMode = rawDisplayMode
            ?.let { token -> DisplayMode.entries.firstOrNull { it.name == token } }
            ?: DisplayMode.SYSTEM
        if (rawDisplayMode != null && rawDisplayMode != displayMode.name) {
            reportInvalidValue("Unknown display_mode value: $rawDisplayMode")
        }

        val rawSkipInterval = preferences[PreferenceKeys.SKIP_INTERVAL_SECONDS]
        val skipInterval = rawSkipInterval
            ?.takeIf { it in UserPreferences.VALID_SKIP_INTERVAL_SECONDS }
            ?: UserPreferences.DEFAULT_SKIP_INTERVAL_SECONDS
        if (rawSkipInterval != null && rawSkipInterval != skipInterval) {
            reportInvalidValue("Unsupported skip_interval_seconds value: $rawSkipInterval")
        }

        return UserPreferences(
            onboardingCompleted = preferences[PreferenceKeys.ONBOARDING_COMPLETED] ?: false,
            displayMode = displayMode,
            skipIntervalSeconds = skipInterval,
            keepScreenAwake = preferences[PreferenceKeys.KEEP_SCREEN_AWAKE] ?: false,
        )
    }

    internal object PreferenceKeys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val SKIP_INTERVAL_SECONDS = intPreferencesKey("skip_interval_seconds")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
    }

    companion object {
        const val NAME = "user_preferences"
        const val FILE_NAME = "user_preferences.preferences_pb"
    }
}
