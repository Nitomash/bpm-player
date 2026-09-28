package com.nitomash.bpmplayer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorRangeFormTest {

    @Test
    fun `valid pair is accepted`() {
        val form = parseDetectorRangeForm("90", "150")

        assertEquals(90.0 to 150.0, form.bpmRange)
        assertFalse(form.minError)
        assertFalse(form.maxError)
        assertFalse(form.rangeError)
    }

    @Test
    fun `surrounding spaces are ignored`() {
        assertEquals(90.0 to 150.0, parseDetectorRangeForm(" 90 ", " 150 ").bpmRange)
    }

    @Test
    fun `empty field is an error`() {
        val form = parseDetectorRangeForm("", "150")

        assertTrue(form.minError)
        assertNull(form.bpmRange)
    }

    @Test
    fun `non numeric field is an error`() {
        assertTrue(parseDetectorRangeForm("abc", "150").minError)
        assertTrue(parseDetectorRangeForm("90", "1.5").maxError)
    }

    @Test
    fun `values below limit are errors`() {
        val form = parseDetectorRangeForm("10", "150")

        assertTrue(form.minError)
        assertNull(form.bpmRange)
    }

    @Test
    fun `values above limit are errors`() {
        val form = parseDetectorRangeForm("90", "400")

        assertTrue(form.maxError)
        assertNull(form.bpmRange)
    }

    @Test
    fun `equal bounds are a range error`() {
        val form = parseDetectorRangeForm("120", "120")

        assertFalse(form.minError)
        assertFalse(form.maxError)
        assertTrue(form.rangeError)
        assertNull(form.bpmRange)
    }

    @Test
    fun `inverted bounds are a range error`() {
        val form = parseDetectorRangeForm("150", "90")

        assertTrue(form.rangeError)
        assertNull(form.bpmRange)
    }

    @Test
    fun `limit bounds are accepted`() {
        assertEquals(20.0 to 300.0, parseDetectorRangeForm("20", "300").bpmRange)
    }
}
