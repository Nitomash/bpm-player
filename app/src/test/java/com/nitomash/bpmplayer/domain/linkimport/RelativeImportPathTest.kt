package com.nitomash.bpmplayer.domain.linkimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeImportPathTest {

    @Test
    fun `of splits path and drops traversal segments`() {
        assertEquals("Disc A/Mix", RelativeImportPath.of("Disc A/Mix")?.value)
        assertEquals("Disc A/Mix", RelativeImportPath.of("../Disc A/./Mix/..")?.value)
        assertEquals("", RelativeImportPath.of("")?.value)
        assertEquals("", RelativeImportPath.of("..")?.value)
    }

    @Test
    fun `of replaces characters forbidden by file systems`() {
        assertEquals("a_b_c_d", RelativeImportPath.of("a\\b:c*d")?.value)
        assertEquals("track", RelativeImportPath.of("  ...track  ")?.value)
    }

    @Test
    fun `child appends a sanitized segment`() {
        val parent = requireNotNull(RelativeImportPath.of("Disc A"))
        assertEquals("Disc A/track.mp3", RelativeImportPath.child(parent, "track.mp3")?.value)
        assertEquals("Disc A/track_mp3", RelativeImportPath.child(parent, "track:mp3")?.value)
        assertNull(RelativeImportPath.child(parent, ".."))
        assertNull(RelativeImportPath.child(parent, "  "))
    }

    @Test
    fun `child of root has no leading separator`() {
        assertEquals("track.mp3", RelativeImportPath.child(RelativeImportPath.ROOT, "track.mp3")?.value)
    }

    @Test
    fun `parent and name split the path back`() {
        val path = requireNotNull(RelativeImportPath.of("Disc A/Sub/track.mp3"))
        assertEquals("Disc A/Sub", RelativeImportPath.parentOf(path).value)
        assertEquals("track.mp3", RelativeImportPath.nameOf(path))

        val rootChild = requireNotNull(RelativeImportPath.of("track.mp3"))
        assertEquals("", RelativeImportPath.parentOf(rootChild).value)
        assertEquals("track.mp3", RelativeImportPath.nameOf(rootChild))
    }
}
