package com.nuvio.tv.core.playlist

/** Which entry "Continue" opens. */
object PlaylistSelection {
    /**
     * Picks up where the user last was, as a show does: the most recently watched entry if it is
     * unfinished, else the first unwatched entry after it. Without any activity (or past the
     * end), the first entry that is not watched, or the first entry when everything is watched
     * (so a finished playlist restarts). Null only for an empty playlist.
     *
     * [lastWatched] is when the entry's title was last watched (0 if never); [inProgress] whether
     * it has an unfinished position.
     */
    fun continueTarget(
        entries: List<PlaylistEntry>,
        watchedKeys: Set<String>,
        lastWatched: (PlaylistEntry) -> Long = { 0L },
        inProgress: (PlaylistEntry) -> Boolean = { false }
    ): PlaylistEntry? {
        var recentIndex = -1
        var recentAt = 0L
        entries.forEachIndexed { index, entry ->
            val at = lastWatched(entry)
            if (at > recentAt) {
                recentAt = at
                recentIndex = index
            }
        }
        if (recentIndex >= 0) {
            val recent = entries[recentIndex]
            if (inProgress(recent) && recent.watchedKey !in watchedKeys) return recent
            (recentIndex + 1 until entries.size).firstOrNull { entries[it].watchedKey !in watchedKeys }
                ?.let { return entries[it] }
        }
        return entries.firstOrNull { it.watchedKey !in watchedKeys } ?: entries.firstOrNull()
    }

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
