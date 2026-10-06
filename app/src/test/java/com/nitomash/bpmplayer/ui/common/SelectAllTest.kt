package com.nitomash.bpmplayer.ui.common

import androidx.compose.ui.state.ToggleableState
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectAllTest {

    private val all = listOf(1L, 2L, 3L)

    @Test
    fun `state is off when nothing selected`() {
        assertEquals(ToggleableState.Off, SelectAll.state(all, emptySet()))
    }

    @Test
    fun `state is on when everything selected`() {
        assertEquals(ToggleableState.On, SelectAll.state(all, all.toSet()))
    }

    @Test
    fun `state is indeterminate on partial selection`() {
        assertEquals(ToggleableState.Indeterminate, SelectAll.state(all, setOf(1L, 3L)))
    }

    @Test
    fun `state is off for empty list`() {
        assertEquals(ToggleableState.Off, SelectAll.state(emptyList(), setOf(1L)))
    }

    @Test
    fun `state ignores ids missing from the list`() {
        assertEquals(ToggleableState.On, SelectAll.state(all, setOf(1L, 2L, 3L, 99L)))
    }

    @Test
    fun `toggled selects everything when not fully selected`() {
        assertEquals(setOf(1L, 2L, 3L), SelectAll.toggled(all, setOf(2L)))
    }

    @Test
    fun `toggled clears selection when fully selected`() {
        assertEquals(emptySet<Long>(), SelectAll.toggled(all, all.toSet()))
    }

    @Test
    fun `toggled keeps nothing for empty list`() {
        assertEquals(emptySet<Long>(), SelectAll.toggled(emptyList(), emptySet()))
    }
}