package com.nuvio.tv.ui.screens.playlist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.playlist.Playlist
import com.nuvio.tv.core.playlist.PlaylistEntry
import com.nuvio.tv.core.playlist.PlaylistPlaybackSession
import com.nuvio.tv.core.playlist.PlaylistRepository
import com.nuvio.tv.core.playlist.PlaylistSelection
import com.nuvio.tv.core.playlist.playlistWatchedKey
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PlaylistUiState(
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val playlist: Playlist? = null,
    /** Entries (by [PlaylistEntry.watchedKey]) whose real title is marked watched. */
    val watchedKeys: Set<String> = emptySet(),
    val watchedCount: Int = 0,
    val movieCount: Int = 0,
    val episodeCount: Int = 0,
    /** Index in [Playlist.entries] that "Continue" opens, or -1 when the playlist is empty. */
    val continueIndex: Int = -1
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val playbackSession: PlaylistPlaybackSession,
    watchProgressRepository: WatchProgressRepository
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>("playlistId").orEmpty()

    private data class Load(val isLoading: Boolean, val playlist: Playlist?)

    private val load = MutableStateFlow(Load(isLoading = true, playlist = null))

    /** Watched state as the rest of the app sees it, keyed like [PlaylistEntry.watchedKey]. */
    private val watchedKeys: StateFlow<Set<String>> =
        combine(watchProgressRepository.watchedItems, watchProgressRepository.allProgress) { items, progress ->
            val keys = HashSet<String>(items.size + progress.size)
            items.forEach { keys.add(playlistWatchedKey(it.contentId, it.season, it.episode)) }
            progress.forEach {
                if (it.isCompleted()) keys.add(playlistWatchedKey(it.contentId, it.season, it.episode))
            }
            keys.toSet()
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val uiState: StateFlow<PlaylistUiState> =
        combine(load, watchedKeys) { state, watched ->
            val playlist = state.playlist
            if (playlist == null) {
                PlaylistUiState(isLoading = state.isLoading, failed = !state.isLoading)
            } else {
                val entries = playlist.entries
                val target = PlaylistSelection.continueTarget(entries, watched)
                PlaylistUiState(
                    isLoading = false,
                    playlist = playlist,
                    watchedKeys = watched,
                    watchedCount = PlaylistSelection.watchedCount(entries, watched),
                    movieCount = entries.count { it.isMovie },
                    episodeCount = entries.count { !it.isMovie },
                    continueIndex = target?.let { entries.indexOf(it) } ?: -1
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState())

    /** Makes [entry] the active playlist position so the player continues through the playlist. */
    fun startPlayback(entry: PlaylistEntry) {
        load.value.playlist?.let { playbackSession.start(it, entry) }
    }

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylist(playlistId)
                .collect { playlist -> load.value = Load(isLoading = true, playlist = playlist) }
            load.value = Load(isLoading = false, playlist = load.value.playlist)
        }
    }
}
