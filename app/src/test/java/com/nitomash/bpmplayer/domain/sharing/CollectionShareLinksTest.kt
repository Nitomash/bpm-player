package com.nitomash.bpmplayer.domain.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionShareLinksTest {

    @Test
    fun `round trips several links`() {
        val links = listOf(
            "https://disk.yandex.ru/d/abc",
            "https://disk.yandex.com/d/def?utm=1",
            "https://yadi.sk/d/xyz",
        )

        val encoded = CollectionShareLinks.encode(links)

        assertTrue(encoded.all { it.isLetterOrDigit() || it in listOf('-', '_', '.') })
        assertEquals(links, CollectionShareLinks.decode(encoded))
    }

    @Test
    fun `handles null and empty input`() {
        assertTrue(CollectionShareLinks.decode(null).isEmpty())
        assertTrue(CollectionShareLinks.decode("").isEmpty())
        assertTrue(CollectionShareLinks.decode("%").isEmpty())
    }
}
