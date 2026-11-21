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

    @TypeConverter
    fun stringListFromValue(value: String?): List<String> =
        value?.takeIf { it.isNotEmpty() }
            ?.split(LINK_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    @TypeConverter
    fun stringListToValue(value: List<String>?): String =
        value?.filter { it.isNotBlank() }?.distinct()?.joinToString(LINK_SEPARATOR).orEmpty()

    private companion object {
        const val LINK_SEPARATOR = "\u001F"
    }
}