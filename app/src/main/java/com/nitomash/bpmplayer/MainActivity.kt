package com.nitomash.bpmplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.linkimport.ImportActions
import com.nitomash.bpmplayer.data.music.SidecarSyncManager
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.sharing.LinkHandling
import com.nitomash.bpmplayer.domain.sharing.CollectionShareLink
import com.nitomash.bpmplayer.ui.BpmPlayerApp
import com.nitomash.bpmplayer.ui.importlink.ImportNavigation
import com.nitomash.bpmplayer.ui.theme.BpmPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var showLinkHandlingHint by mutableStateOf(false)
    private var startupHintDismissedState by mutableStateOf(false)
    private var importHintDismissedState by mutableStateOf(false)

    private var startupHintDismissed: Boolean
        get() = startupHintDismissedState
        set(value) {
            startupHintDismissedState = value
            getSharedPreferences(LINK_HANDLING_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_STARTUP_HINT_DISMISSED, value)
                .apply()
        }

    private var importHintDismissed: Boolean
        get() = importHintDismissedState
        set(value) {
            importHintDismissedState = value
            getSharedPreferences(LINK_HANDLING_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IMPORT_HINT_DISMISSED, value)
                .apply()
        }

    @Inject
    lateinit var sidecarSync: SidecarSyncManager

    @Inject
    lateinit var playbackSession: PlaybackSession

    @Inject
    lateinit var importNavigation: ImportNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        showLinkHandlingHint = !LinkHandling.isAllowed(this)
        val prefs = getSharedPreferences(LINK_HANDLING_PREFS, MODE_PRIVATE)
        startupHintDismissedState = prefs.getBoolean(KEY_STARTUP_HINT_DISMISSED, false)
        importHintDismissedState = prefs.getBoolean(KEY_IMPORT_HINT_DISMISSED, false)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                playbackSession.stopPreviewOnFocusLoss()
            }

            override fun onStop(owner: LifecycleOwner) {
                playbackSession.stopPreviewOnFocusLoss()
                sidecarSync.flushAll()
                playbackSession.saveResumeState()
            }
        })
        setContent {
            BpmPlayerTheme {
                BpmPlayerApp(
                    importNavigation = importNavigation,
                    showLinkHandlingHint = showLinkHandlingHint,
                    startupHintDismissed = startupHintDismissed,
                    importHintDismissed = importHintDismissed,
                    onOpenLinkHandlingSettings = ::openLinkHandlingSettings,
                    onDismissStartupHint = { startupHintDismissed = true },
                    onDismissImportHint = { importHintDismissed = true },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        showLinkHandlingHint = !LinkHandling.isAllowed(this)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ImportActions.OPEN_IMPORT -> {
                importNavigation.openImport(intent.getStringExtra(ImportActions.EXTRA_JOB_ID))
            }

            Intent.ACTION_VIEW -> {
                val data = intent.dataString ?: return
                val links = runCatching { CollectionShareLink.parse(data) }
                    .onFailure { showInvalidLink() }
                    .getOrNull() ?: return
                importNavigation.openImport(null, links)
            }
        }
    }

    private fun showInvalidLink() {
        Toast.makeText(this, R.string.import_error_unsupported, Toast.LENGTH_LONG).show()
    }

    private fun openLinkHandlingSettings() {
        startActivity(LinkHandling.settingsIntent(this))
    }

    private companion object {
        const val LINK_HANDLING_PREFS = "link_handling"
        const val KEY_STARTUP_HINT_DISMISSED = "startup_hint_dismissed"
        const val KEY_IMPORT_HINT_DISMISSED = "import_hint_dismissed"
    }
}
