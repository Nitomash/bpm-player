package com.nitomash.bpmplayer

import android.app.Application
import android.content.ComponentCallbacks2
import com.nitomash.bpmplayer.data.player.PlaybackSession
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BpmPlayerApplication : Application() {
    @Inject
    lateinit var playbackSession: PlaybackSession

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            runCatching { playbackSession.saveResumeState() }
        }
    }
}