package com.nuvio.tv.core.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFeedParserTest {

    private val indexUrl = "https://host.example:7443/playlists.json"

    @Test
    fun `parses an index and resolves relative urls against the index url`() {
        val json = """
            {"version":1,"unknownTopLevel":true,"playlists":[
              {"id":"star-trek","name":"Star Trek","description":"Chronological","image":"https://img/st.jpg",
               "entries":904,"movies":13,"episodes":891,"source":"chronolists","updatedAt":null,"stale":false,
               "url":"/playlists/star-trek.json","extra":{"a":1}},
              {"id":"mcu","name":"MCU","entries":571,"url":"playlists/mcu.json"},
              {"id":"abs","name":"Absolute","url":"https://other.example/abs.json"},
              {"name":"No id"},
              {"id":"no-name"}
            ]}
        """.trimIndent()

        val playlists = PlaylistFeedParser.parseIndex(json, indexUrl)

        assertNotNull(playlists)
        assertEquals(listOf("star-trek", "mcu", "abs"), playlists!!.map { it.id })
        val starTrek = playlists[0]
        assertEquals("Star Trek", starTrek.name)
        assertEquals(904, starTrek.entries)
        assertEquals(13, starTrek.movies)
        assertEquals(891, starTrek.episodes)
        assertNull(starTrek.updatedAt)
        assertFalse(starTrek.stale)
        assertEquals("https://host.example:7443/playlists/star-trek.json", starTrek.url)
        assertEquals("https://host.example:7443/playlists/mcu.json", playlists[1].url)
        assertEquals("https://other.example/abs.json", playlists[2].url)
    }

    @Test
    fun `index without a playlists array is rejected`() {
        assertNull(PlaylistFeedParser.parseIndex("""{"version":1}""", indexUrl))
        assertNull(PlaylistFeedParser.parseIndex("not json", indexUrl))
        assertNull(PlaylistFeedParser.parseIndex("[]", indexUrl))
    }

    @Test
    fun `parses a valid episode entry`() {
        val playlist = PlaylistFeedParser.parsePlaylist(
            """
            {"id":"star-trek","name":"Star Trek","description":"d","image":"i","entries":[
              {"key":"series:tt0244365:1:3","type":"series","id":"tt0244365","season":1,"episode":3,
               "show":"Star Trek: Enterprise","title":"Fight or Flight","image":"https://img/e.jpg"}
            ]}
            """.trimIndent()
        )!!

        val entry = playlist.entries.single()
        assertEquals("series:tt0244365:1:3", entry.key)
        assertEquals("tt0244365", entry.id)
        assertEquals(1, entry.season)
        assertEquals(3, entry.episode)
        assertEquals("Star Trek: Enterprise", entry.show)
        assertEquals("Fight or Flight", entry.title)
        assertEquals("https://img/e.jpg", entry.image)
        assertFalse(entry.isMovie)
        assertEquals("tt0244365:1:3", entry.videoId)
        assertEquals("tt0244365|1|3", entry.watchedKey)
    }

    @Test
    fun `parses a movie entry`() {
        val playlist = PlaylistFeedParser.parsePlaylist(
            """
            {"id":"p","name":"P","entries":[
              {"key":"movie:tt0079945","type":"movie","id":"tt0079945","title":"Star Trek: The Motion Picture"}
            ]}
            """.trimIndent()
        )!!

        val entry = playlist.entries.single()
        assertTrue(entry.isMovie)
        assertNull(entry.season)
        assertNull(entry.episode)
        assertNull(entry.show)
        assertEquals("tt0079945", entry.videoId)
        assertEquals("tt0079945|_|_", entry.watchedKey)
    }

    @Test
    fun `skips entries that cannot be played and keeps the rest in order`() {
        val playlist = PlaylistFeedParser.parsePlaylist(
            """
            {"id":"p","name":"P","entries":[
              {"key":"a","type":"movie","id":"tt0000001","title":"First"},
              {"key":"b","type":"movie","id":"tmdb:123","title":"Not imdb"},
              {"key":"c","type":"movie","title":"No id"},
              {"key":"d","type":"movie","id":"tt12x","title":"Bad imdb"},
              {"key":"e","type":"anime","id":"tt0000002","title":"Unknown type"},
              {"key":"f","type":"series","id":"tt0000003","title":"No season or episode"},
              {"key":"g","type":"series","id":"tt0000003","season":2,"title":"No episode"},
              "not an object",
              {"key":"h","type":"series","id":"tt0000003","season":0,"episode":1,"title":"Special","futureField":[1]},
              {"key":"a","type":"movie","id":"tt0000001","title":"Duplicate key"}
            ]}
            """.trimIndent()
        )!!

        assertEquals(listOf("a", "h"), playlist.entries.map { it.key })
        assertEquals("tt0000003:0:1", playlist.entries[1].videoId)
    }

    @Test
    fun `builds a key when the feed omits it`() {
        val playlist = PlaylistFeedParser.parsePlaylist(
            """
            {"id":"p","name":"P","entries":[
              {"type":"series","id":"tt0000003","season":1,"episode":2},
              {"type":"movie","id":"tt0000001"}
            ]}
            """.trimIndent()
        )!!

        assertEquals(listOf("series:tt0000003:1:2", "movie:tt0000001"), playlist.entries.map { it.key })
    }

    @Test
    fun `playlist without an entries array is rejected`() {
        assertNull(PlaylistFeedParser.parsePlaylist("""{"id":"p","name":"P"}"""))
        assertNull(PlaylistFeedParser.parsePlaylist("""{"name":"P","entries":[]}"""))
        assertNull(PlaylistFeedParser.parsePlaylist("garbage"))
    }
}
