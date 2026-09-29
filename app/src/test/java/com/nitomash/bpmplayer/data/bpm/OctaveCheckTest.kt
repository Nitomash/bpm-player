package com.nitomash.bpmplayer.data.bpm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OctaveCheckTest {

    @Test
    fun `detected is double of reference`() {
        assertTrue(OctaveCheck.isOctaveOf(140.0, 70.0))
    }

    @Test
    fun `detected is half of reference`() {
        assertTrue(OctaveCheck.isOctaveOf(70.0, 140.0))
    }

    @Test
    fun `double within tolerance`() {
        assertTrue(OctaveCheck.isOctaveOf(140.8, 70.0))
        assertTrue(OctaveCheck.isOctaveOf(141.0, 70.0))
    }

    @Test
    fun `half within tolerance`() {
        assertTrue(OctaveCheck.isOctaveOf(69.0, 140.0))
    }

    @Test
    fun `double just outside tolerance is not octave`() {
        assertFalse(OctaveCheck.isOctaveOf(145.0, 70.0))
    }

    @Test
    fun `equal values are not octave`() {
        assertFalse(OctaveCheck.isOctaveOf(100.0, 100.0))
    }

    @Test
    fun `common resonance ratios are not octave`() {
        assertFalse(OctaveCheck.isOctaveOf(105.0, 70.0))
        assertFalse(OctaveCheck.isOctaveOf(93.0, 70.0))
        assertFalse(OctaveCheck.isOctaveOf(134.0, 100.0))
    }

    @Test
    fun `triple is not octave`() {
        assertFalse(OctaveCheck.isOctaveOf(210.0, 70.0))
    }

    @Test
    fun `non positive values are never octave`() {
        assertFalse(OctaveCheck.isOctaveOf(0.0, 70.0))
        assertFalse(OctaveCheck.isOctaveOf(140.0, 0.0))
    }
}