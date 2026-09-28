package com.nitomash.bpmplayer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SettingsRepositoryImplTest {

    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("bpm-settings").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "settings.preferences_pb")
        }
        repository = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun `current returns defaults on first run`() = runBlocking {
        assertEquals(AppSettings(), repository.current())
    }

    @Test
    fun `observe emits defaults on first run`() = runBlocking {
        assertEquals(AppSettings(), repository.observe().first())
    }

    @Test
    fun `update stores transformed value`() = runBlocking {
        repository.update { it.copy(schemaVersion = 2) }

        assertEquals(AppSettings(schemaVersion = 2), repository.current())
    }

    @Test
    fun `observe emits value after update`() = runBlocking {
        repository.update { it.copy(schemaVersion = 2) }

        assertEquals(AppSettings(schemaVersion = 2), repository.observe().first())
    }

    @Test
    fun `values survive reopen of the same file`() = runBlocking {
        repository.update { it.copy(schemaVersion = 2) }

        val reopened = SettingsRepositoryImpl(dataStore)

        assertEquals(AppSettings(schemaVersion = 2), reopened.current())
    }

    @Test
    fun `update keeps fields not touched by transform`() = runBlocking {
        repository.update { it.copy(schemaVersion = 2) }

        repository.update { it }

        assertEquals(AppSettings(schemaVersion = 2), repository.current())
    }

    @Test
    fun `detector range is stored and read back`() = runBlocking {
        repository.update { it.copy(detectorMinBpm = 90.0, detectorMaxBpm = 150.0) }

        val settings = repository.current()

        assertEquals(90.0, settings.detectorMinBpm, 0.001)
        assertEquals(150.0, settings.detectorMaxBpm, 0.001)
    }

    @Test
    fun `default detector range stays 40 to 160`() = runBlocking {
        val settings = repository.current()

        assertEquals(40.0, settings.detectorMinBpm, 0.001)
        assertEquals(160.0, settings.detectorMaxBpm, 0.001)
    }
}
