package com.nitomash.bpmplayer.ui.common

import android.util.Log

internal object BpmLog {
    const val TAG = "BpmPlayer"

    fun d(message: String) {
        Log.d(TAG, message)
    }
}
