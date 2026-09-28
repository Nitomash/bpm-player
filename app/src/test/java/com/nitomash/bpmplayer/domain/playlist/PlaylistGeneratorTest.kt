package com.nitomash.bpmplayer.domain.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaylistGeneratorTest {

    private fun track(id: Long, bpm: Double) = PlaylistTrack(id, bpm)

    private val library = listOf(
        track(1, 100.0),
        track(2, 120.0),
        track(3, 90.0),
        track(4, 110.0),
        track(5, 120.0),
        track(6, 80.0),
    )

    @Test
    fun `ascending order sorts by bpm`() {
        val result = PlaylistGenerator.generate(library, null, null, 10, PlaylistOrder.ASCENDING)
        assertEquals(result.map { it.bpm }, result.map { it.bpm }.sorted())
        assertEquals(library.size, result.size)
    }

    @Test
    fun `descending order sorts by bpm`() {
        val result = PlaylistGenerator.generate(library, null, null, 10, PlaylistOrder.DESCENDING)
        assertEquals(result.map { it.bpm }, result.map { it.bpm }.sortedDescending())
    }

    @Test
    fun `only min keeps tracks not below it`() {
        val result = PlaylistGenerator.generate(library, 100.0, null, 10, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(setOf(1L, 4L, 2L, 5L), result.map { it.trackId }.toSet())
    }

    @Test
    fun `range filters tracks`() {
        val result = PlaylistGenerator.generate(library, 100.0, 120.0, 10, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(setOf(1L, 4L, 2L, 5L), result.map { it.trackId }.toSet())
        assertEquals(result.map { it.bpm }.sorted(), result.map { it.bpm })
    }

    @Test
    fun `only max keeps tracks not above it`() {
        val result = PlaylistGenerator.generate(library, null, 90.0, 10, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(listOf(6L, 3L), result.map { it.trackId })
    }

    @Test
    fun `no range takes whole library`() {
        val result = PlaylistGenerator.generate(library, null, null, 10, PlaylistOrder.ASCENDING)
        assertEquals(library.size, result.size)
    }

    @Test
    fun `count limits the result`() {
        val result = PlaylistGenerator.generate(library, null, null, 3, PlaylistOrder.ASCENDING)
        assertEquals(3, result.size)
    }

    @Test
    fun `fewer candidates than count returns all`() {
        val result = PlaylistGenerator.generate(library, null, 90.0, 50, PlaylistOrder.ASCENDING)
        assertEquals(2, result.size)
    }

    @Test
    fun `null count takes all matching tracks`() {
        val result = PlaylistGenerator.generate(library, 100.0, 120.0, null, PlaylistOrder.ASCENDING)
        assertEquals(setOf(1L, 4L, 2L, 5L), result.map { it.trackId }.toSet())
    }

    @Test
    fun `empty candidates returns empty`() {
        val result = PlaylistGenerator.generate(emptyList(), null, null, 30, PlaylistOrder.RANDOM)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `random order keeps set and respects count`() {
        val random = Random(42)
        val result = PlaylistGenerator.generate(library, null, null, 4, PlaylistOrder.RANDOM, random)
        assertEquals(4, result.size)
        assertTrue(library.map { it.trackId }.toSet().containsAll(result.map { it.trackId }))
    }

    @Test
    fun `random order shuffles the selected tracks`() {
        val random = PlaylistGenerator.generate(spread, 80.0, 180.0, 6, PlaylistOrder.RANDOM, Random(1))
        val ascending = PlaylistGenerator.generate(spread, 80.0, 180.0, 6, PlaylistOrder.ASCENDING, Random(1))

        assertEquals(6, random.size)
        assertEquals(ascending.map { it.trackId }.toSet(), random.map { it.trackId }.toSet())
        assertTrue(random.map { it.bpm } != random.map { it.bpm }.sorted())
    }

    @Test
    fun `zero count returns empty`() {
        val result = PlaylistGenerator.generate(library, null, null, 0, PlaylistOrder.RANDOM)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `random is deterministic for a seeded random`() {
        val a = PlaylistGenerator.generate(library, null, null, 5, PlaylistOrder.RANDOM, Random(7))
        val b = PlaylistGenerator.generate(library, null, null, 5, PlaylistOrder.RANDOM, Random(7))
        assertEquals(a, b)
    }

    @Test
    fun `ascending picks uniformly and sorts by bpm`() {
        val result = PlaylistGenerator.generate(library, null, null, 3, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(3, result.size)
        assertEquals(listOf(80.0, 100.0, 120.0), result.map { it.bpm })
    }

    @Test
    fun `ascending does not take the globally lowest tracks`() {
        val result = PlaylistGenerator.generate(library, null, null, 3, PlaylistOrder.ASCENDING, Random(42))
        assertEquals(3, result.size)
        val lowestIds = library.sortedBy { it.bpm }.take(3).map { it.trackId }.toSet()
        assertTrue(result.map { it.trackId }.toSet() != lowestIds)
    }

    @Test
    fun `descending picks a random subset and sorts it by bpm`() {
        val result = PlaylistGenerator.generate(library, null, null, 3, PlaylistOrder.DESCENDING, Random(5))
        assertEquals(3, result.size)
        assertEquals(result.map { it.bpm }.sortedDescending(), result.map { it.bpm })
    }

    private val spread = listOf(
        track(10, 80.0),
        track(11, 90.0),
        track(12, 100.0),
        track(13, 110.0),
        track(14, 120.0),
        track(15, 130.0),
        track(16, 140.0),
        track(17, 150.0),
        track(18, 160.0),
        track(19, 170.0),
        track(20, 180.0),
    )

    @Test
    fun `uniform first track is lowest bpm and last is highest`() {
        val result = PlaylistGenerator.generate(spread, null, null, 3, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(3, result.size)
        assertEquals(80.0, result.first().bpm, 0.0)
        assertEquals(180.0, result.last().bpm, 0.0)
    }

    @Test
    fun `uniform distributes tracks evenly across the interval`() {
        val result = PlaylistGenerator.generate(spread, 80.0, 180.0, 6, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(listOf(80.0, 100.0, 120.0, 140.0, 160.0, 180.0), result.map { it.bpm })
    }

    @Test
    fun `uniform middle slots fill nearest tracks to equally spaced targets`() {
        val result = PlaylistGenerator.generate(spread, null, 140.0, 4, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(listOf(80.0, 100.0, 120.0, 140.0), result.map { it.bpm })
    }

    @Test
    fun `uniform uses actual data extent when a bound is missing`() {
        val result = PlaylistGenerator.generate(spread, null, 120.0, 3, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(listOf(80.0, 100.0, 120.0), result.map { it.bpm })
    }

    @Test
    fun `uniform single track is nearest to interval center`() {
        val result = PlaylistGenerator.generate(spread, 80.0, 180.0, 1, PlaylistOrder.RANDOM, Random(1))
        assertEquals(listOf(130.0), result.map { it.bpm })
    }

    @Test
    fun `uniform returns full shuffled set when not enough candidates`() {
        val result = PlaylistGenerator.generate(library, null, null, 10, PlaylistOrder.RANDOM, Random(1))
        assertEquals(library.size, result.size)
        assertEquals(library.map { it.trackId }.toSet(), result.map { it.trackId }.toSet())
    }

    @Test
    fun `uniform never duplicates track ids`() {
        val clustered = listOf(
            track(30, 90.0),
            track(31, 91.0),
            track(32, 120.0),
            track(33, 121.0),
            track(34, 180.0),
            track(35, 181.0),
        )
        val result = PlaylistGenerator.generate(clustered, 90.0, 181.0, 3, PlaylistOrder.RANDOM, Random(3))
        assertEquals(3, result.size)
        assertEquals(3, result.map { it.trackId }.toSet().size)
    }

    @Test
    fun `uniform tie-breaks deterministically for a seeded random`() {
        val a = PlaylistGenerator.generate(spread, 80.0, 130.0, 3, PlaylistOrder.ASCENDING, Random(9))
        val b = PlaylistGenerator.generate(spread, 80.0, 130.0, 3, PlaylistOrder.ASCENDING, Random(9))
        assertEquals(a, b)
        assertEquals(80.0, a.first().bpm, 0.0)
        assertEquals(130.0, a.last().bpm, 0.0)
        assertTrue(a[1].bpm == 100.0 || a[1].bpm == 110.0)
    }

    @Test
    fun `uniform anchors pick randomly within 3 bpm slack`() {
        val pool = listOf(
            track(50, 90.0),
            track(51, 91.0),
            track(52, 92.0),
            track(53, 120.0),
            track(54, 160.0),
            track(55, 161.0),
            track(56, 162.0),
            track(57, 163.0),
        )
        val result = PlaylistGenerator.generate(pool, 90.0, 163.0, 3, PlaylistOrder.RANDOM, Random(1))
        assertEquals(3, result.size)
        assertTrue(result.first().bpm in 90.0..93.0)
        assertTrue(result.last().bpm in 160.0..163.0)
    }

    @Test
    fun `uniform middle slots pick randomly within 1 bpm slack`() {
        val pool = listOf(
            track(70, 90.0),
            track(71, 91.0),
            track(72, 104.0),
            track(73, 105.0),
            track(74, 106.0),
            track(75, 120.0),
        )
        val result = PlaylistGenerator.generate(pool, 90.0, 120.0, 3, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(3, result.size)
        assertTrue(result.first().bpm in 90.0..93.0)
        assertTrue(result[1].bpm in 104.0..106.0)
        assertEquals(120.0, result.last().bpm, 0.0)
    }

    @Test
    fun `uniform falls back to nearest track when window is empty`() {
        val pool = listOf(
            track(70, 90.0),
            track(71, 100.0),
            track(72, 115.0),
            track(73, 130.0),
        )
        val result = PlaylistGenerator.generate(pool, 85.0, 130.0, 3, PlaylistOrder.ASCENDING, Random(1))
        assertEquals(90.0, result.first().bpm, 0.0)
        assertTrue(result[1].bpm == 100.0 || result[1].bpm == 115.0)
        assertEquals(130.0, result.last().bpm, 0.0)
    }

    @Test
    fun `uniform keeps same bpm tracks when interval is narrow`() {
        val pool = listOf(
            track(80, 100.0),
            track(81, 100.0),
            track(82, 100.0),
            track(83, 102.0),
            track(84, 102.0),
            track(85, 102.0),
            track(86, 102.0),
        )
        val result = PlaylistGenerator.generate(pool, 100.0, 102.0, 5, PlaylistOrder.RANDOM, Random(1))
        assertEquals(5, result.size)
        assertEquals(5, result.map { it.trackId }.toSet().size)
        assertEquals(2, result.map { it.bpm }.toSet().size)
    }

    @Test
    fun `uniform keeps anchor distribution when count exceeds interval width`() {
        val pool = listOf(
            track(80, 100.0),
            track(81, 100.5),
            track(82, 101.0),
            track(83, 101.5),
            track(84, 102.0),
            track(85, 102.5),
            track(86, 103.0),
        )
        val result = PlaylistGenerator.generate(pool, 100.0, 103.0, 5, PlaylistOrder.RANDOM, Random(1))
        assertEquals(5, result.size)
        assertEquals(5, result.map { it.trackId }.toSet().size)
        assertTrue(result.first().bpm in 100.0..103.0)
        assertTrue(result.last().bpm in 100.0..103.0)
    }
}