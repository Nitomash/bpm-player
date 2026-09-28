package com.nitomash.bpmplayer.ui.editor

import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import kotlin.math.roundToInt

fun formatBpm(bpm: Double): String = bpm.roundToInt().toString()

fun parseBpm(text: String): Int? =
    text.trim().toIntOrNull()?.takeIf { it in 1..999 }

fun bpmSourceLabel(source: BpmSource): Int = when (source) {
    BpmSource.MANUAL -> R.string.bpm_source_manual
    BpmSource.TAP -> R.string.bpm_source_tap
    BpmSource.AUTO -> R.string.bpm_source_auto
}