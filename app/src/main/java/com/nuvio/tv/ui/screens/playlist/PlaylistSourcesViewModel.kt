package com.nuvio.tv.ui.screens.playlist

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.playlist.PlaylistRef
import com.nuvio.tv.core.playlist.PlaylistRepository
import com.nuvio.tv.core.playlist.PlaylistSettingsDataStore
import com.nuvio.tv.core.playlist.PlaylistSourceError
import com.nuvio.tv.core.playlist.PlaylistSourceException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PlaylistToggleRow(
    val ref: PlaylistRef,
    val name: String,
    val entryCount: Int?,
    val enabled: Boolean,
    val supported: Boolean
)

@Immutable
data class PlaylistSourceRow(
    val url: String,
    val name: String,
    val loading: Boolean,
    val error: PlaylistSourceError?,
    /** The last fetch failed but a saved copy is shown. */
    val stale: Boolean,
    val playlists: List<PlaylistToggleRow>
)

@Immutable
data class PlaylistSourcesMessage(val isError: Boolean, @StringRes val text: Int, val argument: String? = null)

@Immutable
data class PlaylistSourcesUiState(
    val sources: List<PlaylistSourceRow> = emptyList(),
    val isAdding: Boolean = false,
    val message: PlaylistSourcesMessage? = null
)

@HiltViewModel
class PlaylistSourcesViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val settings: PlaylistSettingsDataStore
) : ViewModel() {

    private data class Transient(val isAdding: Boolean = false, val message: PlaylistSourcesMessage? = null)

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<PlaylistSourcesUiState> =
        combine(repository.observeSources(), settings.disabledPlaylists, transient) { sources, disabled, state ->
            PlaylistSourcesUiState(
                sources = sources.map { source ->
                    PlaylistSourceRow(
                        url = source.url,
                        name = source.index?.name ?: source.url,
                        loading = source.loading,
                        error = source.error,
                        stale = source.error != null && source.index != null,
                        playlists = source.index?.playlists.orEmpty().map { summary ->
                            PlaylistToggleRow(
                                ref = summary.ref,
                                name = summary.name,
                                entryCount = summary.entryCount,
                                enabled = summary.ref.key !in disabled,
                                supported = summary.supported
                            )
                        }
                    )
                },
                isAdding = state.isAdding,
                message = state.message
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistSourcesUiState())

    fun addSource(input: String) {
        val url = normalizeSourceUrl(input) ?: return
        if (transient.value.isAdding) return
        transient.update { it.copy(isAdding = true, message = null) }
        viewModelScope.launch {
            val result = repository.loadIndex(url)
            val message = result.fold(
                onSuccess = { index ->
                    if (settings.addSource(url)) {
                        PlaylistSourcesMessage(false, R.string.playlists_sources_added, index.name ?: url)
                    } else {
                        PlaylistSourcesMessage(true, R.string.playlists_sources_duplicate)
                    }
                },
                onFailure = { error ->
                    val text = when ((error as? PlaylistSourceException)?.error) {
                        PlaylistSourceError.NOT_AN_INDEX -> R.string.playlists_sources_error_not_index
                        else -> R.string.playlists_sources_error_unreachable
                    }
                    PlaylistSourcesMessage(true, text)
                }
            )
            transient.update { it.copy(isAdding = false, message = message) }
        }
    }

    fun removeSource(url: String) {
        viewModelScope.launch { settings.removeSource(url) }
    }

    fun refresh() = repository.refresh()

    fun setEnabled(ref: PlaylistRef, enabled: Boolean) {
        viewModelScope.launch { settings.setPlaylistsEnabled(listOf(ref), enabled) }
    }

    fun setAllEnabled(source: PlaylistSourceRow, enabled: Boolean) {
        viewModelScope.launch { settings.setPlaylistsEnabled(source.playlists.map { it.ref }, enabled) }
    }

    fun clearMessage() = transient.update { it.copy(message = null) }

    companion object {
        /** Trims [input] and assumes https when no scheme is given. Null for blank input. */
        fun normalizeSourceUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            return if (trimmed.contains("://")) trimmed else "https://$trimmed"
        }
    }
}
