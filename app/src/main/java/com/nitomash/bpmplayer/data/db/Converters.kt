package com.nitomash.bpmplayer.data.db

import androidx.room.TypeConverter
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSort

class Converters {

    @TypeConverter
    fun bpmSourceFromValue(value: Int?): BpmSource? = value?.let(BpmSource::fromValue)

    @TypeConverter
    fun bpmSourceToValue(source: BpmSource?): Int? = source?.value

    @TypeConverter
    fun playlistTrackSortFromValue(value: Int?): PlaylistTrackSort? = value?.let(PlaylistTrackSort::fromValue)

    @TypeConverter
    fun playlistTrackSortToValue(sort: PlaylistTrackSort?): Int? = sort?.value
}