package com.nuvio.tv.core.playlist

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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-profile playlist settings: the source URLs the user added, in order, and the playlists
 * they turned off (by [PlaylistRef.key]). There is no default source.
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
    private val sourcesKey = stringPreferencesKey("source_urls")
    private val disabledKey = stringSetPreferencesKey("disabled_playlists")

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

    /** Removes [url] and forgets which of its playlists were turned off. */
    suspend fun removeSource(url: String) {
        store().edit { prefs ->
            prefs[sourcesKey] = gson.toJson(decodeSources(prefs[sourcesKey]) - url)
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
