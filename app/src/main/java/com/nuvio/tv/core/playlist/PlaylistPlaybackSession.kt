package com.nuvio.tv.core.playlist

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The playlist currently being played through. Playing an entry opens the real title, so the
 * player recognises playlist playback by matching what it plays against the active entry;
 * anything played from elsewhere does not match and keeps normal next-episode behaviour.
 */
@Singleton
class PlaylistPlaybackSession @Inject constructor() {

    data class Position(val playlist: Playlist, val index: Int) {
        val entry: PlaylistEntry get() = playlist.entries[index]
        val next: PlaylistEntry? get() = playlist.entries.getOrNull(index + 1)
    }

    private var active: Position? = null

    @Synchronized
    fun start(playlist: Playlist, entry: PlaylistEntry) {
        val index = playlist.entries.indexOf(entry)
        active = if (index >= 0) Position(playlist, index) else null
    }

    /**
     * Where the title being played sits in the active playlist, moving the session forward when
     * playback has advanced. Null when nothing is playing from a playlist.
     */
    @Synchronized
    fun locate(contentId: String?, season: Int?, episode: Int?): Position? {
        val current = active ?: return null
        val index = PlaylistSelection.locate(current.playlist.entries, current.index, contentId, season, episode)
            ?: return null
        return Position(current.playlist, index).also { active = it }
    }
}
