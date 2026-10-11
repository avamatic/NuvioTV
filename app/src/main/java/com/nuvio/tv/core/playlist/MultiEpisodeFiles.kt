package com.nuvio.tv.core.playlist

// "S01E01-E02", "S01E01E02", "S01E01-02"; a trailing number must not be a resolution ("-720p").
private val SEASON_EPISODE_RANGE = Regex(
    """(?i)(?<![a-z0-9])s(\d{1,2})[ ._-]?e(\d{1,3})((?:[ ._]?-[ ._]?e?\d{1,3}(?![\dp])|[ ._]?e\d{1,3}(?![\dp]))+)"""
)
// "1x01-02", "1x01-1x02".
private val CROSS_RANGE = Regex(
    """(?i)(?<![a-z0-9])(\d{1,2})x(\d{2,3})(?!\d)((?:[ ._]?-[ ._]?(?:\d{1,2}x)?\d{2,3}(?![\dp]))+)"""
)
private const val MAX_EXTRA_EPISODES = 3

/**
 * Later episodes of [season] that a release file also contains when its name is a range starting
 * at [episode] ("S01E01-E02", "S01E01E02", "1x01-02"), as merged two-part pilots and finales
 * often are. Empty for a single-episode file, an unknown name, or a range starting elsewhere.
 */
fun extraEpisodesInFile(filename: String?, season: Int?, episode: Int?): IntRange {
    if (filename.isNullOrBlank() || season == null || episode == null) return IntRange.EMPTY
    val match = SEASON_EPISODE_RANGE.find(filename) ?: CROSS_RANGE.find(filename) ?: return IntRange.EMPTY
    val (fileSeason, first, rest) = match.destructured
    if (fileSeason.toInt() != season || first.toInt() != episode) return IntRange.EMPTY
    val last = Regex("""\d+""").findAll(rest).last().value.toInt()
    if (last <= episode || last - episode > MAX_EXTRA_EPISODES) return IntRange.EMPTY
    return episode + 1..last
}
