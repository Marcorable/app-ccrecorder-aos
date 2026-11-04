package com.marcorable.ccrecorder.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.marcorable.ccrecorder.core.model.DisplayMode
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsDataStoreTest {
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("settings-datastore").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun defaultsAndAllowedValuesPersist() = runTest {
        val file = File(directory, SettingsDataStore.FILE_NAME)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val settings = SettingsDataStore(dataStore)
        val defaults = settings.preferences.first()
        assertFalse(defaults.onboardingCompleted)
        assertEquals(DisplayMode.SYSTEM, defaults.displayMode)
        assertEquals(5, defaults.skipIntervalSeconds)
        assertFalse(defaults.keepScreenAwake)

        settings.setOnboardingCompleted(true)
        settings.setDisplayMode(DisplayMode.DARK)
        settings.setSkipIntervalSeconds(30)
        settings.setKeepScreenAwake(true)
        val persisted = settings.preferences.first()
        assertTrue(persisted.onboardingCompleted)
        assertEquals(DisplayMode.DARK, persisted.displayMode)
        assertEquals(30, persisted.skipIntervalSeconds)
        assertTrue(persisted.keepScreenAwake)

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val restarted = SettingsDataStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
        ).preferences.first()
        assertEquals(persisted, restarted)
    }

    @Test
    fun invalidValuesFallBackAndNextWriteNormalizesThem() = runTest {
        val messages = mutableListOf<String>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(directory, SettingsDataStore.FILE_NAME) },
        )
        dataStore.edit {
            it[SettingsDataStore.PreferenceKeys.DISPLAY_MODE] = "SEPIA"
            it[SettingsDataStore.PreferenceKeys.SKIP_INTERVAL_SECONDS] = 99
        }
        val settings = SettingsDataStore(dataStore, messages::add)
        val fallback = settings.preferences.first()
        assertEquals(DisplayMode.SYSTEM, fallback.displayMode)
        assertEquals(5, fallback.skipIntervalSeconds)
        assertEquals(2, messages.size)

        settings.setKeepScreenAwake(true)
        val normalized = dataStore.data.first()
        assertEquals("SYSTEM", normalized[SettingsDataStore.PreferenceKeys.DISPLAY_MODE])
        assertEquals(5, normalized[SettingsDataStore.PreferenceKeys.SKIP_INTERVAL_SECONDS])
    }
}
