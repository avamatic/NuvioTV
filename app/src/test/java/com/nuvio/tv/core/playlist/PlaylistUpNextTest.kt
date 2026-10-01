package com.nuvio.tv.core.playlist

import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistUpNextTest {

    private val entries = listOf(
        PlaylistEntry("tng-1-1", "series", "tng", 1, 1),
        PlaylistEntry("ds9-1-1", "series", "ds9", 1, 1),
        PlaylistEntry("generations", "movie", "gen", null, null),
        PlaylistEntry("tng-1-2", "series", "tng", 1, 2)
    )
    private val playlist = Playlist(
        ref = PlaylistRef("https://lists.example/index.json", "trek"),
        name = "Trek", description = null, poster = null, background = null, logo = null, updatedAt = null,
        sections = listOf(PlaylistSection(null, entries))
    )

    private fun place(index: Int, at: Long = 10L) = PlaylistPlaybackSession.Position(playlist, index, at)

    private fun watched(id: String, season: Int?, episode: Int?, at: Long = 50L) =
        WatchedItem(contentId = id, contentType = "series", title = id, season = season, episode = episode, watchedAt = at)

    private fun progress(id: String, season: Int?, episode: Int?, percent: Float, at: Long = 60L) = WatchProgress(
        contentId = id, contentType = "series", name = id, poster = null, backdrop = null, logo = null,
        videoId = id, season = season, episode = episode, episodeTitle = null,
        position = (percent * 10).toLong(), duration = 1000L, lastWatched = at
    )

    @Test
    fun `finished place offers the next unwatched entry`() {
        val state = PlaylistWatchState.from(emptyList(), listOf(watched("tng", 1, 1), watched("gen", null, null)))
        val upNext = PlaylistUpNextResolver.resolve(listOf(place(0)), state).single()
        assertEquals("ds9-1-1", upNext.entry.key)
        assertEquals("tng-1-1", upNext.previous.key)
        assertEquals(50L, upNext.timestamp)

        val skipping = PlaylistUpNextResolver.resolve(listOf(place(1)), PlaylistWatchState.from(emptyList(), listOf(
            watched("ds9", 1, 1), watched("gen", null, null)
        ))).single()
        assertEquals("tng-1-2", skipping.entry.key)
    }

    @Test
    fun `unfinished place, started next entry and finished playlist offer nothing`() {
        val unfinished = PlaylistWatchState.from(listOf(progress("tng", 1, 1, 40f)), emptyList())
        assertTrue(PlaylistUpNextResolver.resolve(listOf(place(0)), unfinished).isEmpty())

        val nextStarted = PlaylistWatchState.from(listOf(progress("ds9", 1, 1, 30f)), listOf(watched("tng", 1, 1)))
        assertTrue(PlaylistUpNextResolver.resolve(listOf(place(0)), nextStarted).isEmpty())

        val finished = PlaylistWatchState.from(emptyList(), listOf(watched("tng", 1, 2)))
        assertTrue(PlaylistUpNextResolver.resolve(listOf(place(3)), finished).isEmpty())
    }

    @Test
    fun `completed progress counts as watched`() {
        val state = PlaylistWatchState.from(listOf(progress("tng", 1, 1, 95f, at = 70L)), emptyList())
        val upNext = PlaylistUpNextResolver.resolve(listOf(place(0, at = 5L)), state).single()
        assertEquals("ds9-1-1", upNext.entry.key)
        assertEquals(70L, upNext.timestamp)
    }
}
