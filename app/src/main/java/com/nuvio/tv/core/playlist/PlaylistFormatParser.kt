package com.nuvio.tv.core.playlist

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI

/**
 * Parser for playlist format version 1 (chronio/playlist-format.md). Unknown fields are ignored.
 * Entries that cannot be played are skipped and counted, never fatal. A newer format version
 * yields names only, marked unsupported.
 */
object PlaylistFormatParser {
    const val INDEX_FORMAT = "playlist-index"
    const val PLAYLIST_FORMAT = "playlist"

    /** The source's index, or null if [json] is not a playlist index. */
    fun parseIndex(json: String, sourceUrl: String): PlaylistIndex? {
        val root = parseObject(json) ?: return null
        if (root.string("format") != INDEX_FORMAT) return null
        val supported = (root.int("version") ?: return null) <= PLAYLIST_FORMAT_VERSION
        val rawPlaylists = root.get("playlists")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val seen = HashSet<String>()
        val playlists = rawPlaylists.mapNotNull { element ->
            val obj = element.asObjectOrNull() ?: return@mapNotNull null
            val id = obj.text("id") ?: return@mapNotNull null
            val name = obj.text("name") ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val ref = PlaylistRef(sourceUrl, id)
            val inline = obj.get("playlist")?.asObjectOrNull()?.let { parsePlaylist(it, ref) }
            val url = obj.text("url")?.let { resolveUrl(sourceUrl, it) }
            if (supported && inline == null && url == null) return@mapNotNull null
            PlaylistSummary(
                ref = ref,
                name = name,
                description = obj.text("description"),
                poster = obj.text("poster")?.let { resolveUrl(sourceUrl, it) },
                background = obj.text("background")?.let { resolveUrl(sourceUrl, it) },
                logo = obj.text("logo")?.let { resolveUrl(sourceUrl, it) },
                entryCount = obj.int("entries") ?: inline?.entries?.size,
                updatedAt = obj.text("updatedAt"),
                url = url,
                inline = inline,
                supported = supported && (inline?.supported ?: true)
            )
        }
        return PlaylistIndex(
            name = root.text("name"),
            description = root.text("description"),
            playlists = playlists
        )
    }

    /** The playlist in [json], or null if it is not a playlist document. */
    fun parsePlaylist(json: String, ref: PlaylistRef): Playlist? =
        parseObject(json)?.let { parsePlaylist(it, ref) }

    private fun parsePlaylist(root: JsonObject, ref: PlaylistRef): Playlist? {
        if (root.string("format") != PLAYLIST_FORMAT) return null
        val version = root.int("version") ?: return null
        val name = root.text("name") ?: return null
        val base = Playlist(
            ref = ref,
            name = name,
            description = root.text("description"),
            poster = root.text("poster")?.let { resolveUrl(ref.sourceUrl, it) },
            background = root.text("background")?.let { resolveUrl(ref.sourceUrl, it) },
            logo = root.text("logo")?.let { resolveUrl(ref.sourceUrl, it) },
            updatedAt = root.text("updatedAt"),
            sections = emptyList()
        )
        if (version > PLAYLIST_FORMAT_VERSION) return base.copy(supported = false)
        val rawSections = root.get("sections")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val seenKeys = HashSet<String>()
        var skipped = 0
        val sections = rawSections.mapNotNull { element ->
            val obj = element.asObjectOrNull() ?: return@mapNotNull null
            val rawEntries = obj.get("entries")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@mapNotNull null
            val entries = rawEntries.mapNotNull { raw ->
                val entry = raw.asObjectOrNull()?.let(::parseEntry)
                if (entry == null || !seenKeys.add(entry.key)) {
                    skipped++
                    null
                } else {
                    entry
                }
            }
            if (entries.isEmpty()) null else PlaylistSection(title = obj.text("title"), entries = entries)
        }
        return base.copy(sections = sections, skippedEntries = skipped)
    }

    private fun parseEntry(obj: JsonObject): PlaylistEntry? {
        val key = obj.text("key") ?: return null
        val type = obj.text("type")?.lowercase() ?: return null
        val id = obj.text("id") ?: return null
        val season = obj.int("season")
        val episode = obj.int("episode")
        // Half an episode reference, or a whole series, cannot be played as one entry.
        if ((season == null) != (episode == null)) return null
        if (type == PLAYLIST_ENTRY_TYPE_SERIES && season == null) return null
        return PlaylistEntry(
            key = key,
            type = type,
            id = id,
            season = season,
            episode = episode,
            explicitVideoId = obj.text("videoId"),
            show = obj.text("show"),
            title = obj.text("title"),
            thumbnail = obj.text("thumbnail"),
            overview = obj.text("overview"),
            runtime = obj.int("runtime")?.takeIf { it > 0 },
            released = obj.text("released")
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

    /** Non-blank trimmed string. */
    private fun JsonObject.text(name: String): String? = string(name)?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.let { runCatching { it.asInt }.getOrNull() }
}
