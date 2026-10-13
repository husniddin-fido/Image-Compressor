package uz.husniddin.imagecompressor.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.husniddin.imagecompressor.data.history.HistoryEntity
import uz.husniddin.imagecompressor.data.history.HistoryRepository
import uz.husniddin.imagecompressor.ui.components.UiMessage
import javax.inject.Inject

data class HistoryItemUi(val entry: HistoryEntity, val fileAvailable: Boolean)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Loaded(val items: List<HistoryItemUi>) : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HistoryUiState> = history.entries
        .mapLatest { entries -> HistoryUiState.Loaded(entries.map { HistoryItemUi(it, history.exists(it.outputUri)) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun remove(entry: HistoryEntity) = viewModelScope.launch {
        history.delete(entry.id)
        _messages.send(UiMessage("Removed from history. The image file was not deleted."))
    }

    fun clear() = viewModelScope.launch { history.clear() }

    fun showMessage(text: String) = viewModelScope.launch { _messages.send(UiMessage(text)) }
}
