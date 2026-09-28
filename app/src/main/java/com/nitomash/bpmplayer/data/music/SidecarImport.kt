package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.TrackEntity

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
        source.records.forEach { record ->
            records += record.copy(path = "${source.directoryPath}/${record.path}")
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
    val byPath = HashMap<String, SidecarTrackRecord>(records.size)
    records.forEach { record ->
        byPath[normalizePath(record.path)] = record
    }
    return tracks.mapNotNull { track ->
        val record = byPath[normalizePath(track.relativePath)] ?: return@mapNotNull null
        buildPatch(track, record)
    }
}

fun planImportByName(
    tracks: List<TrackEntity>,
    records: List<SidecarTrackRecord>,
): List<SidecarPatch> {
    val byName = HashMap<String, SidecarTrackRecord>(records.size)
    records.forEach { record ->
        val name = recordFileName(record.path) ?: return@forEach
        if (!byName.containsKey(name)) byName[name] = record
    }
    return tracks.mapNotNull { track ->
        val record = byName[track.fileName] ?: return@mapNotNull null
        buildPatch(track, record)
    }
}

private fun buildPatch(track: TrackEntity, record: SidecarTrackRecord): SidecarPatch = SidecarPatch(
    trackId = track.id,
    title = record.title,
    artist = record.artist,
    durationMs = record.durationMs,
    bpm = record.bpm?.takeIf { it in 1.0..999.0 },
    source = record.source,
    tags = record.tags,
)

private fun recordFileName(path: String): String? =
    normalizePath(path).substringAfterLast('/').takeIf { it.isNotEmpty() }

fun trackToRecord(
    track: TrackEntity,
    tags: List<SidecarTag>,
): SidecarTrackRecord = SidecarTrackRecord(
    path = track.relativePath,
    title = track.title.takeIf { it.isNotEmpty() },
    artist = track.artist.takeIf { it.isNotEmpty() },
    durationMs = track.durationMs.takeIf { it > 0 },
    bpm = track.bpm,
    source = track.bpmSource,
    tags = tags.takeIf { it.isNotEmpty() },
)