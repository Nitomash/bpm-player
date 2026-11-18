package com.nitomash.bpmplayer.data.player

object TempoAdjustment {
    const val MAX_BPM_OFFSET = 15.0
    const val MAX_PERCENT_OFFSET = 0.10

    fun normalizedPosition(value: Float): Float = value.coerceIn(-1f, 1f)

    fun speed(trackBpm: Double?, position: Float): Float {
        val normalized = normalizedPosition(position).toDouble()
        val speed = if (trackBpm != null && trackBpm > 0.0 && trackBpm.isFinite()) {
            (trackBpm + normalized * MAX_BPM_OFFSET) / trackBpm
        } else {
            1.0 + normalized * MAX_PERCENT_OFFSET
        }
        return speed.toFloat()
    }

    fun displayOffset(trackBpm: Double?, position: Float): TempoOffset =
        if (trackBpm != null && trackBpm > 0.0 && trackBpm.isFinite()) {
            TempoOffset.Bpm(normalizedPosition(position) * MAX_BPM_OFFSET.toFloat())
        } else {
            TempoOffset.Percent(normalizedPosition(position) * MAX_PERCENT_OFFSET.toFloat() * 100f)
        }
}

sealed interface TempoOffset {
    val value: Float

    data class Bpm(override val value: Float) : TempoOffset
    data class Percent(override val value: Float) : TempoOffset
}
