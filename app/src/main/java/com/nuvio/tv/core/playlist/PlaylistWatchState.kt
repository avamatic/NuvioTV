package com.nuvio.tv.core.playlist

import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem

/** What the user has watched of the real titles behind playlist entries, keyed like [PlaylistEntry.watchedKey]. */
class PlaylistWatchState private constructor(
    /** Latest progress per title. */
    val latest: Map<String, WatchProgress>,
    /** Titles that are watched: marked watched, or with completed progress. */
    val watchedKeys: Set<String>,
    private val watchedAt: Map<String, Long>
) {
    fun isWatched(entry: PlaylistEntry): Boolean = entry.watchedKey in watchedKeys

    fun isInProgress(entry: PlaylistEntry): Boolean = latest[entry.watchedKey]?.isInProgress() == true

    /** When the user last watched [entry]'s title, or 0. */
    fun lastWatched(entry: PlaylistEntry): Long =
        maxOf(latest[entry.watchedKey]?.lastWatched ?: 0L, watchedAt[entry.watchedKey] ?: 0L)

    companion object {
        val EMPTY = PlaylistWatchState(emptyMap(), emptySet(), emptyMap())

        fun from(allProgress: List<WatchProgress>, watchedItems: List<WatchedItem>): PlaylistWatchState {
            val latest = HashMap<String, WatchProgress>(allProgress.size)
            allProgress.forEach { progress ->
                val key = playlistWatchedKey(progress.contentId, progress.season, progress.episode)
                val existing = latest[key]
                if (existing == null || progress.lastWatched > existing.lastWatched) latest[key] = progress
            }
            val watchedAt = HashMap<String, Long>(watchedItems.size)
            watchedItems.forEach { item ->
                val key = playlistWatchedKey(item.contentId, item.season, item.episode)
                watchedAt[key] = maxOf(watchedAt[key] ?: 0L, item.watchedAt)
            }
            val watchedKeys = HashSet<String>(watchedAt.keys)
            latest.forEach { (key, progress) -> if (progress.isCompleted()) watchedKeys.add(key) }
            return PlaylistWatchState(latest, watchedKeys, watchedAt)
        }
    }
}

/** A playlist's next entry, due once the user finished the entry at their saved place. */
data class PlaylistUpNext(
    val playlist: Playlist,
    val previous: PlaylistEntry,
    val entry: PlaylistEntry,
    /** When the previous entry was finished, for ordering among other Continue Watching items. */
    val timestamp: Long
)

object PlaylistUpNextResolver {
    /**
     * For each saved place whose entry is watched, the first unwatched entry after it. Places
     * whose entry is unfinished, or whose next entry is already in progress, are left out:
     * Continue Watching shows the in-progress title itself.
     */
    fun resolve(places: List<PlaylistPlaybackSession.Position>, state: PlaylistWatchState): List<PlaylistUpNext> =
        places.mapNotNull { place ->
            val previous = place.entry
            if (!state.isWatched(previous)) return@mapNotNull null
            val entries = place.playlist.entries
            val next = (place.index + 1 until entries.size).asSequence()
                .map(entries::get)
                .firstOrNull { !state.isWatched(it) }
                ?: return@mapNotNull null
            if (state.isInProgress(next)) return@mapNotNull null
            PlaylistUpNext(place.playlist, previous, next, maxOf(place.updatedAt, state.lastWatched(previous)))
        }
}
