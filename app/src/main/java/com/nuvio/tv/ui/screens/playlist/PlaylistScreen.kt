@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.playlist.Playlist
import com.nuvio.tv.core.playlist.PlaylistEntry
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.screens.detail.ActionIconButton
import com.nuvio.tv.ui.screens.detail.EpisodesRow
import com.nuvio.tv.ui.screens.detail.PlayButton
import com.nuvio.tv.ui.screens.detail.SeasonTabs
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * A playlist presented like a show: backdrop and hero with Resume/Play, its sections as season
 * tabs and its entries as episode cards, all built from the show page's own components. Cards
 * carry each entry's real watched state and progress.
 */
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel = hiltViewModel(),
    onPlayEntry: (entry: PlaylistEntry, playlistName: String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    val playlist = uiState.playlist
    if (playlist == null || !playlist.supported) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                playlist != null -> CenteredMessage(stringResource(R.string.playlist_needs_update))
                uiState.isLoading -> LoadingIndicator()
                else -> CenteredMessage(stringResource(R.string.playlist_load_failed))
            }
        }
        return
    }
    if (uiState.sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CenteredMessage(stringResource(R.string.playlist_empty))
        }
        return
    }

    val playEntry: (PlaylistEntry) -> Unit = { entry ->
        viewModel.startPlayback(entry)
        onPlayEntry(entry, playlist.name)
    }
    val playFocusRequester = remember { FocusRequester() }
    val tabsFocusRequester = remember { FocusRequester() }
    val showTabs = uiState.sections.size > 1
    val section = uiState.sections.firstOrNull { it.number == uiState.selectedSection } ?: uiState.sections.first()
    val listState = rememberLazyListState()
    val movieLabel = stringResource(R.string.playlist_entry_movie)
    var focusedOnce by remember(playlist.ref) { mutableStateOf(false) }

    LaunchedEffect(playlist.ref) {
        if (!focusedOnce) {
            runCatching { playFocusRequester.requestFocus() }
            focusedOnce = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        PlaylistBackdrop(playlist)

        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            item(key = "hero") {
                PlaylistHero(
                    playlist = playlist,
                    uiState = uiState,
                    playFocusRequester = playFocusRequester,
                    onPlay = { uiState.continueEntry?.let(playEntry) },
                    onStartOver = { playlist.entries.firstOrNull()?.let(playEntry) }
                )
            }

            if (showTabs) {
                item(key = "sections") {
                    SeasonTabs(
                        seasons = uiState.sections.map { it.number },
                        selectedSeason = section.number,
                        onSeasonSelected = viewModel::selectSection,
                        selectedTabFocusRequester = tabsFocusRequester,
                        upFocusRequester = playFocusRequester,
                        seasonLabel = { number ->
                            uiState.sections.firstOrNull { it.number == number }?.title
                                ?: stringResource(R.string.playlist_section_part, number)
                        }
                    )
                }
            }

            item(key = "entries") {
                EpisodesRow(
                    episodes = section.videos,
                    episodeProgressMap = uiState.progress,
                    watchedEpisodes = uiState.watched,
                    onEpisodeClick = { video -> uiState.entryFor(video)?.let(playEntry) },
                    onToggleEpisodeWatched = { video ->
                        val entry = uiState.entryFor(video) ?: return@EpisodesRow
                        val coordinate = video.season?.let { s -> video.episode?.let { e -> s to e } }
                        viewModel.setWatched(entry, coordinate !in uiState.watched)
                    },
                    onMarkSeasonWatched = { viewModel.setSectionWatched(it, true) },
                    onMarkSeasonUnwatched = { viewModel.setSectionWatched(it, false) },
                    isSeasonFullyWatched = section.videos.all { video ->
                        video.season?.let { s -> video.episode?.let { e -> (s to e) in uiState.watched } } == true
                    },
                    selectedSeason = section.number,
                    onMarkPreviousEpisodesWatched = { video -> uiState.entryFor(video)?.let(viewModel::markPreviousWatched) },
                    upFocusRequester = if (showTabs) tabsFocusRequester else playFocusRequester,
                    scrollToEpisodeId = uiState.continueVideoId?.takeIf { id -> section.videos.any { it.id == id } },
                    episodeBadgeLabel = { video -> uiState.entryFor(video)?.let { entryBadge(it, movieLabel) } }
                )
            }

            item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxl)) }
        }
    }
}

@Composable
private fun PlaylistBackdrop(playlist: Playlist) {
    val background = NuvioTheme.colors.Background
    val image = playlist.background ?: playlist.poster
    Box(modifier = Modifier.fillMaxSize()) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background,
                        0.35f to background.copy(alpha = 0.8f),
                        0.7f to background.copy(alpha = 0.2f),
                        1f to background.copy(alpha = 0f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0f),
                        0.4f to background.copy(alpha = 0.1f),
                        0.75f to background.copy(alpha = 0.85f),
                        1f to background
                    )
                )
        )
    }
}

@Composable
private fun PlaylistHero(
    playlist: Playlist,
    uiState: PlaylistUiState,
    playFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onStartOver: () -> Unit
) {
    var logoFailed by remember(playlist.logo) { mutableStateOf(false) }
    val continueEntry = uiState.continueEntry
    val playLabel = continueEntry?.let { entry ->
        stringResource(
            if (uiState.continueIsResume) R.string.playlist_action_resume else R.string.playlist_action_play,
            entryShortLabel(entry)
        )
    }
    val counts = buildList {
        if (uiState.movieCount > 0) add(stringResource(R.string.playlist_count_movies, uiState.movieCount))
        if (uiState.episodeCount > 0) add(stringResource(R.string.playlist_count_episodes, uiState.episodeCount))
        if (uiState.totalRuntimeMinutes > 0) add(formatTotalRuntime(uiState.totalRuntimeMinutes))
        add(stringResource(R.string.playlist_progress, uiState.watchedCount, playlist.entries.size))
        if (playlist.skippedEntries > 0) add(stringResource(R.string.playlist_skipped, playlist.skippedEntries))
    }.joinToString("  ·  ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp)
            .padding(start = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.xxxl, bottom = NuvioTheme.spacing.lg),
        verticalArrangement = Arrangement.Bottom
    ) {
        if (!playlist.logo.isNullOrBlank() && !logoFailed) {
            AsyncImage(
                model = playlist.logo,
                contentDescription = playlist.name,
                onError = { logoFailed = true },
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth(0.4f)
                    .padding(bottom = NuvioTheme.spacing.lg)
            )
        } else {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.displayMedium,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayButton(
                text = playLabel,
                enabled = continueEntry != null,
                onClick = onPlay,
                focusRequester = playFocusRequester
            )
            ActionIconButton(
                icon = Icons.Default.Replay,
                contentDescription = stringResource(R.string.playlist_action_start_over),
                onClick = onStartOver
            )
        }

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.lg))

        playlist.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(bottom = NuvioTheme.spacing.md)
            )
        }

        Text(
            text = counts,
            style = MaterialTheme.typography.labelLarge,
            color = NuvioTheme.extendedColors.textSecondary
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = NuvioTheme.colors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxxl)
    )
}

/** Card badge: "Star Trek: Enterprise · S1E3" for episodes, [movieLabel] for movies, else the type. */
private fun entryBadge(entry: PlaylistEntry, movieLabel: String): String = when {
    entry.isEpisode -> listOfNotNull(entry.show, "S${entry.season}E${entry.episode}").joinToString(" · ")
    entry.isMovie -> movieLabel
    else -> entry.type
}

/** "S1E3 · Fight or Flight" for episodes, the title otherwise. */
private fun entryShortLabel(entry: PlaylistEntry): String =
    if (entry.isEpisode) {
        listOfNotNull("S${entry.season}E${entry.episode}", entry.title).joinToString(" · ")
    } else {
        entry.title ?: entry.show ?: entry.id
    }

private fun formatTotalRuntime(minutes: Int): String {
    val hours = minutes / 60
    return if (hours >= 48) "${hours / 24}d ${hours % 24}h" else "${hours}h ${minutes % 60}m"
}
