package com.nuvio.tv.core.playlist

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI

/**
 * Tolerant parser for the Chronio playlist feed. Unknown fields are ignored; anything that cannot
 * be played as a real title (no IMDb id, unknown type, episode without season/episode) is skipped
 * instead of failing the whole playlist.
 */
object PlaylistFeedParser {
    private val IMDB_ID = Regex("""tt\d+""")

    /** Returns the playlists in the index, or null if [json] is not a feed index at all. */
    fun parseIndex(json: String, indexUrl: String): List<PlaylistSummary>? {
        val root = parseObject(json) ?: return null
        val playlists = root.get("playlists")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val seen = HashSet<String>()
        return playlists.mapNotNull { element ->
            val obj = element.asObjectOrNull() ?: return@mapNotNull null
            val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val url = obj.string("url")?.takeIf { it.isNotBlank() } ?: "playlists/$id.json"
            PlaylistSummary(
                id = id,
                name = name,
                description = obj.string("description")?.takeIf { it.isNotBlank() },
                image = obj.string("image")?.takeIf { it.isNotBlank() },
                entries = obj.int("entries") ?: 0,
                movies = obj.int("movies") ?: 0,
                episodes = obj.int("episodes") ?: 0,
                source = obj.string("source"),
                updatedAt = obj.string("updatedAt"),
                stale = obj.get("stale")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.let { runCatching { it.asBoolean }.getOrNull() }
                    ?: false,
                url = resolveUrl(indexUrl, url)
            )
        }
    }

    /** Returns the playlist, or null if [json] is not a playlist document. */
    fun parsePlaylist(json: String): Playlist? {
        val root = parseObject(json) ?: return null
        val id = root.string("id")?.takeIf { it.isNotBlank() } ?: return null
        val name = root.string("name")?.takeIf { it.isNotBlank() } ?: return null
        val rawEntries = root.get("entries")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val seenKeys = HashSet<String>()
        val entries = rawEntries.mapNotNull { element ->
            parseEntry(element.asObjectOrNull() ?: return@mapNotNull null)
        }.filter { seenKeys.add(it.key) }
        return Playlist(
            id = id,
            name = name,
            description = root.string("description")?.takeIf { it.isNotBlank() },
            image = root.string("image")?.takeIf { it.isNotBlank() },
            source = root.string("source"),
            entries = entries
        )
    }

    private fun parseEntry(obj: JsonObject): PlaylistEntry? {
        val type = obj.string("type")?.lowercase()
        if (type != PLAYLIST_ENTRY_TYPE_MOVIE && type != PLAYLIST_ENTRY_TYPE_SERIES) return null
        val id = obj.string("id")?.trim()?.takeIf { IMDB_ID.matches(it) } ?: return null
        val season = obj.int("season")
        val episode = obj.int("episode")
        if (type == PLAYLIST_ENTRY_TYPE_SERIES && (season == null || episode == null)) return null
        val isMovie = type == PLAYLIST_ENTRY_TYPE_MOVIE
        val key = obj.string("key")?.takeIf { it.isNotBlank() }
            ?: if (isMovie) "movie:$id" else "series:$id:$season:$episode"
        return PlaylistEntry(
            key = key,
            type = type,
            id = id,
            season = if (isMovie) null else season,
            episode = if (isMovie) null else episode,
            show = obj.string("show")?.takeIf { it.isNotBlank() },
            title = obj.string("title")?.takeIf { it.isNotBlank() },
            image = obj.string("image")?.takeIf { it.isNotBlank() }
        )
    }

    /** [reference] is relative to [baseUrl] unless it is already absolute. */
    fun resolveUrl(baseUrl: String, reference: String): String =
        runCatching { URI(baseUrl.trim()).resolve(reference.trim()).toString() }.getOrDefault(reference)

    private fun parseObject(json: String): JsonObject? =
        runCatching { JsonParser.parseString(json) }.getOrNull()?.asObjectOrNull()

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
}
