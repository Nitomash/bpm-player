package com.nitomash.bpmplayer.domain.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionShareLinkTest {

    private val yandex = "https://disk.yandex.ru/d/abc"
    private val second = "https://disk.yandex.com/d/def?utm=1"

    @Test
    fun `builds a share url with one link`() {
        val url = CollectionShareLink.build(listOf(yandex))

        assertEquals(
            "https://bpmplayer.nitomash.com/c/v1?ya=aHR0cHM6Ly9kaXNrLnlhbmRleC5ydS9kL2FiYw",
            url,
        )
        assertEquals(listOf(yandex), CollectionShareLink.parse(url))
    }

    @Test
    fun `builds and parses several links preserving order`() {
        val url = CollectionShareLink.build(listOf(yandex, second, "https://yadi.sk/d/xyz"))

        assertEquals(3, CollectionShareLink.parse(url).size)
        assertEquals(listOf(yandex, second, "https://yadi.sk/d/xyz"), CollectionShareLink.parse(url))
    }

    @Test
    fun `builds links with newly supported yandex domains`() {
        val link = "https://disk.360.yandex.ru/d/abc"

        val url = CollectionShareLink.build(listOf(link))

        assertTrue(url.contains("?ya="))
        assertEquals(listOf(link), CollectionShareLink.parse(url))
    }

    @Test
    fun `builds links without scheme`() {
        val link = "yadi.sk/i/abc"

        val url = CollectionShareLink.build(listOf(link))

        assertTrue(url.contains("?ya="))
        assertEquals(listOf(link), CollectionShareLink.parse(url))
    }

    @Test
    fun `uses provider specific keys`() {
        val url = CollectionShareLink.build(
            listOf(yandex, "https://drive.google.com/drive/folders/abc"),
        )

        assertTrue(url.contains("?ya="))
        assertTrue(url.contains("&gd="))
        assertEquals(2, CollectionShareLink.parse(url).size)
    }

    @Test
    fun `parses known keys in any order`() {
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(yandex.toByteArray(Charsets.UTF_8))

        assertEquals(
            listOf(yandex),
            CollectionShareLink.parse("https://bpmplayer.nitomash.com/c/v1?gd=$encoded"),
        )
    }

    @Test
    fun `rejects insecure scheme`() {
        val url = CollectionShareLink.build(listOf(yandex)).replace("https://", "http://")

        assertThrows(IllegalArgumentException::class.java) { CollectionShareLink.parse(url) }
    }

    @Test
    fun `rejects wrong host or path`() {
        val base = CollectionShareLink.build(listOf(yandex))
        val wrongHost = base.replace(CollectionShareLink.HOST, "evil.com")
        val wrongPath = base.replace(CollectionShareLink.PATH, "/c/v2")

        assertThrows(IllegalArgumentException::class.java) { CollectionShareLink.parse(wrongHost) }
        assertThrows(IllegalArgumentException::class.java) { CollectionShareLink.parse(wrongPath) }
    }

    @Test
    fun `rejects unknown keys and malformed parameters`() {
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(yandex.toByteArray(Charsets.UTF_8))
        val base = "https://bpmplayer.nitomash.com/c/v1"

        assertThrows(IllegalArgumentException::class.java) {
            CollectionShareLink.parse("$base?zz=$encoded")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionShareLink.parse("$base?ya")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionShareLink.parse("$base?ya=bad%2Bvalue")
        }
        assertThrows(IllegalArgumentException::class.java) { CollectionShareLink.parse(base) }
    }

    @Test
    fun `rejects oversized input`() {
        assertThrows(IllegalArgumentException::class.java) {
            CollectionShareLink.build(listOf("https://disk.yandex.ru/d/${"a".repeat(1_100)}"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionShareLink.build(List(33) { yandex })
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionShareLink.parse("https://bpmplayer.nitomash.com/c/v1?ya=${"A".repeat(1_100)}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionShareLink.parse("x".repeat(8_193))
        }
    }

    @Test
    fun `rejects empty build input`() {
        assertThrows(IllegalArgumentException::class.java) { CollectionShareLink.build(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CollectionShareLink.build(listOf("  ")) }
    }

    @Test
    fun `rejects decoded control characters`() {
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("https://disk.yandex.ru/d/abc\nhttps://evil.com".toByteArray(Charsets.UTF_8))

        assertThrows(IllegalArgumentException::class.java) {
            CollectionShareLink.parse("https://bpmplayer.nitomash.com/c/v1?ya=$encoded")
        }
    }
}
