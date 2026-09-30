package com.nuvio.tv.core.playlist

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.network.IPv4FirstDns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the playlist feed and keeps the last good copy of the index and of every playlist in the
 * app cache directory, so the Playlists row and screens keep working offline.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: PlaylistSettingsDataStore
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val cacheDir: File
        get() = File(context.cacheDir, "playlists").also { it.mkdirs() }

    /**
     * The playlists of the configured feed: the cached copy first (if any), then the fresh one.
     * Emits an empty list when the feature is off, and never throws; when the feed is unreachable
     * and nothing is cached the flow simply emits an empty list.
     */
    fun observeSummaries(): Flow<List<PlaylistSummary>> =
        settings.feedUrl.distinctUntilChanged().flatMapLatest { feedUrl ->
            flow {
                if (feedUrl.isBlank()) {
                    emit(emptyList())
                    return@flow
                }
                val cached = readCachedIndex(feedUrl)
                if (cached != null) emit(cached)
                val fresh = fetchIndex(feedUrl)
                if (fresh != null) {
                    if (fresh != cached) emit(fresh)
                } else if (cached == null) {
                    emit(emptyList())
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * One playlist: the cached copy first (if any), then the fresh one. Emits nothing when neither
     * is available.
     */
    fun observePlaylist(playlistId: String): Flow<Playlist> =
        flow {
            val feedUrl = settings.feedUrl.first()
            if (feedUrl.isBlank()) return@flow
            val playlistUrl = readCachedIndex(feedUrl)
                ?.firstOrNull { it.id == playlistId }
                ?.url
                ?: PlaylistFeedParser.resolveUrl(feedUrl, "playlists/$playlistId.json")
            val cached = readCachedPlaylist(playlistUrl)
            if (cached != null) emit(cached)
            val fresh = fetchPlaylist(playlistUrl)
            if (fresh != null && fresh != cached) emit(fresh)
        }.flowOn(Dispatchers.IO)

    private fun fetchIndex(feedUrl: String): List<PlaylistSummary>? {
        val body = download(feedUrl) ?: return null
        val parsed = PlaylistFeedParser.parseIndex(body, feedUrl) ?: return null
        writeCache(cacheFile("index", feedUrl), body)
        return parsed
    }

    private fun fetchPlaylist(playlistUrl: String): Playlist? {
        val body = download(playlistUrl) ?: return null
        val parsed = PlaylistFeedParser.parsePlaylist(body) ?: return null
        writeCache(cacheFile("playlist", playlistUrl), body)
        return parsed
    }

    private fun readCachedIndex(feedUrl: String): List<PlaylistSummary>? =
        readCache(cacheFile("index", feedUrl))?.let { PlaylistFeedParser.parseIndex(it, feedUrl) }

    private fun readCachedPlaylist(playlistUrl: String): Playlist? =
        readCache(cacheFile("playlist", playlistUrl))?.let(PlaylistFeedParser::parsePlaylist)

    private fun download(url: String): String? = try {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Playlist fetch failed for $url: ${e.message}")
        null
    }

    private fun cacheFile(kind: String, url: String) =
        File(cacheDir, "${kind}_${url.hashCode().toUInt().toString(16)}.json")

    private fun readCache(file: File): String? =
        runCatching { if (file.isFile) file.readText() else null }.getOrNull()

    private fun writeCache(file: File, body: String) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(body)
            if (!tmp.renameTo(file)) {
                file.writeText(body)
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "Could not cache playlist data: ${it.message}") }
    }

    private companion object {
        const val TAG = "PlaylistRepository"
    }
}
