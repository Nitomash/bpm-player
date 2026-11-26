package com.nitomash.bpmplayer.domain.linkimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportNameKeyTest {

    @Test
    fun `composed and decomposed names share one key`() {
        val pairs = listOf(
            "The Marías - Hush.mp3" to "The Mari\u0301as - Hush.mp3",
            "алёна швец. - первое свидание.mp3" to "але\u0308на швец. - первое свидание.mp3",
            "Amelíe - Comptine.mp3" to "Ameli\u0301e - Comptine.mp3",
            "다이나믹.mp3" to "\u1103\u1161\u110b\u1175\u1102\u1161\u1106\u1175\u11a8.mp3",
        )

        pairs.forEach { (composed, decomposed) ->
            assertEquals(ImportNameKey.of(composed), ImportNameKey.of(decomposed))
        }
    }

    @Test
    fun `different names keep different keys`() {
        assertNotEquals(ImportNameKey.of("a.mp3"), ImportNameKey.of("b.mp3"))
        assertNotEquals(ImportNameKey.of("\uFF21.mp3"), ImportNameKey.of("A.mp3"))
        assertNotEquals(ImportNameKey.of("track.MP3"), ImportNameKey.of("track.mp3"))
    }

    @Test
    fun `matches compares canonical forms`() {
        assertTrue(ImportNameKey.matches("Marías.mp3", "Mari\u0301as.mp3"))
        assertFalse(ImportNameKey.matches("Marías.mp3", "Maria.mp3"))
    }

    @Test
    fun `index finds an NFC entry by an NFD name`() {
        val entry = ImportEntry("The Marías - Hush.mp3", "doc:nfc", 7_299_018L)
        val index = ImportNameKey.index(listOf(entry))

        assertEquals(entry, index[ImportNameKey.of("The Mari\u0301as - Hush.mp3")])
    }
}
