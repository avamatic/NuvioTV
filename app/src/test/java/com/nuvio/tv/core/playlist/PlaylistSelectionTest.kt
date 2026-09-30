package com.nuvio.tv.core.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistSelectionTest {

    private fun episode(show: String, season: Int, episode: Int) = PlaylistEntry(
        key = "series:$show:$season:$episode",
        type = PLAYLIST_ENTRY_TYPE_SERIES,
        id = show,
        season = season,
        episode = episode,
        show = null,
        title = null,
        image = null
    )

    private fun movie(id: String) = PlaylistEntry(
        key = "movie:$id",
        type = PLAYLIST_ENTRY_TYPE_MOVIE,
        id = id,
        season = null,
        episode = null,
        show = null,
        title = null,
        image = null
    )

    private val entries = listOf(
        episode("tt1", 1, 1),
        episode("tt1", 1, 2),
        movie("tt9"),
        episode("tt2", 1, 1)
    )

    @Test
    fun `continues at the first entry when nothing is watched`() {
        assertEquals(entries[0], PlaylistSelection.continueTarget(entries, emptySet()))
    }

    @Test
    fun `continues at the first unwatched entry`() {
        val watched = setOf(playlistWatchedKey("tt1", 1, 1), playlistWatchedKey("tt1", 1, 2))
        assertEquals(entries[2], PlaylistSelection.continueTarget(entries, watched))
    }

    @Test
    fun `an unwatched gap wins over later watched entries`() {
        val watched = setOf(playlistWatchedKey("tt1", 1, 1), playlistWatchedKey("tt9", null, null))
        assertEquals(entries[1], PlaylistSelection.continueTarget(entries, watched))
    }

    @Test
    fun `watched state is matched on the real id season and episode`() {
        // Same show, different episode watched: must not count.
        val watched = setOf(playlistWatchedKey("tt1", 2, 1))
        assertEquals(entries[0], PlaylistSelection.continueTarget(entries, watched))
        assertEquals(0, PlaylistSelection.watchedCount(entries, watched))
    }

    @Test
    fun `a finished playlist restarts at the first entry`() {
        val watched = entries.map { it.watchedKey }.toSet()
        assertEquals(entries[0], PlaylistSelection.continueTarget(entries, watched))
        assertEquals(entries.size, PlaylistSelection.watchedCount(entries, watched))
    }

    @Test
    fun `an empty playlist has no target`() {
        assertNull(PlaylistSelection.continueTarget(emptyList(), emptySet()))
    }
}
