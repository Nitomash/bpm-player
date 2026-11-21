package com.nitomash.bpmplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nitomash.bpmplayer.data.linkimport.ImportActions
import com.nitomash.bpmplayer.data.music.SidecarSyncManager
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.ui.BpmPlayerApp
import com.nitomash.bpmplayer.ui.importlink.ImportNavigation
import com.nitomash.bpmplayer.ui.theme.BpmPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                BpmPlayerApp(importNavigation)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ImportActions.OPEN_IMPORT) return
        importNavigation.openImport(intent.getStringExtra(ImportActions.EXTRA_JOB_ID))
    }
}