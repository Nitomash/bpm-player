package com.nitomash.bpmplayer.data.playlist.export

import com.nitomash.bpmplayer.data.db.dao.FolderDao
import com.nitomash.bpmplayer.data.db.dao.PlaylistDao
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSort
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSorter
import javax.inject.Inject
import javax.inject.Singleton

data class ExportTrack(
    val trackId: Long,
    val title: String,
    val fileName: String,
    val relativePath: String,
    val folderRootUri: String,
    val accessUri: String,
    val artist: String,
    val durationMs: Long,
    val bpm: Double?,
    val bpmSource: BpmSource?,
)

data class ExportPlaylist(
    val playlistId: Long,
    val name: String,
    val tracks: List<ExportTrack>,
)

@Singleton
class PlaylistExportSource @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val folderDao: FolderDao,
) {

    suspend fun load(playlistIds: List<Long>): List<ExportPlaylist> {
        if (playlistIds.isEmpty()) return emptyList()
        val names = playlistDao.getNamesByIds(playlistIds).associate { it.id to it.name }
        val folderRoots = folderDao.getAll().associate { it.id to it.rootUri }
        return playlistIds.mapNotNull { playlistId ->
            val name = names[playlistId] ?: return@mapNotNull null
            ExportPlaylist(
                playlistId = playlistId,
                name = name,
                tracks = loadTracks(playlistId, folderRoots),
            )
        }
    }

    private suspend fun loadTracks(
        playlistId: Long,
        folderRoots: Map<Long, String>,
    ): List<ExportTrack> {
        val rows = playlistDao.getTrackRows(playlistId)
        val sort = playlistDao.getTrackSortMode(playlistId) ?: PlaylistTrackSort.CUSTOM
        return PlaylistTrackSorter.sort(rows, sort).mapNotNull { row ->
            val rootUri = folderRoots[row.track.folderId] ?: return@mapNotNull null
            ExportTrack(
                trackId = row.track.id,
                title = row.track.title,
                fileName = row.track.fileName,
                relativePath = row.track.relativePath,
                folderRootUri = rootUri,
                accessUri = row.track.accessUri,
                artist = row.track.artist,
                durationMs = row.track.durationMs,
                bpm = row.track.bpm,
                bpmSource = row.track.bpmSource,
            )
        }
    }
}