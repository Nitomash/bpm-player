package com.nitomash.bpmplayer.data.playlist.xspf

import org.junit.Assert.assertEquals
import org.junit.Test

class XspfLocationTest {

    private val internal = StoragePath.of("/storage/emulated/0", "")
    private val card = StoragePath.of("/storage/1A2B-3C4D", "")

    @Test
    fun `track in the export folder is a bare name`() {
        val base = StoragePath.of("/storage/emulated/0", "Music")

        assertEquals("a.mp3", XspfLocation.location(base, StoragePath.of("/storage/emulated/0", "Music/a.mp3")))
    }

    @Test
    fun `track in a subfolder keeps the subfolder`() {
        val base = StoragePath.of("/storage/emulated/0", "Music")

        assertEquals(
            "Sub/a.mp3",
            XspfLocation.location(base, StoragePath.of("/storage/emulated/0", "Music/Sub/a.mp3")),
        )
    }

    @Test
    fun `track in a sibling folder walks up`() {
        val base = StoragePath.of("/storage/emulated/0", "Playlists")

        assertEquals(
            "../Music/a.mp3",
            XspfLocation.location(base, StoragePath.of("/storage/emulated/0", "Music/a.mp3")),
        )
    }

    @Test
    fun `track in a top level folder walks up to the volume root`() {
        val base = StoragePath.of("/storage/emulated/0", "Music/Sub")

        assertEquals(
            "../../a.mp3",
            XspfLocation.location(base, StoragePath.of("/storage/emulated/0", "a.mp3")),
        )
    }

    @Test
    fun `track on another volume falls back to an absolute path`() {
        val base = StoragePath.of("/storage/emulated/0", "Playlists")
        val target = StoragePath.of("/storage/1A2B-3C4D", "Music/a.mp3")

        assertEquals("/storage/1A2B-3C4D/Music/a.mp3", XspfLocation.location(base, target))
    }

    @Test
    fun `track in the volume root of a card against an internal base is absolute`() {
        val base = StoragePath.of("/storage/emulated/0", "")

        assertEquals("/storage/1A2B-3C4D/a.mp3", XspfLocation.location(base, card.resolve("a.mp3")))
    }

    @Test
    fun `export into the volume root yields a relative path`() {
        assertEquals("Music/a.mp3", XspfLocation.location(internal, StoragePath.of("/storage/emulated/0", "Music/a.mp3")))
    }

    @Test
    fun `trailing slashes and doubled separators are normalized`() {
        val base = StoragePath.of("/storage/emulated/0/", "/Music/")

        assertEquals(
            "a.mp3",
            XspfLocation.location(base, StoragePath.of("//storage//emulated//0/", "Music//a.mp3")),
        )
    }

    @Test
    fun `spaces and cyrillic in names survive untouched`() {
        val base = StoragePath.of("/storage/emulated/0", "Мои плейлисты")

        assertEquals(
            "../Музыка/Трек №1.mp3",
            XspfLocation.location(base, StoragePath.of("/storage/emulated/0", "Музыка/Трек №1.mp3")),
        )
    }

    @Test
    fun `isRelative is true only within one volume`() {
        val base = StoragePath.of("/storage/emulated/0", "Music")

        assertEquals(true, XspfLocation.isRelative(base, StoragePath.of("/storage/emulated/0", "Music/a.mp3")))
        assertEquals(false, XspfLocation.isRelative(base, StoragePath.of("/storage/1A2B-3C4D", "Music/a.mp3")))
    }

    @Test
    fun `absolutePath renders the joined path`() {
        assertEquals(
            "/storage/emulated/0/Music/a.mp3",
            StoragePath.of("/storage/emulated/0", "Music/a.mp3").absolutePath,
        )
    }

    @Test
    fun `absolutePath of a volume root has no trailing slash`() {
        assertEquals("/storage/emulated/0", internal.absolutePath)
    }

    @Test
    fun `resolved marks a same volume location as relative`() {
        val base = StoragePath.of("/storage/emulated/0", "Playlists")
        val target = StoragePath.of("/storage/emulated/0", "Music/a.mp3")

        assertEquals(
            XspfLocation.Resolved("../Music/a.mp3", relative = true),
            XspfLocation.location(base, target, fallback = "ignored"),
        )
    }

    @Test
    fun `resolved marks a cross volume location as absolute`() {
        val base = StoragePath.of("/storage/emulated/0", "Playlists")
        val target = StoragePath.of("/storage/1A2B-3C4D", "Music/a.mp3")

        assertEquals(
            XspfLocation.Resolved("/storage/1A2B-3C4D/Music/a.mp3", relative = false),
            XspfLocation.location(base, target, fallback = "ignored"),
        )
    }

    @Test
    fun `resolved falls back to an absolute path when the export base is unknown`() {
        val target = StoragePath.of("/storage/emulated/0", "Music/a.mp3")

        assertEquals(
            XspfLocation.Resolved("/storage/emulated/0/Music/a.mp3", relative = false),
            XspfLocation.location(null, target, fallback = "ignored"),
        )
    }

    @Test
    fun `resolved falls back to the given path when the track folder is unknown`() {
        val base = StoragePath.of("/storage/emulated/0", "Playlists")

        assertEquals(
            XspfLocation.Resolved("Music/a.mp3", relative = false),
            XspfLocation.location(base, null, fallback = "Music/a.mp3"),
        )
    }
}