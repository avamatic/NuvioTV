package com.nuvio.tv.ui.screens.playlist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.playlist.Playlist
import com.nuvio.tv.core.playlist.PlaylistEntry
import com.nuvio.tv.core.playlist.PlaylistPlaybackSession
import com.nuvio.tv.core.playlist.PlaylistRef
import com.nuvio.tv.core.playlist.PlaylistRepository
import com.nuvio.tv.core.playlist.PlaylistSelection
import com.nuvio.tv.core.playlist.PlaylistWatchState
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A section shown as a season: [number] is its 1-based display season, and [videos] are its entries
 * as episode cards whose season/episode are display coordinates (section, position), not the real
 * ones. [PlaylistUiState.entryFor] maps a card back to its entry.
 */
@Immutable
data class PlaylistSectionUi(
    val number: Int,
    val title: String?,
    val videos: List<Video>
)

@Immutable
data class PlaylistUiState(
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val playlist: Playlist? = null,
    val sections: List<PlaylistSectionUi> = emptyList(),
    val selectedSection: Int = 1,
    /** Real progress of each entry, keyed by display coordinates. */
    val progress: Map<Pair<Int, Int>, WatchProgress> = emptyMap(),
    /** Display coordinates of entries whose real title is watched. */
    val watched: Set<Pair<Int, Int>> = emptySet(),
    /** Keys of entries whose real title is watched. */
    val watchedEntryKeys: Set<String> = emptySet(),
    val watchedCount: Int = 0,
    val movieCount: Int = 0,
    val episodeCount: Int = 0,
    val totalRuntimeMinutes: Int = 0,
    /** Entry the primary button plays, and whether it has a saved position. */
    val continueEntry: PlaylistEntry? = null,
    val continueIsResume: Boolean = false,
    /** Card id of [continueEntry], for scrolling the row to it. */
    val continueVideoId: String? = null,
    private val entriesByVideoId: Map<String, PlaylistEntry> = emptyMap()
) {
    fun entryFor(video: Video): PlaylistEntry? = entriesByVideoId[video.id]
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val playbackSession: PlaylistPlaybackSession,
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {

    private val playlistRef: PlaylistRef? = savedStateHandle.get<String>("playlistKey")?.let(PlaylistRef::fromKey)

    private data class Load(val isLoading: Boolean, val playlist: Playlist?)

    private val load = MutableStateFlow(Load(isLoading = true, playlist = null))

    /** Section the user picked; null follows the entry "Continue" would play. */
    private val pickedSection = MutableStateFlow<Int?>(null)

    private val watchState = watchProgressRepository.allProgress
        .combine(watchProgressRepository.watchedItems, PlaylistWatchState::from)

    val uiState: StateFlow<PlaylistUiState> =
        combine(load, watchState, pickedSection) { state, watch, picked ->
            val playlist = state.playlist
                ?: return@combine PlaylistUiState(isLoading = state.isLoading, failed = !state.isLoading)
            buildState(playlist, watch, picked)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState())

    private fun buildState(playlist: Playlist, watch: PlaylistWatchState, picked: Int?): PlaylistUiState {
        val latest = watch.latest
        val watchedKeys = watch.watchedKeys

        val entriesByVideoId = HashMap<String, PlaylistEntry>()
        val coordinates = HashMap<String, Pair<Int, Int>>()
        val progress = HashMap<Pair<Int, Int>, WatchProgress>()
        val watched = HashSet<Pair<Int, Int>>()
        val watchedEntryKeys = HashSet<String>()
        val sections = playlist.sections.mapIndexed { sectionIndex, section ->
            val number = sectionIndex + 1
            PlaylistSectionUi(
                number = number,
                title = section.title,
                videos = section.entries.mapIndexed { entryIndex, entry ->
                    val coordinate = number to entryIndex + 1
                    val video = entry.toCard(coordinate)
                    entriesByVideoId[video.id] = entry
                    coordinates[entry.key] = coordinate
                    latest[entry.watchedKey]?.let { progress[coordinate] = it }
                    if (entry.watchedKey in watchedKeys) {
                        watched.add(coordinate)
                        watchedEntryKeys.add(entry.key)
                    }
                    video
                }
            )
        }

        val entries = playlist.entries
        val continueEntry = PlaylistSelection.continueTarget(entries, watchedKeys)
        val continueCoordinate = continueEntry?.let { coordinates[it.key] }
        val selected = picked?.takeIf { it in 1..sections.size } ?: continueCoordinate?.first ?: 1
        return PlaylistUiState(
            isLoading = false,
            playlist = playlist,
            sections = sections,
            selectedSection = selected,
            progress = progress,
            watched = watched,
            watchedEntryKeys = watchedEntryKeys,
            watchedCount = PlaylistSelection.watchedCount(entries, watchedKeys),
            movieCount = entries.count { it.isMovie },
            episodeCount = entries.count { it.isEpisode },
            totalRuntimeMinutes = entries.sumOf { it.runtime ?: 0 },
            continueEntry = continueEntry,
            continueIsResume = continueCoordinate?.let { progress[it]?.isInProgress() } == true,
            continueVideoId = continueEntry?.let { cardId(it) },
            entriesByVideoId = entriesByVideoId
        )
    }

    fun selectSection(number: Int) {
        pickedSection.value = number
    }

    /** Makes [entry] the active playlist position so the player continues through the playlist. */
    fun startPlayback(entry: PlaylistEntry) {
        load.value.playlist?.let { playbackSession.start(it, entry) }
    }

    fun setWatched(entry: PlaylistEntry, watched: Boolean) {
        viewModelScope.launch {
            if (watched) {
                watchProgressRepository.markAsCompleted(completedProgress(entry))
            } else {
                watchProgressRepository.removeFromHistory(entry.id, videoId = entry.videoId, season = entry.season, episode = entry.episode)
            }
        }
    }

    /** Marks every unwatched entry before [entry] as watched, for joining a playlist midway. */
    fun markPreviousWatched(entry: PlaylistEntry) {
        val state = uiState.value
        val entries = state.playlist?.entries ?: return
        val before = entries.takeWhile { it.key != entry.key }
        markWatched(before.filterNot { isWatched(state, it) })
    }

    fun setSectionWatched(number: Int, watched: Boolean) {
        val state = uiState.value
        val section = state.sections.firstOrNull { it.number == number } ?: return
        val entries = section.videos.mapNotNull(state::entryFor)
        if (watched) {
            markWatched(entries.filterNot { isWatched(state, it) })
        } else {
            viewModelScope.launch {
                entries.forEach { watchProgressRepository.removeFromHistory(it.id, videoId = it.videoId, season = it.season, episode = it.episode) }
            }
        }
    }

    private fun isWatched(state: PlaylistUiState, entry: PlaylistEntry): Boolean = entry.key in state.watchedEntryKeys

    private fun markWatched(entries: List<PlaylistEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch { watchProgressRepository.markAsCompletedBatch(entries.map(::completedProgress)) }
    }

    private fun completedProgress(entry: PlaylistEntry): WatchProgress {
        val runtimeMs = entry.runtime?.toLong()?.times(60_000L) ?: 1L
        return WatchProgress(
            contentId = entry.id,
            contentType = entry.type,
            name = entry.show ?: entry.title ?: entry.id,
            poster = null,
            backdrop = entry.thumbnail,
            logo = null,
            videoId = entry.videoId,
            season = entry.season,
            episode = entry.episode,
            episodeTitle = entry.title.takeIf { entry.isEpisode },
            position = runtimeMs,
            duration = runtimeMs,
            lastWatched = System.currentTimeMillis(),
            progressPercent = 100f
        )
    }

    init {
        viewModelScope.launch {
            val ref = playlistRef ?: run {
                load.value = Load(isLoading = false, playlist = null)
                return@launch
            }
            playlistRepository.observePlaylist(ref)
                .collect { playlist -> load.value = Load(isLoading = true, playlist = playlist) }
            load.value = Load(isLoading = false, playlist = load.value.playlist)
        }
    }

    private companion object {
        fun cardId(entry: PlaylistEntry) = "playlist-entry:${entry.key}"

        fun PlaylistEntry.toCard(coordinate: Pair<Int, Int>) = Video(
            id = cardId(this),
            title = title ?: show ?: id,
            released = released,
            thumbnail = thumbnail,
            season = coordinate.first,
            episode = coordinate.second,
            overview = overview,
            runtime = runtime
        )
    }
}
