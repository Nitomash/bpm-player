package com.nitomash.bpmplayer.domain.linkimport

data class ImportLimits(
    val maxDepth: Int = 32,
    val maxEntries: Int = 10_000,
    val maxKnownFileSizeBytes: Long = 2L * 1024 * 1024 * 1024,
)
