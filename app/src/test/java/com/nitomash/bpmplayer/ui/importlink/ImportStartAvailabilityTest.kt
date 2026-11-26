package com.nitomash.bpmplayer.ui.importlink

import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRoot
import com.nitomash.bpmplayer.domain.linkimport.ImportJobState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportStartAvailabilityTest {

    private val root = AppLibraryRoot("content://root", "Music")

    @Test
    fun `download waits for resolved name`() {
        val state = ImportUiState(link = "https://disk.example/set", root = root)

        assertFalse(state.canStart)

        assertTrue(state.copy(sourceName = "set.bpmmeta").canStart)
    }

    @Test
    fun `sidecar download requires target folder`() {
        val state = ImportUiState(
            link = "https://disk.example/set.bpmmeta",
            root = root,
            sourceName = "set.bpmmeta",
            isSidecar = true,
        )

        assertFalse(state.canStart)

        assertTrue(state.copy(selectedFolderId = 7L).canStart)
    }

    @Test
    fun `inspection and running job block download`() {
        val state = ImportUiState(link = "https://disk.example/set", root = root, sourceName = "set")

        assertFalse(state.copy(isInspecting = true).canStart)
        assertFalse(state.copy(jobState = ImportJobState.Queued).canStart)
    }

    @Test
    fun `failed job switches download to resume`() {
        val state = ImportUiState(
            link = "https://disk.example/set",
            root = root,
            sourceName = "set",
            jobState = ImportJobState.Failed("error", 0, 0),
        )

        assertFalse(state.canStart)
        assertTrue(state.canResume)
        assertFalse(state.copy(sourceName = null).canResume)
    }

    @Test
    fun `collection download waits for resolved name`() {
        val state = ImportUiState(
            folderName = "Set",
            root = root,
            updateLinks = listOf(UpdateLinkState("https://disk.example/set")),
        )

        assertFalse(state.canStartCollection)
        assertTrue(state.copy(sourceName = "Set").canStartCollection)
        assertFalse(
            state.copy(sourceName = "Set", updateLinks = emptyList()).canStartCollection,
        )
    }

    @Test
    fun `missing link or root blocks download`() {
        val named = ImportUiState(sourceName = "set", root = root)

        assertFalse(named.canStart)
        assertFalse(named.copy(link = "https://disk.example/set", root = null).canStart)
    }
}
