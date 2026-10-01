package com.nuvio.tv.core.playlist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The playlist currently being played through, and the user's saved place in every playlist they
 * have played from. Playing an entry opens the real title, so the player recognises playlist
 * playback by matching what it plays against the active entry; anything played from elsewhere
 * does not match and keeps normal next-episode behaviour.
 *
 * Places are stored per profile, so after a restart (or from Continue Watching) playing a saved
 * entry, or the one after it, picks the playlist back up.
 */
@Singleton
class PlaylistPlaybackSession @Inject constructor(
    private val settings: PlaylistSettingsDataStore,
    private val repository: PlaylistRepository
) {

    data class Position(val playlist: Playlist, val index: Int, val updatedAt: Long = 0L) {
        val entry: PlaylistEntry get() = playlist.entries[index]
        val next: PlaylistEntry? get() = playlist.entries.getOrNull(index + 1)

        fun isSamePlace(other: Position?): Boolean =
            other != null && other.playlist.ref == playlist.ref && other.index == index
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Playlists already parsed, so saving a place doesn't re-read every file. */
    private val playlists = ConcurrentHashMap<PlaylistRef, Playlist>()

    private var active: Position? = null

    private val _places = MutableStateFlow<List<Position>>(emptyList())

    /** The saved place in each enabled playlist, most recently played first. */
    val places: StateFlow<List<Position>> = _places.asStateFlow()

    init {
        scope.launch {
            combine(settings.places, settings.sourceUrls, settings.disabledPlaylists) { saved, sources, disabled ->
                saved.filter { it.ref.sourceUrl in sources && it.ref.key !in disabled }
            }.distinctUntilChanged().collectLatest { saved ->
                _places.value = saved.sortedByDescending { it.updatedAt }.mapNotNull { place ->
                    val playlist = playlists[place.ref]
                        ?: repository.observePlaylist(place.ref).firstOrNull()?.also { playlists[place.ref] = it }
                        ?: return@mapNotNull null
                    val index = playlist.entries.indexOfFirst { it.key == place.entryKey }
                    if (index < 0) null else Position(playlist, index, place.updatedAt)
                }
            }
        }
    }

    fun start(playlist: Playlist, entry: PlaylistEntry) {
        val index = playlist.entries.indexOf(entry)
        if (index < 0) return
        playlists[playlist.ref] = playlist
        moveTo(Position(playlist, index))
    }

    /**
     * Where the title being played sits in a playlist, moving the session along when playback
     * has moved. Searches forward from the active entry, then the rest of the active playlist
     * (for a jump back from the player's episodes panel), then picks a saved playlist back up
     * when the title is its saved entry or the one after it. Null when nothing is playing from a
     * playlist.
     */
    fun locate(contentId: String?, season: Int?, episode: Int?): Position? {
        val found = synchronized(this) {
            active?.let { current ->
                val entries = current.playlist.entries
                (PlaylistSelection.locate(entries, current.index, contentId, season, episode)
                    ?: PlaylistSelection.locate(entries, 0, contentId, season, episode))
                    ?.let { Position(current.playlist, it) }
            } ?: _places.value.firstNotNullOfOrNull { place ->
                PlaylistSelection.locate(place.playlist.entries, place.index, contentId, season, episode)
                    ?.takeIf { it <= place.index + 1 }
                    ?.let { Position(place.playlist, it) }
            }
        } ?: return null
        moveTo(found)
        return found
    }

    /** Forgets the saved place in [ref], e.g. when its Continue Watching card is removed. */
    fun forget(ref: PlaylistRef) {
        synchronized(this) {
            if (active?.playlist?.ref == ref) active = null
        }
        scope.launch { settings.removePlace(ref) }
    }

    private fun moveTo(position: Position) {
        val changed = synchronized(this) {
            val previous = active
            active = position
            !position.isSamePlace(previous) || _places.value.none { position.isSamePlace(it) }
        }
        if (!changed) return
        scope.launch {
            settings.savePlace(position.playlist.ref, position.entry.key, System.currentTimeMillis())
        }
    }
}
