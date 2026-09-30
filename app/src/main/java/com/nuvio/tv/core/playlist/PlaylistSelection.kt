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
}
