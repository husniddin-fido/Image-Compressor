package uz.husniddin.imagecompressor.ui.comparison

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.husniddin.imagecompressor.data.output.ResultSaver
import uz.husniddin.imagecompressor.data.output.SaveDestination
import uz.husniddin.imagecompressor.data.preferences.SettingsRepository
import uz.husniddin.imagecompressor.data.processing.BatchItem
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.data.processing.BatchProcessor
import uz.husniddin.imagecompressor.image.ImageProcessingException
import uz.husniddin.imagecompressor.navigation.ComparisonRoute
import uz.husniddin.imagecompressor.ui.components.UiMessage
import javax.inject.Inject

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    batchProcessor: BatchProcessor,
    private val resultSaver: ResultSaver,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val saveLocation: StateFlow<SaveLocation> = settingsRepository.settings
        .map { it.saveLocation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveLocation.Pictures)

    private val itemId = savedStateHandle.toRoute<ComparisonRoute>().itemId

    val item: StateFlow<BatchItem?> = batchProcessor.state
        .map { state -> state?.items?.firstOrNull { it.id == itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), batchProcessor.state.value?.items?.firstOrNull { it.id == itemId })

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun save(destination: SaveDestination, fileName: String?) {
        val current = item.value ?: return
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            try {
                val location = resultSaver.save(current, destination, fileName)
                _messages.send(UiMessage("Saved as ${location.displayName}"))
            } catch (e: ImageProcessingException) {
                _messages.send(UiMessage(e.message ?: "The image couldn't be saved."))
            } finally {
                _saving.value = false
            }
        }
    }

    fun saveToDefault(fileName: String) = save(SaveDestination.from(saveLocation.value), fileName)

    fun showMessage(text: String) {
        viewModelScope.launch { _messages.send(UiMessage(text)) }
    }
}
