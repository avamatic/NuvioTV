package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.playlist.PLAYLIST_ENTRY_TYPE_MOVIE
import com.nuvio.tv.core.playlist.PlaylistEntry
import com.nuvio.tv.core.playlist.PlaylistWatchState
import com.nuvio.tv.domain.model.Video
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Fills in which playlist entries are watched, for the episodes panel's playlist mode. */
internal fun PlayerRuntimeController.loadPlaylistWatchedKeys() {
    scope.launch {
        val state = runCatching {
            PlaylistWatchState.from(watchProgressRepository.allProgress.first(), watchProgressRepository.watchedItems.first())
        }.getOrNull() ?: return@launch
        _uiState.update { it.copy(playlistWatchedKeys = state.watchedKeys) }
    }
}

/**
 * An entry picked in the episodes panel's playlist mode. Another episode of the show playing now
 * opens its streams in the panel and switches in place, as a normal episode pick does; any other
 * title is handed over to the Stream screen. The playlist session follows on its own once the
 * new title plays.
 */
internal fun PlayerRuntimeController.selectPlaylistEntry(entry: PlaylistEntry) {
    val isEpisode = !contentType.equals(PLAYLIST_ENTRY_TYPE_MOVIE, ignoreCase = true) &&
        currentSeason != null && currentEpisode != null
    if (entry.isEpisode && isEpisode && entry.id == contentId) {
        val video = metaVideos.firstOrNull { it.season == entry.season && it.episode == entry.episode }
            ?: Video(
                id = entry.videoId,
                title = entry.title ?: entry.show ?: entry.id,
                released = entry.released,
                thumbnail = entry.thumbnail,
                season = entry.season,
                episode = entry.episode,
                overview = entry.overview,
                runtime = entry.runtime
            )
        loadStreamsForEpisode(video)
        return
    }
    if (entry.key == _uiState.value.playlistPosition?.entry?.key) {
        dismissEpisodesPanel()
        return
    }
    _uiState.update { it.copy(showEpisodesPanel = false, playlistJumpTo = entry) }
}
