package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.dao.TagDao
import com.nitomash.bpmplayer.data.db.dao.TrackTagDao
import com.nitomash.bpmplayer.data.db.entity.TrackTagEntity
import javax.inject.Inject

data class TaggedTrackTags(
    val trackId: Long,
    val tags: List<SidecarTag>,
)

class TagBatchApplier @Inject constructor(
    private val tagDao: TagDao,
    private val trackTagDao: TrackTagDao,
) {

    suspend fun apply(folderId: Long, tagged: List<TaggedTrackTags>): Int {
        if (tagged.isEmpty()) return 0
        val existingByTrack = trackTagDao.getByFolder(folderId).groupBy { it.trackId }
        val plan = planTagPatches(
            tagged.map { patch ->
                TagPatchRequest(
                    trackId = patch.trackId,
                    existing = existingByTrack[patch.trackId].orEmpty()
                        .map { SidecarTag(it.name, it.rating) },
                    tags = patch.tags,
                )
            },
        )
        if (plan.isEmpty) return 0
        val tagIds = tagDao.resolveIds(plan.entries.map { it.name }.distinct())
        val rows = plan.entries.mapNotNull { entry ->
            tagIds[entry.name]?.let { id ->
                TrackTagEntity(trackId = entry.trackId, tagId = id, rating = entry.rating)
            }
        }
        trackTagDao.applyTagPatch(plan.clearTrackIds, rows)
        return rows.size
    }
}
