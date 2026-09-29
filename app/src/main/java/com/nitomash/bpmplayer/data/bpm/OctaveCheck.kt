package com.nitomash.bpmplayer.data.bpm

import kotlin.math.abs

object OctaveCheck {

    private const val RATIO = 2.0
    private const val TOLERANCE = 0.03

    fun isOctaveOf(detected: Double, reference: Double): Boolean {
        if (detected <= 0.0 || reference <= 0.0) return false
        val ratio = detected / reference
        val doubleGap = abs(ratio - RATIO)
        val halfGap = abs(ratio - 1.0 / RATIO)
        val doubleBound = RATIO * TOLERANCE
        val halfBound = (1.0 / RATIO) * TOLERANCE
        return doubleGap <= doubleBound || halfGap <= halfBound
    }
}