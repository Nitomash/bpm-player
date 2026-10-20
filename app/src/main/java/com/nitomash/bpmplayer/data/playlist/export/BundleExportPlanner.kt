package com.nitomash.bpmplayer.data.playlist.export

import com.nitomash.bpmplayer.data.music.SidecarTag
import com.nitomash.bpmplayer.data.music.SidecarTrackRecord
import com.nitomash.bpmplayer.data.playlist.xspf.XspfFileName
import com.nitomash.bpmplayer.data.playlist.xspf.XspfPlaylist
import com.nitomash.bpmplayer.data.playlist.xspf.XspfTrack

object BundleExportPlanner {

    fun planFolderName(playlistName: String, takenNames: Set<String>): String {
        val base = XspfFileName.sanitize(playlistName)
        if (base !in takenNames) return base
        var index = 1
        while ("$base ($index)" in takenNames) index++
        return "$base ($index)"
    }

    fun planJobs(tracks: List<ExportTrack>): List<CopyJob> {
        val taken = mutableSetOf<String>()
        return tracks.map { track ->
            val target = uniqueFileName(track.fileName, taken)
            taken += target
            CopyJob(track = track, targetName = target)
        }
    }

    fun buildXspf(playlistName: String, jobs: List<CopyJob>): XspfPlaylist = XspfPlaylist(
        name = playlistName,
        tracks = jobs.map { job ->
            XspfTrack(
                location = job.targetName,
                title = job.track.title.ifBlank { job.track.fileName.substringBeforeLast('.') },
            )
        },
    )

    fun buildRecords(
        jobs: List<CopyJob>,
        tagsByTrack: Map<Long, List<SidecarTag>>,
    ): List<SidecarTrackRecord> = jobs.map { job ->
        val track = job.track
        val tags = tagsByTrack[track.trackId].orEmpty()
        SidecarTrackRecord(
            path = job.targetName,
            title = track.title.takeIf { it.isNotEmpty() },
            artist = track.artist.takeIf { it.isNotEmpty() },
            durationMs = track.durationMs.takeIf { it > 0 },
            bpm = track.bpm,
            source = track.bpmSource,
            tags = tags.takeIf { it.isNotEmpty() },
        )
    }

    private fun uniqueFileName(fileName: String, taken: Set<String>): String {
        if (fileName !in taken) return fileName
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while ("$base ($index)$extension" in taken) index++
        return "$base ($index)$extension"
    }
}
