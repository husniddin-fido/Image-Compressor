package uz.husniddin.imagecompressor.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import uz.husniddin.imagecompressor.data.history.HistoryRepository
import uz.husniddin.imagecompressor.data.processing.BatchProcessor
import uz.husniddin.imagecompressor.data.session.SelectionRepository
import uz.husniddin.imagecompressor.ui.history.HistoryItemUi
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val selection: SelectionRepository,
    private val batchProcessor: BatchProcessor,
    history: HistoryRepository,
) : ViewModel() {

    /** Null while loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recent: StateFlow<List<HistoryItemUi>?> = history.recent(RECENT_COUNT)
        .mapLatest { entries -> entries.map { HistoryItemUi(it, history.exists(it.outputUri)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Picking from Home always starts a fresh session (and frees the previous one's temp files). */
    fun startSession(uris: List<Uri>) {
        batchProcessor.clear()
        selection.replaceWith(uris)
    }

    private companion object {
        const val RECENT_COUNT = 3
    }
}
