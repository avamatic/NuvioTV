package com.nuvio.tv.core.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistConfigurationTest {
    private val a = "https://example.com/a/index.json"
    private val b = "https://example.com/b/index.json"
    private val c = "https://example.com/c/index.json"
    private val off = DisabledPlaylist(a, "star-trek")

    @Test fun firstSyncImportsLocalSourcesWithoutDiscardingRemoteSources() {
        val merged = mergePlaylistConfiguration(null,
            PlaylistConfiguration(sources = listOf(a), disabledPlaylists = listOf(off)),
            PlaylistConfiguration(sources = listOf(b)))
        assertEquals(listOf(b, a), merged.sources)
        assertEquals(listOf(off), merged.disabledPlaylists)
    }

    @Test fun unchangedLocalAcceptsRemoteRemovalAndToggle() {
        val base = PlaylistConfiguration(sources = listOf(a, b))
        val remote = PlaylistConfiguration(sources = listOf(a), disabledPlaylists = listOf(off))
        assertEquals(remote, mergePlaylistConfiguration(base, base, remote))
    }

    @Test fun localSourceRemovalDoesNotDiscardIndependentRemoteAddition() {
        val base = PlaylistConfiguration(sources = listOf(a, b), disabledPlaylists = listOf(off))
        val merged = mergePlaylistConfiguration(base, PlaylistConfiguration(sources = listOf(b)),
            base.copy(sources = listOf(a, b, c)))
        assertEquals(listOf(b, c), merged.sources)
        assertEquals(emptyList<DisabledPlaylist>(), merged.disabledPlaylists)
    }

    @Test fun localToggleAndRemoteSourceRemovalMergeIndependently() {
        val base = PlaylistConfiguration(sources = listOf(a, b), disabledPlaylists = listOf(off))
        val merged = mergePlaylistConfiguration(base, base.copy(disabledPlaylists = emptyList()),
            base.copy(sources = listOf(a)))
        assertEquals(PlaylistConfiguration(sources = listOf(a)), merged)
    }

    @Test fun localReorderingKeepsRemoteNewSources() {
        val base = PlaylistConfiguration(sources = listOf(a, b))
        assertEquals(listOf(b, a, c), mergePlaylistConfiguration(base,
            base.copy(sources = listOf(b, a)), base.copy(sources = listOf(a, b, c))).sources)
    }

    @Test fun emptyRemoteConfigurationIsAnExplicitDeletion() {
        val base = PlaylistConfiguration(sources = listOf(a), disabledPlaylists = listOf(off))
        assertEquals(PlaylistConfiguration(), mergePlaylistConfiguration(base, base, PlaylistConfiguration()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownVersionIsNeverMergedOrReplaced() {
        mergePlaylistConfiguration(null, PlaylistConfiguration(), PlaylistConfiguration(version = 2))
    }
}
