package com.nuvio.tv.core.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFormatParserTest {

    private val source = "https://lists.example/watch/index.json"

    private val playlistJson = """
        {
          "format": "playlist", "version": 1, "id": "trek", "name": "Star Trek",
          "description": "In order.", "background": "art/bg.jpg", "x-extra": {"ignored": true},
          "sections": [
            { "title": "22nd century", "entries": [
              { "key": "ent-1-1", "type": "series", "id": "tt0244365", "season": 1, "episode": 1,
                "show": "Star Trek: Enterprise", "title": "Broken Bow", "thumbnail": "https://img/1.jpg",
                "overview": "Maiden voyage.", "runtime": 86, "released": "2001-09-26" },
              { "key": "tmp", "type": "movie", "id": "tt0079945", "title": "The Motion Picture" }
            ]},
            { "entries": [
              { "key": "kitsu", "type": "anime", "id": "kitsu:1", "season": 1, "episode": 3, "videoId": "kitsu:1:3" },
              { "key": "tmp", "type": "movie", "id": "tt0079945" },
              { "key": "half", "type": "series", "id": "tt1", "season": 1 },
              { "key": "whole-show", "type": "series", "id": "tt2" },
              { "type": "movie", "id": "tt3" },
              "not an object"
            ]},
            { "title": "Nothing playable", "entries": [ { "key": "x" } ] }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses sections, entries and display hints`() {
        val playlist = PlaylistFormatParser.parsePlaylist(playlistJson, PlaylistRef(source, "trek"))!!
        assertEquals("Star Trek", playlist.name)
        assertEquals("https://lists.example/watch/art/bg.jpg", playlist.background)
        assertEquals(listOf("22nd century", null), playlist.sections.map { it.title })
        assertEquals(listOf("ent-1-1", "tmp", "kitsu"), playlist.entries.map { it.key })

        val first = playlist.entries[0]
        assertEquals("tt0244365:1:1", first.videoId)
        assertEquals("Star Trek: Enterprise", first.show)
        assertEquals("https://img/1.jpg", first.thumbnail)
        assertEquals(86, first.runtime)
        assertTrue(first.isEpisode)

        val movie = playlist.entries[1]
        assertTrue(movie.isMovie)
        assertFalse(movie.isEpisode)
        assertEquals("tt0079945", movie.videoId)
    }

    @Test
    fun `accepts any addon type and explicit video ids`() {
        val anime = PlaylistFormatParser.parsePlaylist(playlistJson, PlaylistRef(source, "trek"))!!.entries[2]
        assertEquals("anime", anime.type)
        assertEquals("kitsu:1", anime.id)
        assertEquals("kitsu:1:3", anime.videoId)
    }

    @Test
    fun `skips and counts unusable entries and duplicate keys, and drops empty sections`() {
        val playlist = PlaylistFormatParser.parsePlaylist(playlistJson, PlaylistRef(source, "trek"))!!
        // Duplicate "tmp", half an episode, a whole series, a missing key, a non-object, and "x".
        assertEquals(6, playlist.skippedEntries)
        assertEquals(2, playlist.sections.size)
    }

    @Test
    fun `newer playlist versions are kept by name but not parsed`() {
        val json = """{ "format": "playlist", "version": 2, "id": "p", "name": "Future", "sections": "unknown" }"""
        val playlist = PlaylistFormatParser.parsePlaylist(json, PlaylistRef(source, "p"))!!
        assertFalse(playlist.supported)
        assertEquals("Future", playlist.name)
        assertTrue(playlist.sections.isEmpty())
    }

    @Test
    fun `rejects documents that are not playlists`() {
        assertNull(PlaylistFormatParser.parsePlaylist("""{ "id": "p", "name": "No format", "sections": [] }""", PlaylistRef(source, "p")))
        assertNull(PlaylistFormatParser.parsePlaylist("not json", PlaylistRef(source, "p")))
        assertNull(PlaylistFormatParser.parseIndex("""{ "format": "playlist", "version": 1 }""", source))
    }

    @Test
    fun `parses an index with linked and inline playlists`() {
        val json = """
            {
              "format": "playlist-index", "version": 1, "name": "Watch orders",
              "playlists": [
                { "id": "trek", "name": "Star Trek", "url": "star-trek.json", "entries": 904, "poster": "/p.jpg" },
                { "id": "inline", "name": "Inline", "playlist": $playlistJson },
                { "id": "trek", "name": "Duplicate id" },
                { "id": "nowhere", "name": "No url or playlist" },
                { "name": "No id", "url": "x.json" }
              ]
            }
        """.trimIndent()
        val index = PlaylistFormatParser.parseIndex(json, source)!!
        assertEquals("Watch orders", index.name)
        assertEquals(listOf("trek", "inline"), index.playlists.map { it.ref.id })

        val linked = index.playlists[0]
        assertEquals(PlaylistRef(source, "trek"), linked.ref)
        assertEquals("https://lists.example/watch/star-trek.json", linked.url)
        assertEquals("https://lists.example/p.jpg", linked.poster)
        assertEquals(904, linked.entryCount)
        assertNull(linked.inline)

        val inline = index.playlists[1]
        assertNull(inline.url)
        assertNotNull(inline.inline)
        assertEquals(PlaylistRef(source, "inline"), inline.inline!!.ref)
        assertEquals(3, inline.entryCount)
    }

    @Test
    fun `newer index versions list playlists as unsupported`() {
        val json = """{ "format": "playlist-index", "version": 2, "playlists": [ { "id": "a", "name": "A", "url": "a.json" }, { "id": "b", "name": "B" } ] }"""
        val index = PlaylistFormatParser.parseIndex(json, source)!!
        assertEquals(listOf("a", "b"), index.playlists.map { it.ref.id })
        assertTrue(index.playlists.none { it.supported })
    }

    @Test
    fun `playlist refs round-trip through their key`() {
        val ref = PlaylistRef("https://lists.example/a@b/index.json", "id with @ and spaces")
        assertEquals(ref, PlaylistRef.fromKey(ref.key))
        assertNull(PlaylistRef.fromKey("no-separator"))
    }
}
