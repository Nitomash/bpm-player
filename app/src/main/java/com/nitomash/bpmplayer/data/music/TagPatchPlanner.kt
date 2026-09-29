package com.nitomash.bpmplayer.data.music

data class TagPatchRequest(
    val trackId: Long,
    val existing: List<SidecarTag>,
    val tags: List<SidecarTag>,
)

data class TagPatchEntry(
    val trackId: Long,
    val name: String,
    val rating: Int,
)

data class TagPatchPlan(
    val clearTrackIds: List<Long>,
    val entries: List<TagPatchEntry>,
) {
    val isEmpty: Boolean get() = clearTrackIds.isEmpty() && entries.isEmpty()
}

fun planTagPatches(requests: List<TagPatchRequest>): TagPatchPlan {
    val clearTrackIds = mutableListOf<Long>()
    val entries = mutableListOf<TagPatchEntry>()
    requests.forEach { request ->
        val next = normalizeTags(request.tags)
        if (next.toSet() == normalizeTags(request.existing).toSet()) return@forEach
        clearTrackIds += request.trackId
        next.forEach { tag -> entries += TagPatchEntry(request.trackId, tag.name, tag.rating) }
    }
    return TagPatchPlan(clearTrackIds, entries)
}

fun normalizeTags(tags: List<SidecarTag>): List<SidecarTag> =
    tags.filter { it.name.isNotBlank() && it.rating in 1..5 }.distinctBy { it.name }
