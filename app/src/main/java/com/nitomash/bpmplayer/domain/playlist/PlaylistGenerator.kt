package com.nitomash.bpmplayer.domain.playlist

import kotlin.math.abs
import kotlin.random.Random

enum class PlaylistOrder {
    RANDOM,
    TEMPO_ALTERNATION,
    ASCENDING,
    DESCENDING,
    TITLE,
}

enum class PlaylistSelectionAlgorithm {
    UNIFORM,
}

data class PlaylistTrack(val trackId: Long, val bpm: Double, val title: String = "")

object PlaylistGenerator {

    private const val ANCHOR_SLACK_BPM = 3.0
    private const val TARGET_SLACK_BPM = 1.0

    fun generate(
        candidates: List<PlaylistTrack>,
        minBpm: Double?,
        maxBpm: Double?,
        count: Int?,
        order: PlaylistOrder,
        random: Random = Random.Default,
        algorithm: PlaylistSelectionAlgorithm = PlaylistSelectionAlgorithm.UNIFORM,
    ): List<PlaylistTrack> {
        val filtered = candidates.filter { matchesBpm(it.bpm, minBpm, maxBpm) }
        val limit = count?.coerceAtLeast(0) ?: filtered.size
        val selected = when (algorithm) {
            PlaylistSelectionAlgorithm.UNIFORM -> selectUniform(filtered, minBpm, maxBpm, limit, random)
        }
        return when (order) {
            PlaylistOrder.RANDOM -> selected.shuffled(random)
            PlaylistOrder.ASCENDING -> selected.sortedBy { it.bpm }
            PlaylistOrder.DESCENDING -> selected.sortedByDescending { it.bpm }
            PlaylistOrder.TITLE -> selected.sortedBy { it.title.trim().lowercase() }
            PlaylistOrder.TEMPO_ALTERNATION -> alternateByTempo(selected, random)
        }
    }

    private fun selectUniform(
        pool: List<PlaylistTrack>,
        minBpm: Double?,
        maxBpm: Double?,
        limit: Int,
        random: Random,
        anchorSlackBpm: Double = ANCHOR_SLACK_BPM,
        targetSlackBpm: Double = TARGET_SLACK_BPM,
    ): List<PlaylistTrack> {
        if (pool.isEmpty() || limit == 0) return emptyList()
        if (pool.size <= limit) return pool.shuffled(random)
        val (lower, upper) = intervalBounds(pool, minBpm, maxBpm)
        val remaining = pool.sortedBy { it.bpm }.toMutableList()
        if (limit == 1) {
            val center = (lower + upper) / 2
            return listOf(pickNear(remaining, center, center - targetSlackBpm..center + targetSlackBpm, random))
        }
        return fillUniformGrid(remaining, lower, upper, limit, random, anchorSlackBpm, targetSlackBpm)
    }

    private fun intervalBounds(pool: List<PlaylistTrack>, minBpm: Double?, maxBpm: Double?): Pair<Double, Double> {
        val extent = pool.minOf { it.bpm } to pool.maxOf { it.bpm }
        return (minBpm ?: extent.first) to (maxBpm ?: extent.second)
    }

    private fun fillUniformGrid(
        remaining: MutableList<PlaylistTrack>,
        lower: Double,
        upper: Double,
        limit: Int,
        random: Random,
        anchorSlackBpm: Double,
        targetSlackBpm: Double,
    ): List<PlaylistTrack> {
        val span = upper - lower
        val result = ArrayList<PlaylistTrack>(limit)
        result.add(pickNear(remaining, lower, lower..lower + anchorSlackBpm, random))
        for (slot in 1 until limit - 1) {
            val target = lower + span * slot / (limit - 1)
            result.add(pickNear(remaining, target, target - targetSlackBpm..target + targetSlackBpm, random))
        }
        result.add(pickNear(remaining, upper, upper - anchorSlackBpm..upper, random))
        return result
    }

    private fun pickNear(
        pool: MutableList<PlaylistTrack>,
        target: Double,
        window: ClosedRange<Double>,
        random: Random,
    ): PlaylistTrack {
        val inWindow = pool.filter { it.bpm in window }
        if (inWindow.isEmpty()) {
            return pickNearest(pool, target, random)
        }
        val chosen = inWindow.shuffled(random).first()
        pool.remove(chosen)
        return chosen
    }

    private fun pickNearest(pool: MutableList<PlaylistTrack>, target: Double, random: Random): PlaylistTrack {
        val nearest = pool.groupBy { abs(it.bpm - target) }.minBy { it.key }.value
        val chosen = if (nearest.size == 1) nearest[0] else nearest.shuffled(random).first()
        pool.remove(chosen)
        return chosen
    }

    private fun alternateByTempo(tracks: List<PlaylistTrack>, random: Random): List<PlaylistTrack> {
        if (tracks.size <= 1) return tracks.shuffled(random)
        val sorted = tracks.sortedBy { it.bpm }
        val groups = splitIntoTempoGroups(sorted).map { it.shuffled(random).toMutableList() }.toMutableList()
        val result = ArrayList<PlaylistTrack>(tracks.size)
        var previousGroup = -1
        while (result.size < tracks.size) {
            val candidates = groups.indices.filter { it != previousGroup && groups[it].isNotEmpty() }
            val chosenGroup = if (candidates.isNotEmpty()) {
                val maxSize = candidates.maxOf { groups[it].size }
                val largest = candidates.filter { groups[it].size == maxSize }
                largest[random.nextInt(largest.size)]
            } else {
                groups.indices.first { groups[it].isNotEmpty() }
            }
            result.add(groups[chosenGroup].removeAt(groups[chosenGroup].lastIndex))
            previousGroup = chosenGroup
        }
        return result
    }

    private fun splitIntoTempoGroups(sortedByBpm: List<PlaylistTrack>): List<List<PlaylistTrack>> {
        val n = sortedByBpm.size
        val base = n / 3
        val remainder = n % 3
        val sizes = IntArray(3) { base + if (it < remainder) 1 else 0 }
        val groups = ArrayList<List<PlaylistTrack>>(3)
        var index = 0
        for (size in sizes) {
            groups.add(sortedByBpm.subList(index, index + size))
            index += size
        }
        return groups
    }

    private fun matchesBpm(bpm: Double, min: Double?, max: Double?): Boolean = when {
        min != null && max != null -> bpm in min..max
        min != null -> bpm >= min
        max != null -> bpm <= max
        else -> true
    }
}