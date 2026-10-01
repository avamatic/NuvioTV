@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.playlist.PlaylistSourceError
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

/** Content & Discovery → Playlists: playlist source URLs and which of their playlists are shown. */
@Composable
fun PlaylistSourcesScreen(
    viewModel: PlaylistSourcesViewModel = hiltViewModel(),
    onBackPress: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sourceUrl by remember { mutableStateOf("") }

    BackHandler { onBackPress() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        delay(if (message.isError) 5000 else 3000)
        viewModel.clearMessage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.xl)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = NuvioTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.playlists_settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = NuvioTheme.colors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.playlists_settings_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            }

            item {
                AddSourceCard(
                    url = sourceUrl,
                    onUrlChange = { sourceUrl = it },
                    onConfirm = {
                        if (sourceUrl.isNotBlank()) {
                            viewModel.addSource(sourceUrl)
                            sourceUrl = ""
                        }
                    },
                    isLoading = uiState.isAdding
                )
            }

            item {
                Text(
                    text = stringResource(R.string.playlists_sources_section, uiState.sources.size),
                    style = MaterialTheme.typography.titleLarge,
                    color = NuvioTheme.colors.TextPrimary
                )
            }

            if (uiState.sources.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.playlists_sources_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioTheme.colors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = NuvioTheme.spacing.xl)
                    )
                }
            }

            uiState.sources.forEach { source ->
                item(key = "source_${source.url}") {
                    SourceCard(
                        source = source,
                        onToggleAll = { viewModel.setAllEnabled(source, it) },
                        onRefresh = viewModel::refresh,
                        onRemove = { viewModel.removeSource(source.url) }
                    )
                }
                items(source.playlists, key = { "playlist_${it.ref.key}" }) { playlist ->
                    PlaylistToggleCard(
                        playlist = playlist,
                        onToggle = { viewModel.setEnabled(playlist.ref, it) }
                    )
                }
            }
        }

        MessageOverlay(message = uiState.message?.let { stringResource(it.text, it.argument.orEmpty()) }, isError = uiState.message?.isError == true)
    }
}

@Composable
private fun AddSourceCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    isLoading: Boolean
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textFieldFocusRequester = remember { FocusRequester() }
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            textFieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val confirm = {
        onConfirm()
        isEditing = false
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(NuvioTheme.radii.md))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.playlists_sources_add),
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = stringResource(R.string.playlists_sources_add_hint),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The surface stays in the tree so D-pad focus is stable; selecting it starts editing.
                Surface(
                    onClick = { isEditing = true },
                    modifier = Modifier.weight(1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundElevated,
                        focusedContainerColor = NuvioTheme.colors.BackgroundElevated
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(
                            border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                        ),
                        focusedBorder = Border(
                            border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                        )
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Box(modifier = Modifier.padding(NuvioTheme.spacing.md)) {
                        BasicTextField(
                            value = url,
                            onValueChange = onUrlChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(textFieldFocusRequester)
                                .onFocusChanged {
                                    if (!it.isFocused && isEditing) {
                                        isEditing = false
                                        keyboardController?.hide()
                                    }
                                },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false
                            ),
                            keyboardActions = KeyboardActions(onDone = { confirm() }),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = NuvioTheme.colors.TextPrimary),
                            cursorBrush = SolidColor(if (isEditing) NuvioTheme.colors.Primary else Color.Transparent),
                            decorationBox = { innerTextField ->
                                if (url.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.playlists_sources_url_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NuvioTheme.colors.TextTertiary
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Button(
                    onClick = { confirm() },
                    enabled = !isLoading && url.isNotBlank(),
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Secondary,
                        focusedContainerColor = NuvioTheme.colors.SecondaryVariant,
                        contentColor = NuvioTheme.colors.OnSecondary,
                        focusedContentColor = NuvioTheme.colors.OnSecondaryVariant
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                            shape = RoundedCornerShape(50)
                        )
                    )
                ) {
                    if (isLoading) {
                        LoadingIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                    Text(stringResource(R.string.playlists_sources_add_btn))
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: PlaylistSourceRow,
    onToggleAll: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit
) {
    var toggleFocused by remember { mutableStateOf(false) }
    var refreshFocused by remember { mutableStateOf(false) }
    var removeFocused by remember { mutableStateOf(false) }
    val isCardFocused = toggleFocused || refreshFocused || removeFocused
    val borderColor by animateColorAsState(
        targetValue = if (isCardFocused) NuvioTheme.colors.FocusRing else Color.Transparent,
        label = "playlistSourceBorder"
    )
    val enabledCount = source.playlists.count { it.enabled && it.supported }
    val anyEnabled = enabledCount > 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(18.dp))
            .border(
                width = if (isCardFocused) NuvioTheme.spacing.xxs else NuvioTheme.spacing.none,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NuvioTheme.spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioTheme.colors.TextPrimary
                )
                Text(
                    text = source.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(NuvioTheme.spacing.xs))
                val status = when {
                    source.loading && source.playlists.isEmpty() -> null
                    source.stale -> stringResource(R.string.playlists_sources_stale)
                    source.error == PlaylistSourceError.NOT_AN_INDEX -> stringResource(R.string.playlists_sources_error_not_index)
                    source.error != null -> stringResource(R.string.playlists_sources_error_unreachable)
                    else -> stringResource(R.string.playlists_sources_count, source.playlists.size)
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (source.error != null) NuvioTheme.colors.Error else NuvioTheme.colors.TextSecondary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (source.loading) LoadingIndicator(modifier = Modifier.size(20.dp))
                if (source.playlists.isNotEmpty()) {
                    Surface(
                        onClick = { onToggleAll(!anyEnabled) },
                        modifier = Modifier.onFocusChanged { toggleFocused = it.isFocused },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = NuvioTheme.colors.Surface,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.playlists_sources_enabled_count, enabledCount, source.playlists.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (anyEnabled) NuvioTheme.colors.Secondary else NuvioTheme.colors.TextSecondary
                            )
                            Switch(
                                checked = anyEnabled,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NuvioTheme.colors.Secondary,
                                    checkedTrackColor = NuvioTheme.colors.Secondary.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
                IconAction(
                    onClick = onRefresh,
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh),
                    focusedTint = NuvioTheme.colors.Primary,
                    onFocusChange = { refreshFocused = it }
                )
                IconAction(
                    onClick = onRemove,
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_remove),
                    focusedTint = NuvioTheme.colors.Error,
                    onFocusChange = { removeFocused = it }
                )
            }
        }
    }
}

@Composable
private fun IconAction(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    focusedTint: Color,
    onFocusChange: (Boolean) -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { onFocusChange(it.isFocused) },
        colors = ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.Surface,
            contentColor = NuvioTheme.colors.TextSecondary,
            focusedContainerColor = NuvioTheme.colors.FocusBackground,
            focusedContentColor = focusedTint
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun PlaylistToggleCard(
    playlist: PlaylistToggleRow,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { if (playlist.supported) onToggle(!playlist.enabled) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NuvioTheme.spacing.xl),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            )
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTheme.spacing.lg, vertical = NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = NuvioTheme.colors.TextPrimary
                )
                val detail = when {
                    !playlist.supported -> stringResource(R.string.playlists_sources_needs_update)
                    playlist.entryCount != null -> stringResource(R.string.playlists_entries_count, playlist.entryCount)
                    else -> null
                }
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            }
            Switch(
                checked = playlist.enabled && playlist.supported,
                onCheckedChange = null,
                enabled = playlist.supported,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NuvioTheme.colors.Secondary,
                    checkedTrackColor = NuvioTheme.colors.Secondary.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun MessageOverlay(message: String?, isError: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(NuvioTheme.spacing.xl),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .background(
                        color = if (isError) Color(0xFFC62828).copy(alpha = 0.9f) else Color(0xFF2E7D32).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(NuvioTheme.radii.md)
                    )
                    .padding(horizontal = 20.dp, vertical = NuvioTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
            ) {
                Icon(
                    imageVector = if (isError) Icons.Default.Close else Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White
                )
                Text(text = message.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
    }
}
