package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.playlist.PlaylistEntry
import com.nuvio.tv.core.playlist.PlaylistPlaybackSession
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

/**
 * The episodes panel while playing from a playlist: the playlist's sections as season tabs and
 * its entries as episodes, opening on the entry playing now.
 */
@Composable
internal fun PlaylistEntriesListView(
    position: PlaylistPlaybackSession.Position,
    watchedKeys: Set<String>,
    blurUnwatched: Boolean,
    episodesFocusRequester: FocusRequester,
    onEntrySelected: (PlaylistEntry) -> Unit
) {
    val sections = position.playlist.sections
    val currentKey = position.entry.key
    val currentSection = remember(sections, currentKey) {
        (sections.indexOfFirst { section -> section.entries.any { it.key == currentKey } } + 1).coerceAtLeast(1)
    }
    var selectedSection by remember(position.playlist.ref, currentSection) { mutableIntStateOf(currentSection) }
    val sectionNumbers = remember(sections.size) { (1..sections.size).toList() }
    val entries = sections.getOrNull(selectedSection - 1)?.entries.orEmpty()
    val focusIndex = entries.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val sectionTabFocusRequester = remember { FocusRequester() }
    val hasTabs = sections.size > 1
    val movieLabel = stringResource(R.string.playlist_entry_movie)

    LaunchedEffect(selectedSection, entries) {
        if (entries.isEmpty()) return@LaunchedEffect
        runCatching {
            listState.scrollToItem(focusIndex)
            delay(32)
            episodesFocusRequester.requestFocus()
        }
    }

    Column(modifier = Modifier.fillMaxHeight()) {
        if (hasTabs) {
            EpisodesSeasonTabs(
                seasons = sectionNumbers,
                selectedSeason = selectedSection,
                selectedTabFocusRequester = sectionTabFocusRequester,
                onSeasonSelected = { selectedSection = it },
                seasonLabel = { number ->
                    sections.getOrNull(number - 1)?.title ?: stringResource(R.string.playlist_section_part, number)
                }
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            contentPadding = PaddingValues(top = NuvioTheme.spacing.xs),
            modifier = Modifier
                .fillMaxHeight()
                .then(if (hasTabs) Modifier.focusProperties { up = sectionTabFocusRequester } else Modifier)
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                EpisodeItem(
                    episode = entry.toPanelVideo(),
                    isCurrent = entry.key == currentKey,
                    isWatched = entry.watchedKey in watchedKeys,
                    blurUnwatched = blurUnwatched,
                    focusRequester = episodesFocusRequester,
                    requestInitialFocus = index == focusIndex,
                    availableSeasons = if (hasTabs) sectionNumbers else emptyList(),
                    currentSeason = selectedSection,
                    onSeasonNavigate = { selectedSection = it },
                    badgeLabel = when {
                        entry.isEpisode -> "S${entry.season}E${entry.episode}"
                        entry.isMovie -> movieLabel
                        else -> entry.type
                    },
                    subtitle = entry.show?.takeIf { entry.isEpisode },
                    onClick = { onEntrySelected(entry) }
                )
            }
        }
    }
}

private fun PlaylistEntry.toPanelVideo() = Video(
    id = key,
    title = title ?: show ?: id,
    released = released,
    thumbnail = thumbnail,
    season = season,
    episode = episode,
    overview = overview,
    runtime = runtime
)
