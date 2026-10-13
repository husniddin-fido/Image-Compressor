package uz.husniddin.imagecompressor.ui.processing

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import uz.husniddin.imagecompressor.data.processing.BatchProcessor
import uz.husniddin.imagecompressor.data.processing.BatchState
import javax.inject.Inject

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    private val batchProcessor: BatchProcessor,
) : ViewModel() {
    val state: StateFlow<BatchState?> = batchProcessor.state
    fun cancel() = batchProcessor.cancel()
}
