package com.nitomash.bpmplayer.domain.linkimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDuplicateNamesTest {

    private fun entry(name: String, uri: String) = ImportEntry(name, uri, 100L)

    @Test
    fun `counts only extra entries in canonical groups`() {
        val composed = entry("The Marías - Hush.mp3", "doc:1")
        val decomposed = entry("The Mari\u0301as - Hush.mp3", "doc:2")
        val entries = listOf(composed, decomposed, entry("Track.mp3", "doc:3"))

        assertEquals(1, ImportDuplicateNames.extraCount(entries))
        assertEquals(listOf(decomposed), ImportDuplicateNames.toDelete(entries))
    }

    @Test
    fun `keeps the composed form and drops the decomposed one`() {
        val composed = entry("The Marías - Hush.mp3", "doc:1")
        val decomposed = entry("The Mari\u0301as - Hush.mp3", "doc:2")

        val toDelete = ImportDuplicateNames.toDelete(listOf(decomposed, composed))

        assertEquals(listOf(decomposed), toDelete)
        assertEquals(composed, ImportDuplicateNames.keeperOf(listOf(decomposed, composed)))
    }

    @Test
    fun `exactly equal names keep a single file`() {
        val first = entry("Track.mp3", "doc:1")
        val second = entry("Track.mp3", "doc:2")

        assertEquals(1, ImportDuplicateNames.extraCount(listOf(first, second)))
        assertEquals(listOf(second), ImportDuplicateNames.toDelete(listOf(first, second)))
    }

    @Test
    fun `distinct names are untouched`() {
        val entries = listOf(
            entry("A.mp3", "doc:1"),
            entry("a.mp3", "doc:2"),
            entry("Ｂ.mp3", "doc:3"),
        )

        assertEquals(0, ImportDuplicateNames.extraCount(entries))
        assertTrue(ImportDuplicateNames.toDelete(entries).isEmpty())
    }
}
