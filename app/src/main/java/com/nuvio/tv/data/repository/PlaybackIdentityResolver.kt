package com.nuvio.tv.data.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.VideoPlaybackIdentity
import com.nuvio.tv.domain.repository.MetaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves the addon-supplied [VideoPlaybackIdentity] of [videoId] within the meta [contentId].
 *
 * The cached meta is used when available. Otherwise the meta is fetched, but only for video IDs
 * that are not already IMDb-based, so ordinary titles never wait on an extra meta request.
 */
suspend fun MetaRepository.resolvePlaybackIdentity(
    type: String?,
    contentId: String?,
    videoId: String?
): VideoPlaybackIdentity? {
    val resolvedType = type?.takeIf { it.isNotBlank() } ?: return null
    val metaId = contentId?.takeIf { it.isNotBlank() } ?: return null
    val resolvedVideoId = videoId?.takeIf { it.isNotBlank() } ?: return null
    getCachedMeta(resolvedType, metaId)?.let { meta ->
        return meta.videos.firstOrNull { it.id == resolvedVideoId }?.playbackIdentity
    }
    if (resolvedVideoId.startsWith("tt")) return null
    val result = withTimeoutOrNull(8_000L) {
        getMetaFromAllAddons(type = resolvedType, id = metaId).first { it !is NetworkResult.Loading }
    }
    return (result as? NetworkResult.Success)?.data?.videos
        ?.firstOrNull { it.id == resolvedVideoId }
        ?.playbackIdentity
}
