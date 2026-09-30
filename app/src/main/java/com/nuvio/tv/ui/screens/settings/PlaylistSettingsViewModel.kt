package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.playlist.PlaylistSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistSettingsViewModel @Inject constructor(
    private val dataStore: PlaylistSettingsDataStore
) : ViewModel() {

    val feedUrl: StateFlow<String> = dataStore.feedUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistSettingsDataStore.DEFAULT_FEED_URL)

    fun setFeedUrl(url: String) {
        viewModelScope.launch { dataStore.setFeedUrl(url) }
    }

    fun resetFeedUrl() {
        viewModelScope.launch { dataStore.resetFeedUrl() }
    }
}
