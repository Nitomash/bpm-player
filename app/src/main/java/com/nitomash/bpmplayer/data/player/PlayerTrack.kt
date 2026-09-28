package com.nitomash.bpmplayer.data.player

import com.nitomash.bpmplayer.data.db.entity.TrackEntity

data class PlayerTrack(
    val trackId: Long,
    val uri: String,
    val title: String,
)

fun TrackEntity.toPlayerTrack(): PlayerTrack =
    PlayerTrack(trackId = id, uri = accessUri, title = title)