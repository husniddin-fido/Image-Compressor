package uz.husniddin.imagecompressor.ui.selection

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uz.husniddin.imagecompressor.data.session.SelectionItem
import uz.husniddin.imagecompressor.data.session.SelectionRepository
import javax.inject.Inject

data class SelectionUiState(
    val items: List<SelectionItem> = emptyList(),
) {
    val readyCount: Int get() = items.count { it is SelectionItem.Ready }
    val failedCount: Int get() = items.count { it is SelectionItem.Failed }
    val isLoading: Boolean get() = items.any { it is SelectionItem.Loading }
    val totalBytes: Long get() = items.filterIsInstance<SelectionItem.Ready>().sumOf { it.image.sizeBytes }
    val canContinue: Boolean get() = readyCount > 0 && !isLoading
}

@HiltViewModel
class SelectionViewModel @Inject constructor(
    private val selection: SelectionRepository,
) : ViewModel() {

    val state: StateFlow<SelectionUiState> = selection.items
        .map { SelectionUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SelectionUiState(selection.items.value))

    fun add(uris: List<Uri>) = selection.add(uris)
    fun remove(id: String) = selection.remove(id)
    fun removeFailed() = state.value.items.filterIsInstance<SelectionItem.Failed>().forEach { selection.remove(it.id) }
}
