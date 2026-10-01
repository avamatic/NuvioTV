package com.nuvio.tv.core.playlist

/** Which entry "Continue" opens. */
object PlaylistSelection {
    /**
     * The first entry that is not watched, or the first entry when everything is watched (so a
     * finished playlist restarts). Null only for an empty playlist.
     */
    fun continueTarget(entries: List<PlaylistEntry>, watchedKeys: Set<String>): PlaylistEntry? =
        entries.firstOrNull { it.watchedKey !in watchedKeys } ?: entries.firstOrNull()

    /** Number of entries whose real title is marked watched. */
    fun watchedCount(entries: List<PlaylistEntry>, watchedKeys: Set<String>): Int =
        entries.count { it.watchedKey in watchedKeys }

    /**
     * Index of the entry that plays [contentId] ([season]/[episode] for episodes), searching
     * forward from [fromIndex] so repeated titles resolve to the next occurrence. Null when the
     * title does not appear at or after [fromIndex].
     */
    fun locate(
        entries: List<PlaylistEntry>,
        fromIndex: Int,
        contentId: String?,
        season: Int?,
        episode: Int?
    ): Int? {
        if (contentId.isNullOrBlank()) return null
        for (index in fromIndex.coerceAtLeast(0) until entries.size) {
            val entry = entries[index]
            if (entry.id != contentId) continue
            if (!entry.isEpisode || (entry.season == season && entry.episode == episode)) return index
        }
        return null
    }
}
