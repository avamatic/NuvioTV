package com.nuvio.tv.core.playlist

import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.PosterShape

/** Id of the synthetic Home collection that carries the playlist cards. */
const val PLAYLISTS_HOME_COLLECTION_ID = "chronio_playlists"

/**
 * The playlists as a Home row. It reuses the collection row so it shows up in every Home layout;
 * each folder id is a playlist id and opening one is routed to the playlist screen by the caller.
 * Returns null when there is nothing to show.
 */
fun List<PlaylistSummary>.toHomeCollection(title: String): Collection? {
    if (isEmpty()) return null
    return Collection(
        id = PLAYLISTS_HOME_COLLECTION_ID,
        title = title,
        pinToTop = true,
        focusGlowEnabled = false,
        folders = map { summary ->
            CollectionFolder(
                id = summary.id,
                title = summary.name,
                coverImageUrl = summary.image,
                tileShape = PosterShape.LANDSCAPE
            )
        }
    )
}
