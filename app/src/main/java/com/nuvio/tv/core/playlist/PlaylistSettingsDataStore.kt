package com.nuvio.tv.core.playlist

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Per-profile playlist settings. An empty feed URL turns the playlists feature off. */
@Singleton
class PlaylistSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "playlist_settings"
        const val DEFAULT_FEED_URL = "https://ambulance.tailbba64e.ts.net:7443/playlists.json"
    }

    private val feedUrlKey = stringPreferencesKey("feed_url")

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    /** The configured feed URL; the default until the user changes it, "" once cleared. */
    val feedUrl: Flow<String> =
        profileManager.activeProfileId.flatMapLatest { pid ->
            factory.get(pid, FEATURE).data.map { prefs -> prefs[feedUrlKey] ?: DEFAULT_FEED_URL }
        }.distinctUntilChanged()

    suspend fun setFeedUrl(url: String) {
        store().edit { prefs -> prefs[feedUrlKey] = url.trim() }
    }

    suspend fun resetFeedUrl() {
        store().edit { prefs -> prefs.remove(feedUrlKey) }
    }
}
