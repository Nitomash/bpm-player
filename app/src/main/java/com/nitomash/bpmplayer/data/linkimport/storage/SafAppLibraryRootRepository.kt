package com.nitomash.bpmplayer.data.linkimport.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nitomash.bpmplayer.data.music.FolderScanner
import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRoot
import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRootRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafAppLibraryRootRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : AppLibraryRootRepository {

    override suspend fun getRoot(): AppLibraryRoot? = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val uri = prefs[KEY_URI] ?: return@map null
            AppLibraryRoot(uri, prefs[KEY_NAME] ?: FolderScanner.displayName(Uri.parse(uri)))
        }
        .first()

    override suspend fun saveRoot(uri: String, displayName: String): AppLibraryRoot {
        val parsed = Uri.parse(uri)
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                parsed,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val root = AppLibraryRoot(parsed.toString(), displayName.ifBlank { FolderScanner.displayName(parsed) })
        dataStore.edit { prefs ->
            prefs[KEY_URI] = root.uri
            prefs[KEY_NAME] = root.displayName
        }
        return root
    }

    override suspend fun clearRoot() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_URI)
            prefs.remove(KEY_NAME)
        }
    }

    private companion object {
        val KEY_URI = stringPreferencesKey("import_root_uri")
        val KEY_NAME = stringPreferencesKey("import_root_name")
    }
}
