package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import kotlin.math.abs

data class SidecarSource(
    val directoryPath: String,
    val records: List<SidecarTrackRecord>,
)

data class SidecarPatch(
    val trackId: Long,
    val title: String?,
    val artist: String?,
    val durationMs: Long?,
    val bpm: Double?,
    val source: BpmSource?,
    val tags: List<SidecarTag>?,
)

fun mergeSidecarSources(sources: List<SidecarSource>): List<SidecarTrackRecord> {
    val records = mutableListOf<SidecarTrackRecord>()
    sources.filter { it.directoryPath.isNotEmpty() }.forEach { source ->
        val prefix = normalizePath(source.directoryPath).trimEnd('/')
        source.records.forEach { record ->
            val legacyPath = record.legacyPath
            records += if (!prefix.isEmpty() && legacyPath != null) {
                record.copy(legacyPath = "$prefix/${normalizePath(legacyPath).trimStart('/')}")
            } else {
                record
            }
        }
    }
    sources.filter { it.directoryPath.isEmpty() }.forEach { source ->
        records += source.records
    }
    return records
}

fun planImport(
    tracks: List<TrackEntity>,
    records: List<SidecarTrackRecord>,
): List<SidecarPatch> {
    val byTrackId = LinkedHashMap<Long, SidecarPatch>()
    records.forEach { record ->
        val track = findUniqueMatch(tracks, record) ?: return@forEach
        val patch = buildPatch(track, record)
        val current = byTrackId[track.id]
        byTrackId[track.id] = if (current == null) patch else mergePatches(current, patch)
    }
    return byTrackId.values.toList()
}

private fun mergePatches(current: SidecarPatch, next: SidecarPatch): SidecarPatch = SidecarPatch(
    trackId = current.trackId,
    title = next.title?.takeIf { it.isNotBlank() } ?: current.title,
    artist = next.artist?.takeIf { it.isNotBlank() } ?: current.artist,
    durationMs = next.durationMs?.takeIf { it > 0 } ?: current.durationMs,
    bpm = next.bpm ?: current.bpm,
    source = next.source ?: current.source,
    tags = next.tags?.takeIf { it.isNotEmpty() } ?: current.tags ?: next.tags,
)

private fun findUniqueMatch(
    tracks: List<TrackEntity>,
    record: SidecarTrackRecord,
): TrackEntity? {
    val candidates = tracks.filter { sameName(it.fileName, record.fileName) && durationCompatible(it, record) }
    if (candidates.isEmpty()) return null
    val legacyPath = record.legacyPath?.let(::normalizePath)
    if (legacyPath != null) {
        val byPath = candidates.filter { normalizePath(it.relativePath) == legacyPath }
        if (byPath.size == 1) return byPath.single()
    }
    if (candidates.size == 1) return candidates.single()
    val bestScore = candidates.maxOf { score(it, record) }
    val best = candidates.filter { score(it, record) == bestScore }
    return if (best.size == 1) best.single() else null
}

private fun sameName(trackName: String, recordName: String): Boolean =
    trackName.equals(recordName, ignoreCase = true)

private fun durationCompatible(track: TrackEntity, record: SidecarTrackRecord): Boolean {
    val trackDuration = track.durationMs
    val recordDuration = record.durationMs
    if (trackDuration <= 0 || recordDuration == null || recordDuration <= 0) return true
    return abs(trackDuration - recordDuration) <= DURATION_TOLERANCE_MS
}

private fun score(track: TrackEntity, record: SidecarTrackRecord): Int {
    var value = 0
    if (track.durationMs > 0 && record.durationMs != null && record.durationMs > 0 &&
        abs(track.durationMs - record.durationMs) <= DURATION_TOLERANCE_MS
    ) {
        value++
    }
    if (metaEquals(track.title, record.title)) value++
    if (metaEquals(track.artist, record.artist)) value++
    return value
}

private fun metaEquals(trackValue: String, recordValue: String?): Boolean {
    if (recordValue.isNullOrBlank() || trackValue.isBlank()) return false
    return trackValue.trim().equals(recordValue.trim(), ignoreCase = true)
}

private fun buildPatch(track: TrackEntity, record: SidecarTrackRecord): SidecarPatch = SidecarPatch(
    trackId = track.id,
    title = record.title,
    artist = record.artist,
    durationMs = record.durationMs?.takeIf { it > 0 },
    bpm = record.bpm?.takeIf { it in 1.0..999.0 },
    source = record.source,
    tags = record.tags,
)

fun trackToRecord(
    track: TrackEntity,
    tags: List<SidecarTag>,
): SidecarTrackRecord = SidecarTrackRecord(
    fileName = track.fileName,
    title = track.title.takeIf { it.isNotEmpty() },
    artist = track.artist.takeIf { it.isNotEmpty() },
    durationMs = track.durationMs.takeIf { it > 0 },
    bpm = track.bpm,
    source = track.bpmSource,
    tags = tags.takeIf { it.isNotEmpty() },
)

private const val DURATION_TOLERANCE_MS = 2000L