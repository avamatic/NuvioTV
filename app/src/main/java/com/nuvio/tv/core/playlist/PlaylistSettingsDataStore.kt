package com.nuvio.tv.core.playlist

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Where the user is in a playlist: the entry last played from it, and when. */
data class SavedPlaylistPlace(val ref: PlaylistRef, val entryKey: String, val updatedAt: Long)

/**
 * Per-profile playlist settings: the source URLs the user added, in order, the playlists they
 * turned off (by [PlaylistRef.key]), and their place in each playlist they have played from.
 * There is no default source.
 */
@Singleton
class PlaylistSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    private companion object {
        const val FEATURE = "playlist_settings"
    }

    private val gson = Gson()
    private val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val syncOwnerKey = stringPreferencesKey("sync_owner")
    private val syncDocumentKey = stringPreferencesKey("sync_document")
    private val syncRevisionKey = androidx.datastore.preferences.core.longPreferencesKey("sync_revision")
    private val sourcesKey = stringPreferencesKey("source_urls")
    private val disabledKey = stringSetPreferencesKey("disabled_playlists")
    private val placesKey = stringPreferencesKey("playback_places")

    private fun store() = factory.get(profileManager.activeProfileId.value, FEATURE)

    private fun decodeSources(raw: String?): List<String> =
        raw?.let { runCatching { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }.getOrNull() }
            .orEmpty()

    val sourceUrls: Flow<List<String>> =
        profileManager.activeProfileId.flatMapLatest { pid ->
            factory.get(pid, FEATURE).data.map { prefs -> decodeSources(prefs[sourcesKey]) }
        }.distinctUntilChanged()

    val disabledPlaylists: Flow<Set<String>> =
        profileManager.activeProfileId.flatMapLatest { pid ->
            factory.get(pid, FEATURE).data.map { prefs -> prefs[disabledKey].orEmpty() }
        }.distinctUntilChanged()

    val places: Flow<List<SavedPlaylistPlace>> =
        profileManager.activeProfileId.flatMapLatest { pid ->
            factory.get(pid, FEATURE).data.map { prefs -> decodePlaces(prefs[placesKey]) }
        }.distinctUntilChanged()

    private fun decodePlaces(raw: String?): List<SavedPlaylistPlace> =
        runCatching {
            val array = JSONArray(raw ?: return emptyList())
            (0 until array.length()).mapNotNull { i ->
                val stored = array.optJSONObject(i) ?: return@mapNotNull null
                val ref = PlaylistRef.fromKey(stored.optString("playlist")) ?: return@mapNotNull null
                val entry = stored.optString("entry").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                SavedPlaylistPlace(ref, entry, stored.optLong("at"))
            }
        }.getOrDefault(emptyList())

    private fun encodePlaces(places: List<SavedPlaylistPlace>): String =
        JSONArray(places.map { JSONObject().put("playlist", it.ref.key).put("entry", it.entryKey).put("at", it.updatedAt) }).toString()


    private fun configuration(prefs: Preferences): PlaylistConfiguration = PlaylistConfiguration(
        sources = decodeSources(prefs[sourcesKey]),
        disabledPlaylists = prefs[disabledKey].orEmpty().mapNotNull(PlaylistRef::fromKey)
            .map { DisabledPlaylist(it.sourceUrl, it.id) }
            .sortedWith(compareBy({ it.sourceUrl }, { it.id }))
    )

    internal fun observeConfiguration(profileId: Int): Flow<PlaylistConfiguration> =
        factory.get(profileId, FEATURE).data.map(::configuration).distinctUntilChanged()

    internal suspend fun syncSnapshot(profileId: Int, owner: String): PlaylistConfigurationSnapshot {
        val prefs = factory.get(profileId, FEATURE).data.first()
        val document = if (prefs[syncOwnerKey] == owner) {
            prefs[syncDocumentKey]?.let { syncJson.parseToJsonElement(it) as? JsonObject }
        } else null
        return PlaylistConfigurationSnapshot(configuration(prefs), document,
            if (document == null) 0 else prefs[syncRevisionKey] ?: 0)
    }

    /** Apply only if a user edit has not occurred during the network call. */
    internal suspend fun acknowledgeConfiguration(
        profileId: Int,
        owner: String,
        expectedLocal: PlaylistConfiguration,
        remote: PlaylistConfiguration,
        document: JsonObject,
        revision: Long
    ): Boolean {
        var applied = false
        factory.get(profileId, FEATURE).edit { prefs ->
            if (configuration(prefs) != expectedLocal) return@edit
            prefs[sourcesKey] = gson.toJson(remote.sources)
            prefs[disabledKey] = remote.disabledPlaylists.map { it.ref.key }.toSet()
            prefs[placesKey] = encodePlaces(decodePlaces(prefs[placesKey]).filter { it.ref.sourceUrl in remote.sources })
            prefs[syncOwnerKey] = owner
            prefs[syncDocumentKey] = document.toString()
            prefs[syncRevisionKey] = revision
            applied = true
        }
        return applied
    }

    /** Records [entryKey] as the user's place in [ref]. */
    suspend fun savePlace(ref: PlaylistRef, entryKey: String, updatedAt: Long) {
        store().edit { prefs ->
            val others = decodePlaces(prefs[placesKey]).filterNot { it.ref == ref }
            prefs[placesKey] = encodePlaces(others + SavedPlaylistPlace(ref, entryKey, updatedAt))
        }
    }

    /** Forgets the user's place in [ref]. */
    suspend fun removePlace(ref: PlaylistRef) {
        store().edit { prefs ->
            prefs[placesKey] = encodePlaces(decodePlaces(prefs[placesKey]).filterNot { it.ref == ref })
        }
    }

    /** Adds [url] at the end; returns false if it was already present. */
    suspend fun addSource(url: String): Boolean {
        var added = false
        store().edit { prefs ->
            val current = decodeSources(prefs[sourcesKey])
            if (url !in current) {
                prefs[sourcesKey] = gson.toJson(current + url)
                added = true
            }
        }
        return added
    }

    /** Removes [url] and forgets which of its playlists were turned off and the places in them. */
    suspend fun removeSource(url: String) {
        store().edit { prefs ->
            prefs[sourcesKey] = gson.toJson(decodeSources(prefs[sourcesKey]) - url)
            prefs[placesKey] = encodePlaces(decodePlaces(prefs[placesKey]).filterNot { it.ref.sourceUrl == url })
            prefs[disabledKey] = prefs[disabledKey].orEmpty()
                .filterNot { PlaylistRef.fromKey(it)?.sourceUrl == url }
                .toSet()
        }
    }

    suspend fun setPlaylistsEnabled(refs: Collection<PlaylistRef>, enabled: Boolean) {
        val keys = refs.map { it.key }.toSet()
        store().edit { prefs ->
            val current = prefs[disabledKey].orEmpty()
            prefs[disabledKey] = if (enabled) current - keys else current + keys
        }
    }
}

internal data class PlaylistConfigurationSnapshot(
    val local: PlaylistConfiguration,
    val document: JsonObject?,
    val revision: Long
)
