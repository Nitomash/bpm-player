package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.BpmSource

const val SIDECAR_EXTENSION = "bpmmeta"
const val SIDECAR_MIME = "application/octet-stream"
const val SIDECAR_MAGIC = "#BPM-PLAYER-META v2"
const val SIDECAR_LEGACY_MAGIC = "#BPM-PLAYER-META v1"

data class SidecarTrackRecord(
    val path: String,
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