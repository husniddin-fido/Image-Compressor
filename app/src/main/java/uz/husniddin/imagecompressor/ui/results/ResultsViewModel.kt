package uz.husniddin.imagecompressor.ui.results

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import uz.husniddin.imagecompressor.data.output.ResultSaver
import uz.husniddin.imagecompressor.data.output.SaveDestination
import uz.husniddin.imagecompressor.data.preferences.SettingsRepository
import uz.husniddin.imagecompressor.data.processing.BatchProcessor
import uz.husniddin.imagecompressor.data.processing.BatchState
import uz.husniddin.imagecompressor.data.session.SelectionRepository
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.ui.components.UiMessage
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val batchProcessor: BatchProcessor,
    private val selection: SelectionRepository,
    private val resultSaver: ResultSaver,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<BatchState?> = batchProcessor.state

    val saveLocation: StateFlow<SaveLocation> = settingsRepository.settings
        .map { it.saveLocation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveLocation.Pictures)

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun retryFailed() = batchProcessor.retryFailed()

    fun saveAll(destination: SaveDestination) {
        val items = state.value?.succeeded?.filter { it.saved == null }.orEmpty()
        if (items.isEmpty() || _saving.value) return
        viewModelScope.launch {
            _saving.value = true
            val summary = resultSaver.saveAll(items, destination)
            _saving.value = false
            val where = summary.saved.firstOrNull()?.locationLabel
            val text = when {
                summary.failures.isEmpty() -> "Saved ${summary.saved.size} image(s) to $where"
                summary.saved.isEmpty() -> "Couldn't save: ${summary.failures.first()}"
                else -> "Saved ${summary.saved.size}, ${summary.failures.size} failed. ${summary.failures.first()}"
            }
            _messages.send(UiMessage(text))
        }
    }

    fun saveAllToDefault() = saveAll(SaveDestination.from(saveLocation.value))

    fun saveAllToFolder(treeUri: Uri) = saveAll(SaveDestination.Folder(treeUri))

    fun showMessage(text: String) {
        viewModelScope.launch { _messages.send(UiMessage(text)) }
    }

    /** Ends the session: temporary outputs are deleted (saved copies are untouched). */
    fun finishSession() {
        batchProcessor.clear()
        selection.clear()
    }
}
