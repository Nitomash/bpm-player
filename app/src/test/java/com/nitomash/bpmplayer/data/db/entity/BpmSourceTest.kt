package com.nitomash.bpmplayer.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BpmSourceTest {

    @Test
    fun `every source resolves from its value`() {
        for (source in BpmSource.entries) {
            assertEquals(source, BpmSource.fromValue(source.value))
        }
    }

    @Test
    fun `unknown value resolves to null`() {
        assertNull(BpmSource.fromValue(-1))
        assertNull(BpmSource.fromValue(3))
    }
}