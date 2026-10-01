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
    )

    private fun movie(id: String) = PlaylistEntry(
        key = "movie:$id",
        type = PLAYLIST_ENTRY_TYPE_MOVIE,
        id = id,
        season = null,
        episode = null,
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
    fun `continues after the most recently watched entry, like a show`() {
        val watched = setOf(playlistWatchedKey("tt9", null, null))
        val lastWatched = { entry: PlaylistEntry -> if (entry.id == "tt9") 100L else 0L }
        // tt1 1x1 and 1x2 are unwatched gaps before tt9; the user carries on after tt9.
        assertEquals(entries[3], PlaylistSelection.continueTarget(entries, watched, lastWatched))
    }

    @Test
    fun `resumes the most recent entry while it is unfinished`() {
        val lastWatched = { entry: PlaylistEntry -> if (entry.id == "tt9") 100L else if (entry.episode == 1) 50L else 0L }
        val inProgress = { entry: PlaylistEntry -> entry.id == "tt9" }
        assertEquals(entries[2], PlaylistSelection.continueTarget(entries, emptySet(), lastWatched, inProgress))
    }

    @Test
    fun `past the most recent entry with nothing left after it, falls back to the first unwatched`() {
        val last = entries.last()
        val watched = setOf(last.watchedKey)
        val lastWatched = { entry: PlaylistEntry -> if (entry == last) 100L else 0L }
        assertEquals(entries[0], PlaylistSelection.continueTarget(entries, watched, lastWatched))
    }

    @Test
    fun `an empty playlist has no target`() {
        assertNull(PlaylistSelection.continueTarget(emptyList(), emptySet()))
    }

    @Test
    fun `locates episodes by real coordinates and movies by id`() {
        assertEquals(1, PlaylistSelection.locate(entries, 0, "tt1", 1, 2))
        assertEquals(2, PlaylistSelection.locate(entries, 0, "tt9", null, null))
        assertEquals(3, PlaylistSelection.locate(entries, 0, "tt2", 1, 1))
    }

    @Test
    fun `locate searches forward from the current position`() {
        val repeated = entries + episode("tt1", 1, 1)
        assertEquals(4, PlaylistSelection.locate(repeated, 1, "tt1", 1, 1))
        assertNull(PlaylistSelection.locate(entries, 2, "tt1", 1, 2))
    }

    @Test
    fun `locate ignores titles outside the playlist`() {
        assertNull(PlaylistSelection.locate(entries, 0, "tt1", 0, 2))
        assertNull(PlaylistSelection.locate(entries, 0, "tt5", null, null))
        assertNull(PlaylistSelection.locate(entries, 0, null, null, null))
    }
}
