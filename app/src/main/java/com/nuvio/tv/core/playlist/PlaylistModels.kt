package com.nuvio.tv.core.playlist

import androidx.compose.runtime.Immutable
import java.net.URLDecoder
import java.net.URLEncoder

const val PLAYLIST_ENTRY_TYPE_MOVIE = "movie"
const val PLAYLIST_ENTRY_TYPE_SERIES = "series"

/** Highest playlist format version this app understands (see chronio/playlist-format.md). */
const val PLAYLIST_FORMAT_VERSION = 1

/** Identifies a playlist across sources: the source (index) URL plus the playlist's id in it. */
@Immutable
data class PlaylistRef(val sourceUrl: String, val id: String) {
    /** Single-string form for navigation arguments and stored preferences. */
    val key: String
        get() = "${URLEncoder.encode(id, "UTF-8")}@$sourceUrl"

    companion object {
        fun fromKey(key: String): PlaylistRef? {
            val at = key.indexOf('@')
            if (at <= 0 || at == key.lastIndex) return null
            val id = runCatching { URLDecoder.decode(key.substring(0, at), "UTF-8") }.getOrNull() ?: return null
            return PlaylistRef(sourceUrl = key.substring(at + 1), id = id)
        }
    }
}

/** A playlist as listed in its source's index. [url] is absolute; [inline] is set for embedded playlists. */
@Immutable
data class PlaylistSummary(
    val ref: PlaylistRef,
    val name: String,
    val description: String?,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val entryCount: Int?,
    val updatedAt: String?,
    val url: String?,
    val inline: Playlist?,
    /** False when the source uses a newer format version than this app understands. */
    val supported: Boolean
)

/** A source's index. */
@Immutable
data class PlaylistIndex(
    val name: String?,
    val description: String?,
    val playlists: List<PlaylistSummary>
)

/**
 * A reference to a title Nuvio can play. [type] and [id] are what addons know it by; [season] and
 * [episode] are real coordinates within [id]. Everything else is a display hint.
 */
@Immutable
data class PlaylistEntry(
    val key: String,
    val type: String,
    val id: String,
    val season: Int?,
    val episode: Int?,
    val explicitVideoId: String? = null,
    val show: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    val released: String? = null
) {
    val isMovie: Boolean
        get() = type == PLAYLIST_ENTRY_TYPE_MOVIE

    /** An episode of [id] rather than a whole title. */
    val isEpisode: Boolean
        get() = season != null && episode != null

    /** Addon video id: explicit, else "id:season:episode" for episodes and the id otherwise. */
    val videoId: String
        get() = explicitVideoId ?: if (isEpisode) "$id:$season:$episode" else id

    /** Key matching the watched-state index built by [playlistWatchedKey]. */
    val watchedKey: String
        get() = playlistWatchedKey(id, season, episode)
}

@Immutable
data class PlaylistSection(
    val title: String?,
    val entries: List<PlaylistEntry>
)

@Immutable
data class Playlist(
    val ref: PlaylistRef,
    val name: String,
    val description: String?,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val updatedAt: String?,
    val sections: List<PlaylistSection>,
    /** Entries the app could not use (missing fields, unknown shape, duplicate keys). */
    val skippedEntries: Int = 0,
    /** False when the file uses a newer format version; [sections] is then empty. */
    val supported: Boolean = true
) {
    /** All entries in play order. */
    val entries: List<PlaylistEntry> by lazy { sections.flatMap { it.entries } }
}

/** Same shape as the watched-item keys in `WatchedItemsPreferences`: "{id}|{S or _}|{E or _}". */
fun playlistWatchedKey(contentId: String, season: Int?, episode: Int?): String =
    "$contentId|${season?.toString() ?: "_"}|${episode?.toString() ?: "_"}"
