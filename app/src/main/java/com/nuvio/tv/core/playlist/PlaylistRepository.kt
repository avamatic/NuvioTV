package com.nuvio.tv.core.playlist

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.nuvio.tv.core.network.IPv4FirstDns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaylistSourceError { UNREACHABLE, NOT_AN_INDEX }

class PlaylistSourceException(val error: PlaylistSourceError) : Exception(error.name)

/** A source as last seen: its index (fresh or cached) and whether the last fetch failed. */
@Immutable
data class PlaylistSourceState(
    val url: String,
    val index: PlaylistIndex?,
    val error: PlaylistSourceError?,
    val loading: Boolean
)

/**
 * Fetches playlist sources and playlists, keeping the last good copy of each file in the app cache
 * so playlists keep working offline. Revalidates with ETag/Last-Modified, refreshes sources hourly
 * while observed, and on [refresh].
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

    private val refreshTick = MutableStateFlow(0)

    /** Re-fetches every source now. */
    fun refresh() = refreshTick.update { it + 1 }

    /** Every configured source, in the user's order. */
    fun observeSources(): Flow<List<PlaylistSourceState>> =
        combine(settings.sourceUrls, refreshTick) { urls, _ -> urls }
            .flatMapLatest { urls ->
                if (urls.isEmpty()) flowOf(emptyList())
                else combine(urls.map(::observeSource)) { it.toList() }
            }

    /** Playlists of every source that are supported and not turned off, in source order. */
    fun observeEnabledSummaries(): Flow<List<PlaylistSummary>> =
        combine(observeSources(), settings.disabledPlaylists) { sources, disabled ->
            sources.flatMap { it.index?.playlists.orEmpty() }
                .filter { it.supported && it.ref.key !in disabled }
        }.distinctUntilChanged()

    /** Fetches [url] as a source index without adding it, to validate it before it is saved. */
    suspend fun loadIndex(url: String): Result<PlaylistIndex> = withContext(Dispatchers.IO) { fetchIndex(url) }

    /** One playlist: the cached copy first (if any), then the fresh one. Emits nothing if it can't be found. */
    fun observePlaylist(ref: PlaylistRef): Flow<Playlist> =
        flow {
            val summary = (readCachedIndex(ref.sourceUrl) ?: fetchIndex(ref.sourceUrl).getOrNull())
                ?.playlists
                ?.firstOrNull { it.ref == ref }
                ?: return@flow
            if (!summary.supported) {
                emit(summary.toUnsupportedPlaylist())
                return@flow
            }
            summary.inline?.let {
                emit(it)
                return@flow
            }
            val url = summary.url ?: return@flow
            val cached = readCachedPlaylist(url, ref)
            if (cached != null) emit(cached)
            val fresh = fetchPlaylist(url, ref).getOrNull()
            if (fresh != null && fresh != cached) emit(fresh)
        }.flowOn(Dispatchers.IO)

    private fun observeSource(url: String): Flow<PlaylistSourceState> =
        flow {
            var index = readCachedIndex(url)
            emit(PlaylistSourceState(url, index, error = null, loading = true))
            while (true) {
                val result = fetchIndex(url)
                result.getOrNull()?.let { index = it }
                val error = (result.exceptionOrNull() as? PlaylistSourceException)?.error
                    ?: result.exceptionOrNull()?.let { PlaylistSourceError.UNREACHABLE }
                emit(PlaylistSourceState(url, index, error, loading = false))
                delay(REFRESH_INTERVAL_MS)
            }
        }.flowOn(Dispatchers.IO)

    private fun fetchIndex(url: String): Result<PlaylistIndex> =
        download(url, cacheFile("index", url)) { PlaylistFormatParser.parseIndex(it, url) }

    private fun fetchPlaylist(url: String, ref: PlaylistRef): Result<Playlist> =
        download(url, cacheFile("playlist", url)) { PlaylistFormatParser.parsePlaylist(it, ref) }

    private fun readCachedIndex(url: String): PlaylistIndex? =
        readCache(cacheFile("index", url))?.let { PlaylistFormatParser.parseIndex(it, url) }

    private fun readCachedPlaylist(url: String, ref: PlaylistRef): Playlist? =
        readCache(cacheFile("playlist", url))?.let { PlaylistFormatParser.parsePlaylist(it, ref) }

    /**
     * Downloads [url], revalidating the cached copy in [file] when one exists. A body is cached
     * only if [parse] accepts it.
     */
    private fun <T : Any> download(url: String, file: File, parse: (String) -> T?): Result<T> {
        val cached = readCache(file)
        val validators = if (cached != null) readCache(validatorFile(file))?.lines().orEmpty() else emptyList()
        return try {
            val request = Request.Builder().url(url).header("Accept", "application/json").apply {
                validators.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { header("If-None-Match", it) }
                validators.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { header("If-Modified-Since", it) }
            }.build()
            client.newCall(request).execute().use { response ->
                if (response.code == 304 && cached != null) {
                    return parse(cached)?.let { Result.success(it) }
                        ?: Result.failure(PlaylistSourceException(PlaylistSourceError.NOT_AN_INDEX))
                }
                if (!response.isSuccessful) return Result.failure(PlaylistSourceException(PlaylistSourceError.UNREACHABLE))
                val body = response.body?.string()
                    ?: return Result.failure(PlaylistSourceException(PlaylistSourceError.UNREACHABLE))
                val parsed = parse(body)
                    ?: return Result.failure(PlaylistSourceException(PlaylistSourceError.NOT_AN_INDEX))
                writeCache(file, body)
                writeCache(validatorFile(file), "${response.header("ETag").orEmpty()}\n${response.header("Last-Modified").orEmpty()}")
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Playlist fetch failed for $url: ${e.message}")
            Result.failure(PlaylistSourceException(PlaylistSourceError.UNREACHABLE))
        }
    }

    private fun cacheFile(kind: String, url: String) =
        File(cacheDir, "${kind}_${url.hashCode().toUInt().toString(16)}.json")

    private fun validatorFile(file: File) = File(file.parentFile, file.name + ".validators")

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

    private fun PlaylistSummary.toUnsupportedPlaylist() = Playlist(
        ref = ref,
        name = name,
        description = description,
        poster = poster,
        background = background,
        logo = logo,
        updatedAt = updatedAt,
        sections = emptyList(),
        supported = false
    )

    private companion object {
        const val TAG = "PlaylistRepository"
        const val REFRESH_INTERVAL_MS = 60L * 60L * 1000L
    }
}
