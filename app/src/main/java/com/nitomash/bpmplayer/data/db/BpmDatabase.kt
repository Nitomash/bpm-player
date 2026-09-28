package com.nitomash.bpmplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nitomash.bpmplayer.data.db.dao.FolderDao
import com.nitomash.bpmplayer.data.db.dao.PlaylistDao
import com.nitomash.bpmplayer.data.db.dao.TagDao
import com.nitomash.bpmplayer.data.db.dao.TrackDao
import com.nitomash.bpmplayer.data.db.dao.TrackTagDao
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.ConditionEntity
import com.nitomash.bpmplayer.data.db.entity.ConditionTagEntity
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.PlaylistEntity
import com.nitomash.bpmplayer.data.db.entity.PlaylistOrderEntity
import com.nitomash.bpmplayer.data.db.entity.PlaylistTrackEntity
import com.nitomash.bpmplayer.data.db.entity.TagEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.db.entity.TrackTagEntity

@Database(
    entities = [
        FolderEntity::class,
        TrackEntity::class,
        TagEntity::class,
        TrackTagEntity::class,
        ConditionEntity::class,
        ConditionTagEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlaylistOrderEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BpmDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun trackDao(): TrackDao
    abstract fun tagDao(): TagDao
    abstract fun trackTagDao(): TrackTagDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN bpm_source INTEGER")
                db.execSQL("UPDATE tracks SET bpm_source = ${BpmSource.TAP.value} WHERE bpm IS NOT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE playlists ADD COLUMN track_sort_mode INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE playlist_tracks ADD COLUMN added_at_ms INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "UPDATE playlist_tracks SET added_at_ms = COALESCE(" +
                        "(SELECT p.created_at_ms FROM playlists p WHERE p.id = playlist_tracks.playlist_id), 0)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playlist_order (" +
                        "playlist_id INTEGER NOT NULL, " +
                        "position INTEGER NOT NULL, " +
                        "PRIMARY KEY(playlist_id), " +
                        "FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO playlist_order (playlist_id, position) " +
                        "SELECT p.id, (SELECT COUNT(*) FROM playlists p2 " +
                        "WHERE p2.created_at_ms > p.created_at_ms " +
                        "OR (p2.created_at_ms = p.created_at_ms AND p2.id > p.id)) " +
                        "FROM playlists p",
                )
            }
        }
    }
}