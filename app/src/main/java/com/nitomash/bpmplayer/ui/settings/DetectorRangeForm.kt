package com.nitomash.bpmplayer.ui.settings

import com.nitomash.bpmplayer.data.settings.AppSettings
import com.nitomash.bpmplayer.ui.editor.parseBpm

data class DetectorRangeForm(
    val minBpm: Int?,
    val maxBpm: Int?,
    val minError: Boolean,
    val maxError: Boolean,
    val rangeError: Boolean,
) {
    val bpmRange: Pair<Double, Double>?
        get() = if (minError || maxError || rangeError) null else minBpm!!.toDouble() to maxBpm!!.toDouble()
}

fun parseDetectorRangeForm(minText: String, maxText: String): DetectorRangeForm {
    val limitMin = AppSettings.BPM_LIMIT_MIN.toInt()
    val limitMax = AppSettings.BPM_LIMIT_MAX.toInt()
    val min = parseBpm(minText)
    val max = parseBpm(maxText)
    val minError = min == null || min !in limitMin..limitMax
    val maxError = max == null || max !in limitMin..limitMax
    return DetectorRangeForm(
        minBpm = min,
        maxBpm = max,
        minError = minError,
        maxError = maxError,
        rangeError = !minError && !maxError && min!! >= max!!,
    )
}
