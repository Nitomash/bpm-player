package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.domain.linkimport.SupportedImportFormats

internal fun staleTrackPaths(
    existingPaths: Collection<String>,
    scannedPaths: Set<String>,
): Set<String> {
    val unsupported = existingPaths.filterNot { path ->
        SupportedImportFormats.isSupportedAudioExtension(path.substringAfterLast('/'))
    }
    return (existingPaths.toSet() - scannedPaths) + unsupported
}
