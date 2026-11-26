package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.domain.linkimport.SupportedImportFormats

const val SIDECAR_EXTENSION = SupportedImportFormats.SIDECAR_EXTENSION
const val SIDECAR_MIME = "application/octet-stream"
const val SIDECAR_MAGIC = "#BPM-PLAYER-META v3"
const val SIDECAR_MAGIC_V2 = "#BPM-PLAYER-META v2"
const val SIDECAR_LEGACY_MAGIC = "#BPM-PLAYER-META v1"

data class SidecarTrackRecord(
    val fileName: String,
    val legacyPath: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val bpm: Double? = null,
    val source: BpmSource? = null,
    val tags: List<SidecarTag>? = null,
)

data class SidecarTag(
    val name: String,
    val rating: Int,
)

fun normalizePath(path: String): String =
    path.trim().replace('\\', '/').replace(Regex("/{2,}"), "/")

fun safeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]+"), "_").ifBlank { SIDECAR_EXTENSION }

fun fileNameOfPath(path: String): String? =
    normalizePath(path).substringAfterLast('/').takeIf { it.isNotEmpty() }

fun SidecarTrackRecord.hasPayload(): Boolean {
    if (bpm != null || source != null) return true
    if (!artist.isNullOrBlank()) return true
    if (durationMs != null && durationMs > 0) return true
    if (!tags.isNullOrEmpty()) return true
    val derivedTitle = fileName.substringBeforeLast('.')
    return !title.isNullOrBlank() && title.trim() != derivedTitle
}