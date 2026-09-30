package com.nuvio.tv.core.playlist

import androidx.compose.runtime.Immutable

const val PLAYLIST_ENTRY_TYPE_MOVIE = "movie"
const val PLAYLIST_ENTRY_TYPE_SERIES = "series"

/** One row of the feed index. [url] is absolute. */
@Immutable
data class PlaylistSummary(
    val id: String,
    val name: String,
    val description: String?,
    val image: String?,
    val entries: Int,
    val movies: Int,
    val episodes: Int,
    val source: String?,
    val updatedAt: String?,
    val stale: Boolean,
    val url: String
)

/**
 * A reference to a real title. [id] is always an IMDb id and [season]/[episode] are the real
 * coordinates, so playing an entry is the same as playing that title from anywhere else.
 */
@Immutable
data class PlaylistEntry(
    val key: String,
    val type: String,
    val id: String,
    val season: Int?,
    val episode: Int?,
    val show: String?,
    val title: String?,
    val image: String?
) {
    val isMovie: Boolean
        get() = type == PLAYLIST_ENTRY_TYPE_MOVIE

    /** Stremio video id: the IMDb id for movies, "id:season:episode" for episodes. */
    val videoId: String
        get() = if (isMovie || season == null || episode == null) id else "$id:$season:$episode"

    /** Key matching the watched-state index built by [playlistWatchedKey]. */
    val watchedKey: String
        get() = playlistWatchedKey(id, season.takeUnless { isMovie }, episode.takeUnless { isMovie })
}

@Immutable
data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val image: String?,
    val source: String?,
    val entries: List<PlaylistEntry>
)

/** Same shape as the watched-item keys in `WatchedItemsPreferences`: "{id}|{S or _}|{E or _}". */
fun playlistWatchedKey(contentId: String, season: Int?, episode: Int?): String =
    "$contentId|${season?.toString() ?: "_"}|${episode?.toString() ?: "_"}"
