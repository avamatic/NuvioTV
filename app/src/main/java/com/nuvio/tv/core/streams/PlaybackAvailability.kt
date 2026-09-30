package com.nuvio.tv.core.streams

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.ScraperInfo
import com.nuvio.tv.domain.model.Video

internal fun Addon.supportsStreamResource(type: String, videoId: String): Boolean =
    resources.any { resource ->
        resource.name == "stream" &&
            (resource.types.isEmpty() || resource.types.contains(type)) &&
            run {
                val prefixes = resource.idPrefixes?.takeIf { it.isNotEmpty() }
                    ?: idPrefixes.takeIf { it.isNotEmpty() }
                prefixes == null || prefixes.any { videoId.startsWith(it) }
            }
    }

internal data class PlaybackAvailability(
    val addons: List<Addon> = emptyList(),
    val scrapers: List<ScraperInfo> = emptyList(),
    val isLoaded: Boolean = false,
    private val cachedMeta: (String, String) -> Meta? = { _, _ -> null }
) {
    fun canStream(
        type: String,
        videoId: String,
        contentId: String = videoId,
        video: Video? = null
    ): Boolean {
        val meta = cachedMeta(type, contentId)
        val playbackIdentity = video?.takeIf { it.id == videoId }?.playbackIdentity
            ?: meta?.videos?.firstOrNull { it.id == videoId }?.playbackIdentity
        if (playbackIdentity != null) {
            // Streams are searched by the real title, not the synthetic catalogue video.
            return addons.any { it.enabled && it.supportsStreamResource(playbackIdentity.type, playbackIdentity.videoId) } ||
                scrapers.any { it.enabled && it.supportsType(playbackIdentity.type) }
        }
        // An uncached synthetic video (e.g. resuming after a restart) may carry an identity the
        // streams screen resolves; let it through rather than reporting playback unavailable.
        if (meta == null && video?.id != videoId && contentId != videoId && !videoId.startsWith("tt") &&
            (addons.any { addon -> addon.enabled && addon.resources.any { it.name == "stream" } } || scrapers.any { it.enabled })
        ) return true
        return video?.takeIf { it.id == videoId }?.streams?.isNotEmpty() == true ||
            meta?.videos?.any { it.id == videoId && it.streams.isNotEmpty() } == true ||
            addons.any { it.enabled && it.supportsStreamResource(type, videoId) } ||
            scrapers.any { it.enabled && it.supportsType(type) }
    }
}
