package com.nuvio.tv.core.playlist

import android.util.Log
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.ServerConfiguration
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlaylistConfigSync"

/** Local-first configuration sync for custom account servers supporting the additive v1 API. */
@Singleton
@OptIn(FlowPreview::class)
class PlaylistConfigurationSyncService @Inject constructor(
    private val settings: PlaylistSettingsDataStore,
    private val authManager: AuthManager,
    private val profileManager: ProfileManager,
    private val serverConfiguration: ServerConfiguration,
    private val postgrest: Postgrest
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        scope.launch {
            combine(authManager.authState, profileManager.activeProfileId) { auth, profile -> auth to profile }
                .collectLatest { (auth, profileId) ->
                    if (auth !is AuthState.FullAccount || !serverConfiguration.isCustom) return@collectLatest
                    coroutineScope {
                        launch {
                            settings.observeConfiguration(profileId).debounce(500).collect {
                                synchronize(auth.userId, profileId)
                            }
                        }
                        while (isActive) {
                            synchronize(auth.userId, profileId)
                            delay(60_000)
                        }
                    }
                }
        }
    }

    private fun stillActive(userId: String, profileId: Int): Boolean =
        (authManager.authState.value as? AuthState.FullAccount)?.userId == userId &&
            profileManager.activeProfileId.value == profileId

    private suspend fun <T> withJwtRefreshRetry(block: suspend () -> T): T = try {
        block()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!authManager.refreshSessionIfJwtExpired(error)) throw error
        block()
    }

    private suspend fun synchronize(userId: String, profileId: Int) = mutex.withLock {
        try {
            val owner = "${serverConfiguration.backendUrl}|$userId"
            repeat(3) {
                if (!stillActive(userId, profileId)) return@withLock
                val snapshot = settings.syncSnapshot(profileId, owner)
                val rows = withJwtRefreshRetry {
                    postgrest.rpc("sync_pull_playlist_configuration", buildJsonObject {
                        put("p_profile_id", profileId)
                    }).decodeList<RemotePlaylistConfiguration>()
                }
                val remote = rows.singleOrNull()
                val remoteConfig = remote?.configuration?.let(::decode)
                val base = snapshot.document?.let(::decode)
                val merged = if (remoteConfig == null) snapshot.local else
                    mergePlaylistConfiguration(base, snapshot.local, remoteConfig)
                // Preserve extension fields supplied by another v1 client.
                val document = JsonObject((remote?.configuration ?: snapshot.document).orEmpty() +
                    (json.encodeToJsonElement(merged) as JsonObject))
                if (!stillActive(userId, profileId)) return@withLock
                val saved = if (remote != null && merged == remoteConfig) remote else {
                    try {
                        withJwtRefreshRetry {
                            postgrest.rpc("sync_push_playlist_configuration", buildJsonObject {
                                put("p_profile_id", profileId)
                                put("p_configuration", document)
                                put("p_expected_revision", remote?.revision ?: 0)
                            }).decodeList<RemotePlaylistConfiguration>().single()
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (error.message.orEmpty().contains("PT409")) return@repeat
                        throw error
                    }
                }
                if (!stillActive(userId, profileId)) return@withLock
                if (settings.acknowledgeConfiguration(profileId, owner, snapshot.local,
                        decode(saved.configuration), saved.configuration, saved.revision)) return@withLock
                // A local edit arrived during the request. Re-read it before acknowledging.
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Outages and older servers must never clear the last known configuration.
            Log.w(TAG, "Playlist configuration sync failed for profile $profileId; retaining local settings", error)
        }
    }

    private fun decode(document: JsonObject): PlaylistConfiguration {
        val config = json.decodeFromJsonElement<PlaylistConfiguration>(document)
        require(config.version == 1) { "Unsupported playlist configuration version" }
        return config.copy(disabledPlaylists = config.disabledPlaylists.distinct()
            .sortedWith(compareBy({ it.sourceUrl }, { it.id })))
    }
}

@Serializable
private data class RemotePlaylistConfiguration(val configuration: JsonObject, val revision: Long)
