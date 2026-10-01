package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.playlist.Playlist
import com.nuvio.tv.core.playlist.PlaylistEntry
import com.nuvio.tv.core.playlist.PlaylistPlaybackSession
import com.nuvio.tv.core.playlist.PlaylistRef
import com.nuvio.tv.core.playlist.PlaylistSection
import com.nuvio.tv.core.playlist.PlaylistUpNext
import com.nuvio.tv.domain.model.ContinueWatchingSortMode
import com.nuvio.tv.domain.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlaylistContinueWatchingTest {

    private val tng1 = PlaylistEntry("tng-1-1", "series", "tng", 1, 1, title = "Encounter at Farpoint")
    private val ds9 = PlaylistEntry("ds9-1-1", "series", "ds9", 1, 1, show = "Deep Space Nine", title = "Emissary", thumbnail = "still.jpg")
    private val movie = PlaylistEntry("generations", "movie", "gen", null, null, title = "Generations", runtime = 118)
    private val tng2 = PlaylistEntry("tng-1-2", "series", "tng", 1, 2)
    private val playlist = Playlist(
        ref = PlaylistRef("https://lists.example/index.json", "trek"),
        name = "Trek", description = null, poster = null, background = null, logo = null, updatedAt = null,
        sections = listOf(PlaylistSection(null, listOf(tng1, ds9, movie, tng2)))
    )

    private fun overlay(index: Int, upNext: List<PlaylistUpNext>) = PlaylistContinueWatching.build(
        places = listOf(PlaylistPlaybackSession.Position(playlist, index, 0L)),
        upNext = upNext,
        sortMode = ContinueWatchingSortMode.DEFAULT
    )

    private fun nativeNextUp(id: String, season: Int, episode: Int, seedSeason: Int, seedEpisode: Int, at: Long) =
        ContinueWatchingItem.NextUp(
            NextUpInfo(
                contentId = id, contentType = "series", name = id, poster = null, backdrop = null, logo = null,
                videoId = "$id:$season:$episode", season = season, episode = episode, episodeTitle = null,
                thumbnail = null, lastWatched = at, sortTimestamp = at, seedSeason = seedSeason, seedEpisode = seedEpisode
            )
        )

    private fun inProgress(id: String, season: Int?, episode: Int?, at: Long) = ContinueWatchingItem.InProgress(
        WatchProgress(
            contentId = id, contentType = "series", name = id, poster = null, backdrop = null, logo = null,
            videoId = id, season = season, episode = episode, episodeTitle = null,
            position = 300L, duration = 1000L, lastWatched = at
        )
    )

    @Test
    fun `next entry appears as Next up in time order`() {
        val other = inProgress("other", null, null, at = 200L)
        val state = HomeUiState(continueWatchingItems = listOf(other))
        val result = overlay(0, listOf(PlaylistUpNext(playlist, tng1, ds9, timestamp = 300L))).applyTo(state)

        val info = (result.continueWatchingItems.first() as ContinueWatchingItem.NextUp).info
        assertEquals("ds9", info.contentId)
        assertEquals("Deep Space Nine", info.name)
        assertEquals("Emissary", info.episodeTitle)
        assertEquals("still.jpg", info.thumbnail)
        assertEquals(1 to 1, info.seedSeason to info.seedEpisode)
        assertSame(other, result.continueWatchingItems[1])
    }

    @Test
    fun `movie entry appears as an unstarted card`() {
        val result = overlay(1, listOf(PlaylistUpNext(playlist, ds9, movie, timestamp = 1L))).applyTo(HomeUiState())
        val progress = (result.continueWatchingItems.single() as ContinueWatchingItem.InProgress).progress
        assertEquals("gen", progress.contentId)
        assertEquals(0L, progress.position)
        assertEquals(118L * 60_000L, progress.duration)
    }

    @Test
    fun `show's own Next up is hidden while the playlist owns it`() {
        // TNG 1x2 is ahead of the place and was seeded from 1x1, which is behind it.
        val owned = nativeNextUp("tng", 1, 2, seedSeason = 1, seedEpisode = 1, at = 100L)
        // Seeded from an episode the playlist doesn't contain: someone watching TNG on its own.
        val independent = nativeNextUp("tng", 1, 2, seedSeason = 3, seedEpisode = 5, at = 100L)
        val unrelated = nativeNextUp("voy", 1, 2, seedSeason = 1, seedEpisode = 1, at = 100L)

        val overlay = overlay(1, emptyList())
        val result = overlay.applyTo(HomeUiState(continueWatchingItems = listOf(owned, unrelated)))
        assertEquals(listOf(unrelated), result.continueWatchingItems)

        val untouched = HomeUiState(continueWatchingItems = listOf(independent, unrelated))
        assertSame(untouched, overlay.applyTo(untouched))
    }

    @Test
    fun `title already in progress is not offered twice`() {
        val started = inProgress("ds9", 1, 1, at = 50L)
        val state = HomeUiState(continueWatchingItems = listOf(started))
        val result = overlay(0, listOf(PlaylistUpNext(playlist, tng1, ds9, timestamp = 300L))).applyTo(state)
        assertSame(state, result)
    }

    @Test
    fun `removed cards are traced back to their playlist`() {
        val overlay = overlay(0, listOf(
            PlaylistUpNext(playlist, tng1, ds9, timestamp = 1L),
            PlaylistUpNext(playlist, ds9, movie, timestamp = 1L)
        ))
        assertEquals(playlist.ref, overlay.playlistFor("ds9", 1, 1, isNextUp = true))
        assertEquals(playlist.ref, overlay.playlistFor("gen", null, null, isNextUp = false))
        assertNull(overlay.playlistFor("ds9", 1, 1, isNextUp = false))
        assertNull(overlay.playlistFor("tng", 1, 1, isNextUp = true))
    }
}
