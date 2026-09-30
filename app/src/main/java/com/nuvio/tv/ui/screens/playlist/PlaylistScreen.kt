@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.playlist.PlaylistEntry
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * A playlist: a header on the left (artwork, description, counts, Continue / Start from beginning)
 * and the numbered entries on the right. Opening an entry plays the real title.
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
    if (playlist == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                uiState.isLoading -> LoadingIndicator()
                else -> Text(
                    text = stringResource(R.string.playlist_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioTheme.colors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxxl)
                )
            }
        }
        return
    }

    val entries = playlist.entries
    val listState = rememberLazyListState()
    val continueFocusRequester = remember { FocusRequester() }

    // Land on the entry "Continue" would open, and focus the Continue button.
    LaunchedEffect(playlist.id) {
        val index = uiState.continueIndex
        if (index > 0) listState.scrollToItem(index)
        runCatching { continueFocusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xl)
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            if (!playlist.image.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.image,
                    contentDescription = playlist.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineMedium,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!playlist.description.isNullOrBlank()) {
                Text(
                    text = playlist.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(
                    R.string.playlist_counts,
                    entries.size,
                    uiState.movieCount,
                    uiState.episodeCount
                ),
                style = MaterialTheme.typography.labelMedium,
                color = NuvioTheme.colors.TextSecondary
            )
            Text(
                text = stringResource(R.string.playlist_progress, uiState.watchedCount, entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = NuvioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xs))
            Button(
                onClick = {
                    entries.getOrNull(uiState.continueIndex)?.let { onPlayEntry(it, playlist.name) }
                },
                enabled = uiState.continueIndex >= 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(continueFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.playlist_action_continue))
            }
            Button(
                onClick = { entries.firstOrNull()?.let { onPlayEntry(it, playlist.name) } },
                enabled = entries.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundElevated,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.playlist_action_start_over))
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.playlist_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioTheme.colors.TextSecondary
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = NuvioTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
            ) {
                itemsIndexed(
                    items = entries,
                    key = { _, entry -> entry.key },
                    contentType = { _, _ -> "playlist_entry" }
                ) { index, entry ->
                    PlaylistEntryRow(
                        position = index + 1,
                        entry = entry,
                        watched = entry.watchedKey in uiState.watchedKeys,
                        onClick = { onPlayEntry(entry, playlist.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistEntryRow(
    position: Int,
    entry: PlaylistEntry,
    watched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.BackgroundElevated
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                shape = shape
            ),
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = shape
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextSecondary,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(52.dp)
            )
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (!entry.image.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                val label = if (entry.isMovie || entry.season == null || entry.episode == null) {
                    stringResource(R.string.playlist_entry_movie)
                } else {
                    val episodeLabel = stringResource(
                        R.string.playlist_entry_season_episode,
                        entry.season,
                        entry.episode
                    )
                    if (entry.show.isNullOrBlank()) episodeLabel else "${entry.show} · $episodeLabel"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.title ?: entry.show ?: entry.id,
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioTheme.colors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (watched) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.playlist_entry_watched),
                    tint = NuvioTheme.colors.Success,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
