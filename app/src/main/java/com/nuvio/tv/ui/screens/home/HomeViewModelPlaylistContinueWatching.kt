package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.playlist.PlaylistUpNextResolver
import com.nuvio.tv.core.playlist.PlaylistWatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** Keeps [HomeViewModel.playlistContinueWatching] in step with saved playlist places and watch progress. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun HomeViewModel.observePlaylistContinueWatching() {
    viewModelScope.launch {
        playlistPlaybackSession.places
            .flatMapLatest { places ->
                if (places.isEmpty()) {
                    flowOf(PlaylistContinueWatching.EMPTY)
                } else {
                    combine(
                        watchProgressRepository.allProgress,
                        watchProgressRepository.watchedItems,
                        layoutPreferenceDataStore.continueWatchingSortMode
                    ) { progress, watched, sortMode ->
                        val state = PlaylistWatchState.from(progress, watched)
                        PlaylistContinueWatching.build(places, PlaylistUpNextResolver.resolve(places, state), sortMode)
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .collect { playlistContinueWatching.value = it }
    }
}
