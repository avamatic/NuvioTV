package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.VideoPlaybackIdentity

/**
 * Real-world identity supplied by the addon for the video that is playing, if any.
 *
 * Addons that build synthetic series (for example chronological watch orders spanning
 * several shows) number their videos S1E1..N under a synthetic meta ID. Such videos may
 * carry a `playbackIdentity` so stream searches, scrobbling and skip-segment lookups use
 * the real title.
 */
internal fun PlayerRuntimeController.currentVideoPlaybackIdentity(): VideoPlaybackIdentity? {
    val videoId = currentVideoId?.takeIf { it.isNotBlank() } ?: return null
    metaVideos.firstOrNull { it.id == videoId }?.playbackIdentity?.let { return it }
    val id = contentId?.takeIf { it.isNotBlank() } ?: return null
    val type = contentType?.takeIf { it.isNotBlank() } ?: return null
    return metaRepository.getCachedMeta(type, id)
        ?.videos
        ?.firstOrNull { it.id == videoId }
        ?.playbackIdentity
}
