package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSelectionTest {

    private fun folder(id: Long, name: String): FolderEntity =
        FolderEntity(id = id, rootUri = "uri$id", displayName = name, addedAtMs = 0L)

    private val folders = listOf(
        folder(1, "House Club"),
        folder(2, "Chillout"),
        folder(3, "DnB Party"),
    )

    @Test
    fun `all folders flag takes every folder`() {
        assertEquals(setOf(1L, 2L, 3L), resolveFolderIdsForGeneration(folders, allFolders = true, selectedIds = emptySet()))
    }

    @Test
    fun `empty selection is treated as all folders`() {
        assertEquals(setOf(1L, 2L, 3L), resolveFolderIdsForGeneration(folders, allFolders = false, selectedIds = emptySet()))
    }

    @Test
    fun `single id filters exactly`() {
        assertEquals(setOf(2L), resolveFolderIdsForGeneration(folders, allFolders = false, selectedIds = setOf(2L)))
    }

    @Test
    fun `multiple ids union folder ids`() {
        assertEquals(
            setOf(1L, 3L),
            resolveFolderIdsForGeneration(folders, allFolders = false, selectedIds = setOf(1L, 3L)),
        )
    }

    @Test
    fun `unknown ids return empty set`() {
        assertEquals(emptySet<Long>(), resolveFolderIdsForGeneration(folders, allFolders = false, selectedIds = setOf(99L)))
    }

    @Test
    fun `unknown ids mixed with known keep only known`() {
        assertEquals(setOf(2L), resolveFolderIdsForGeneration(folders, allFolders = false, selectedIds = setOf(2L, 99L)))
    }
}