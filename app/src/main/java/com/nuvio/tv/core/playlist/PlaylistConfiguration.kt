package com.nuvio.tv.core.playlist

import kotlinx.serialization.Serializable

/** Account configuration only: cached content, playback place and title progress stay local. */
@Serializable
data class PlaylistConfiguration(
    val version: Int = 1,
    val sources: List<String> = emptyList(),
    val disabledPlaylists: List<DisabledPlaylist> = emptyList()
)

@Serializable
data class DisabledPlaylist(val sourceUrl: String, val id: String) {
    val ref: PlaylistRef get() = PlaylistRef(sourceUrl, id)
}

/** Merge independent edits against the last acknowledged snapshot. Local edits win conflicts. */
internal fun mergePlaylistConfiguration(
    base: PlaylistConfiguration?,
    local: PlaylistConfiguration,
    remote: PlaylistConfiguration
): PlaylistConfiguration {
    require(local.version == 1 && remote.version == 1 && (base == null || base.version == 1))
    val baseSources = base?.sources.orEmpty()
    val removed = baseSources.toSet() - local.sources.toSet()
    val added = local.sources.toSet() - baseSources.toSet()
    val sources = (remote.sources.filterNot { it in removed } + local.sources.filter { it in added }).distinct()
    val ordered = if (base != null && local.sources != baseSources) {
        local.sources.filter { it in sources } + sources.filterNot { it in local.sources }
    } else sources
    val baseDisabled = base?.disabledPlaylists.orEmpty().toSet()
    val disabled = (remote.disabledPlaylists.toSet() - (baseDisabled - local.disabledPlaylists.toSet()) +
        (local.disabledPlaylists.toSet() - baseDisabled))
        .filter { it.sourceUrl in ordered }
        .sortedWith(compareBy({ it.sourceUrl }, { it.id }))
    return PlaylistConfiguration(sources = ordered, disabledPlaylists = disabled)
}
