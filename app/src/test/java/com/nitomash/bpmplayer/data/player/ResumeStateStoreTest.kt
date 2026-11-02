package com.nitomash.bpmplayer.data.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResumeStateStoreTest {

    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: ResumeStateStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("bpm-resume").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "resume.preferences_pb")
        }
        store = ResumeStateStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun `load returns null before first save`() = runBlocking {
        assertNull(store.load())
    }

    @Test
    fun `playlist queue is stored and read back`() = runBlocking {
        val state = ResumeState(
            playlistId = 7L,
            trackIds = listOf(11L, 12L, 13L),
            currentTrackId = 12L,
            positionMs = 42_500L,
            name = null,
        )

        store.save(state)

        assertEquals(state, store.load())
    }

    @Test
    fun `generated queue keeps its name`() = runBlocking {
        val state = ResumeState(
            playlistId = null,
            trackIds = listOf(1L, 2L),
            currentTrackId = 2L,
            positionMs = 1_000L,
            name = "Сгенерированный плейлист",
        )

        store.save(state)

        assertEquals(state, store.load())
    }

    @Test
    fun `clear removes the saved queue`() = runBlocking {
        store.save(
            ResumeState(
                playlistId = 3L,
                trackIds = listOf(1L),
                currentTrackId = 1L,
                positionMs = 0L,
                name = null,
            ),
        )

        store.clear()

        assertNull(store.load())
    }

    @Test
    fun `save after clear wins over the cleared state`() = runBlocking {
        val second = ResumeState(
            playlistId = null,
            trackIds = listOf(5L, 6L),
            currentTrackId = 6L,
            positionMs = 9_000L,
            name = "Новый",
        )

        store.save(
            ResumeState(
                playlistId = 1L,
                trackIds = listOf(2L),
                currentTrackId = 2L,
                positionMs = 500L,
                name = null,
            ),
        )
        store.clear()
        store.save(second)

        assertEquals(second, store.load())
    }

    @Test
    fun `empty track list is not restored`() = runBlocking {
        store.save(
            ResumeState(
                playlistId = null,
                trackIds = emptyList(),
                currentTrackId = null,
                positionMs = 0L,
                name = null,
            ),
        )

        assertNull(store.load())
    }

    @Test
    fun `negative position is coerced to zero`() = runBlocking {
        store.save(
            ResumeState(
                playlistId = 4L,
                trackIds = listOf(8L),
                currentTrackId = 8L,
                positionMs = -5_000L,
                name = null,
            ),
        )

        assertEquals(0L, store.load()?.positionMs)
    }

    @Test
    fun `track ids roundtrip`() {
        val ids = listOf(1L, 22L, 333L)

        assertEquals(ids, decodeTrackIds(encodeTrackIds(ids)))
    }

    @Test
    fun `broken track ids are dropped`() {
        assertEquals(listOf(1L, 2L), decodeTrackIds("1,abc, 2 ,"))
        assertTrue(decodeTrackIds(null).isEmpty())
        assertTrue(decodeTrackIds("").isEmpty())
    }
}
