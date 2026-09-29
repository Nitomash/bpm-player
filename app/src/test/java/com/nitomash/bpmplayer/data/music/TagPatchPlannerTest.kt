package com.nitomash.bpmplayer.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPatchPlannerTest {

    @Test
    fun `blank names are dropped`() {
        val plan = planTagPatches(
            listOf(request(1L, tags = listOf(SidecarTag("", 3), SidecarTag("   ", 2), SidecarTag("rock", 4)))),
        )

        assertEquals(listOf("rock"), plan.entries.map { it.name })
    }

    @Test
    fun `ratings outside one to five are dropped`() {
        val plan = planTagPatches(
            listOf(
                request(
                    1L,
                    tags = listOf(
                        SidecarTag("zero", 0),
                        SidecarTag("six", 6),
                        SidecarTag("minus", -1),
                        SidecarTag("ok", 5),
                    ),
                ),
            ),
        )

        assertEquals(listOf("ok"), plan.entries.map { it.name })
    }

    @Test
    fun `first rating of a duplicated name wins`() {
        val plan = planTagPatches(
            listOf(request(1L, tags = listOf(SidecarTag("rock", 2), SidecarTag("rock", 5)))),
        )

        assertEquals(listOf(TagPatchEntry(1L, "rock", 2)), plan.entries)
    }

    @Test
    fun `entry order follows input order`() {
        val plan = planTagPatches(
            listOf(request(1L, tags = listOf(SidecarTag("b", 1), SidecarTag("a", 2), SidecarTag("c", 3)))),
        )

        assertEquals(listOf("b", "a", "c"), plan.entries.map { it.name })
        assertEquals(listOf(1L), plan.clearTrackIds)
    }

    @Test
    fun `empty tags without existing rows produce empty plan`() {
        val plan = planTagPatches(listOf(request(1L, tags = emptyList())))

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `empty tags clear existing rows`() {
        val plan = planTagPatches(
            listOf(request(1L, existing = listOf(SidecarTag("rock", 3)), tags = emptyList())),
        )

        assertEquals(listOf(1L), plan.clearTrackIds)
        assertTrue(plan.entries.isEmpty())
    }

    @Test
    fun `unchanged tags produce empty plan`() {
        val plan = planTagPatches(
            listOf(
                request(
                    1L,
                    existing = listOf(SidecarTag("rock", 3), SidecarTag("house", 5)),
                    tags = listOf(SidecarTag("rock", 3), SidecarTag("house", 5)),
                ),
            ),
        )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `changed rating rewrites the track`() {
        val plan = planTagPatches(
            listOf(
                request(
                    1L,
                    existing = listOf(SidecarTag("rock", 3)),
                    tags = listOf(SidecarTag("rock", 4)),
                ),
            ),
        )

        assertEquals(listOf(1L), plan.clearTrackIds)
        assertEquals(listOf(TagPatchEntry(1L, "rock", 4)), plan.entries)
    }

    @Test
    fun `reordered tags count as unchanged`() {
        val plan = planTagPatches(
            listOf(
                request(
                    1L,
                    existing = listOf(SidecarTag("rock", 3), SidecarTag("house", 5)),
                    tags = listOf(SidecarTag("house", 5), SidecarTag("rock", 3)),
                ),
            ),
        )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `only changed requests are planned`() {
        val plan = planTagPatches(
            listOf(
                request(1L, existing = listOf(SidecarTag("rock", 3)), tags = listOf(SidecarTag("rock", 3))),
                request(2L, existing = listOf(SidecarTag("rock", 3)), tags = listOf(SidecarTag("house", 2))),
                request(3L, tags = listOf(SidecarTag("jazz", 1))),
            ),
        )

        assertEquals(listOf(2L, 3L), plan.clearTrackIds)
        assertEquals(
            listOf(TagPatchEntry(2L, "house", 2), TagPatchEntry(3L, "jazz", 1)),
            plan.entries,
        )
    }

    @Test
    fun `dropped invalid rows still clear a track that keeps nothing`() {
        val plan = planTagPatches(
            listOf(request(1L, existing = listOf(SidecarTag("rock", 3)), tags = listOf(SidecarTag("rock", 9)))),
        )

        assertEquals(listOf(1L), plan.clearTrackIds)
        assertTrue(plan.entries.isEmpty())
    }

    @Test
    fun `empty request list produces empty plan`() {
        assertTrue(planTagPatches(emptyList()).isEmpty)
    }

    private fun request(
        trackId: Long,
        existing: List<SidecarTag> = emptyList(),
        tags: List<SidecarTag>,
    ) = TagPatchRequest(trackId = trackId, existing = existing, tags = tags)
}
