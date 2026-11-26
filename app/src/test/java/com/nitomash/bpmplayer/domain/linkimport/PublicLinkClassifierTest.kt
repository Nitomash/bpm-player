package com.nitomash.bpmplayer.domain.linkimport

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicLinkClassifierTest {

    @Test
    fun `detects yandex disk public links across supported domains`() {
        val links = listOf(
            "https://disk.yandex.ru/d/abc",
            "https://DISK.YANDEX.COM/d/abc",
            "https://disk.yandex.net/d/abc",
            "https://disk.360.yandex.ru/d/abc",
            "https://disk.360.yandex.com/d/abc",
            "https://disk.yandex.kz/d/abc",
            "https://disk.360.yandex.uz/d/abc",
            "https://disk.yandex.com.tr/d/abc",
            "https://disk.360.yandex.by/d/abc",
            "https://m.disk.yandex.ru/d/abc",
            "https://www.disk.yandex.ru/i/abc",
            "https://yadi.sk/d/abc",
            "yadi.sk/i/abc",
            "https://disk.yandex.ru/d/abc/track.mp3",
            "https://disk.yandex.com/d/def?utm=1",
        )

        assertEquals(List(links.size) { PublicLinkSource.YANDEX_DISK }, links.map(PublicLinkClassifier::detect))
    }

    @Test
    fun `detects google drive links`() {
        assertEquals(
            PublicLinkSource.GOOGLE_DRIVE,
            PublicLinkClassifier.detect("https://drive.google.com/drive/folders/abc"),
        )
        assertEquals(
            PublicLinkSource.GOOGLE_DRIVE,
            PublicLinkClassifier.detect("https://docs.google.com/document/d/abc"),
        )
    }

    @Test
    fun `rejects unrelated hosts paths and schemes`() {
        val links = listOf(
            "https://mail.yandex.ru/d/abc",
            "https://disk.yandex.ru/client/disk",
            "https://yadi.sk/",
            "https://drive.google.com.evil.com/d/abc",
            "ftp://disk.yandex.ru/d/abc",
            "not a url",
            "",
        )

        assertEquals(List(links.size) { PublicLinkSource.UNKNOWN }, links.map(PublicLinkClassifier::detect))
    }

    @Test
    fun `normalizes links without scheme`() {
        assertEquals("https://yadi.sk/d/abc", PublicLinkClassifier.normalize(" yadi.sk/d/abc "))
        assertEquals("https://disk.yandex.ru/d/abc", PublicLinkClassifier.normalize("https://disk.yandex.ru/d/abc"))
    }
}
