package com.nuvio.tv.ui.screens.home

import androidx.compose.runtime.Immutable
import com.nuvio.tv.core.playlist.PlaylistPlaybackSession
import com.nuvio.tv.core.playlist.PlaylistRef
import com.nuvio.tv.core.playlist.PlaylistUpNext
import com.nuvio.tv.core.playlist.playlistWatchedKey
import com.nuvio.tv.domain.model.ContinueWatchingSortMode
import com.nuvio.tv.domain.model.WatchProgress

/**
 * Playlist additions to Continue Watching, laid over the list Home builds on its own:
 *
 * - each playlist's next entry appears once the entry at the user's place is finished, as a
 *   normal Next up card for an episode, or a not-yet-started card for a movie;
 * - a show's own Next up is hidden while a playlist is responsible for it, i.e. the episode it
 *   was seeded from is behind the user's place in a playlist and the one it offers is ahead.
 *
 * Kept apart from the Continue Watching pipeline so the pipeline itself stays as upstream has it.
 */
@Immutable
internal class PlaylistContinueWatching(
    private val upNext: List<PlaylistUpNext>,
    private val ranges: List<PlaceRange>,
    private val sortMode: ContinueWatchingSortMode
) {
    /** Entry keys up to and including a place, and after it. */
    class PlaceRange(val behind: Set<String>, val ahead: Set<String>)

    fun applyTo(state: HomeUiState): HomeUiState {
        if (upNext.isEmpty() && ranges.isEmpty()) return state
        val current = state.continueWatchingItems + state.upcomingItems
        val inProgress = current.mapNotNullTo(HashSet()) { item ->
            (item as? ContinueWatchingItem.InProgress)?.targetKey()
        }
        val playlistItems = upNext.map { it.toItem() }.filterNot { it.targetKey() in inProgress }
        val playlistTargets = playlistItems.mapTo(HashSet()) { it.targetKey() }
        val kept = current.filterNot { item ->
            item is ContinueWatchingItem.NextUp && (item.targetKey() in playlistTargets || isHandledByPlaylist(item.info))
        }
        if (playlistItems.isEmpty() && kept.size == current.size) return state
        val (main, upcoming) = splitUpcomingItems(sortContinueWatchingItems(kept + playlistItems, sortMode), sortMode)
        return state.copy(continueWatchingItems = main, upcomingItems = upcoming)
    }

    /**
     * The playlist a removed card came from. Home identifies a Next up card by its title and the
     * episode it was seeded from, and other cards by their title.
     */
    fun playlistFor(contentId: String, season: Int?, episode: Int?, isNextUp: Boolean): PlaylistRef? =
        upNext.firstOrNull { up ->
            up.entry.id == contentId && if (isNextUp) {
                up.entry.isEpisode && up.previous.season == season && up.previous.episode == episode
            } else {
                !up.entry.isEpisode
            }
        }?.playlist?.ref

    private fun isHandledByPlaylist(info: NextUpInfo): Boolean {
        val seed = playlistWatchedKey(info.contentId, info.seedSeason ?: return false, info.seedEpisode ?: return false)
        val target = playlistWatchedKey(info.contentId, info.season, info.episode)
        return ranges.any { seed in it.behind && target in it.ahead }
    }

    private fun PlaylistUpNext.toItem(): ContinueWatchingItem {
        val name = entry.show ?: entry.title ?: entry.id
        if (!entry.isEpisode) {
            return ContinueWatchingItem.InProgress(
                progress = WatchProgress(
                    contentId = entry.id,
                    contentType = entry.type,
                    name = name,
                    poster = null,
                    backdrop = entry.thumbnail,
                    logo = null,
                    videoId = entry.videoId,
                    season = null,
                    episode = null,
                    episodeTitle = null,
                    position = 0L,
                    duration = entry.runtime?.toLong()?.times(60_000L) ?: 0L,
                    lastWatched = timestamp
                ),
                episodeDescription = entry.overview,
                episodeThumbnail = entry.thumbnail
            )
        }
        return ContinueWatchingItem.NextUp(
            NextUpInfo(
                contentId = entry.id,
                contentType = entry.type,
                name = name,
                poster = null,
                backdrop = entry.thumbnail,
                logo = null,
                videoId = entry.videoId,
                season = entry.season ?: 0,
                episode = entry.episode ?: 0,
                episodeTitle = entry.title,
                episodeDescription = entry.overview,
                thumbnail = entry.thumbnail,
                released = entry.released,
                lastWatched = timestamp,
                sortTimestamp = timestamp,
                seedSeason = previous.season,
                seedEpisode = previous.episode
            )
        )
    }

    companion object {
        val EMPTY = PlaylistContinueWatching(emptyList(), emptyList(), ContinueWatchingSortMode.DEFAULT)

        fun build(
            places: List<PlaylistPlaybackSession.Position>,
            upNext: List<PlaylistUpNext>,
            sortMode: ContinueWatchingSortMode
        ): PlaylistContinueWatching {
            val ranges = places.map { place ->
                val keys = place.playlist.entries.map { it.watchedKey }
                PlaceRange(
                    behind = keys.subList(0, place.index + 1).toHashSet(),
                    ahead = keys.subList(place.index + 1, keys.size).toHashSet()
                )
            }
            return PlaylistContinueWatching(upNext, ranges, sortMode)
        }

        private fun ContinueWatchingItem.targetKey(): String = when (this) {
            is ContinueWatchingItem.InProgress -> playlistWatchedKey(progress.contentId, progress.season, progress.episode)
            is ContinueWatchingItem.NextUp -> playlistWatchedKey(info.contentId, info.season, info.episode)
        }
    }
}
