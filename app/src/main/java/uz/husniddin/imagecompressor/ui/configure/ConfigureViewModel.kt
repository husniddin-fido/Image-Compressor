package uz.husniddin.imagecompressor.ui.configure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.husniddin.imagecompressor.data.preferences.SettingsRepository
import uz.husniddin.imagecompressor.data.preview.PreviewRenderer
import uz.husniddin.imagecompressor.data.processing.BatchProcessor
import uz.husniddin.imagecompressor.data.session.SelectionRepository
import uz.husniddin.imagecompressor.domain.model.CompressionPreset
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.ResizeSpec
import uz.husniddin.imagecompressor.image.ImageProcessingException
import uz.husniddin.imagecompressor.telemetry.Events
import uz.husniddin.imagecompressor.telemetry.Telemetry

/** Live preview of one image at the current settings. */
sealed interface PreviewState {
    /** The last finished preview, kept on screen while a newer one renders. */
    val last: ProcessedImage?

    data class Rendering(override val last: ProcessedImage?) : PreviewState
    data class Ready(val result: ProcessedImage) : PreviewState {
        override val last: ProcessedImage get() = result
    }
    data class Failed(val message: String, override val last: ProcessedImage?) : PreviewState
}

@HiltViewModel
class ConfigureViewModel @Inject constructor(
    selection: SelectionRepository,
    private val batchProcessor: BatchProcessor,
    private val previewRenderer: PreviewRenderer,
    settingsRepository: SettingsRepository,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(selection))
    val state: StateFlow<ConfigureUiState> = _state.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, PreviewState>>(emptyMap())
    val previews: StateFlow<Map<String, PreviewState>> = _previews.asStateFlow()

    private var renderJob: Job? = null
    private var renderedOptions: ProcessingOptions? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            applyDefaults(settings.defaultQuality, settings.defaultFormat)
        }
        observeSettingsForPreview()
    }

    private fun initialState(selection: SelectionRepository): ConfigureUiState {
        val images = selection.readyImages
        val first = images.firstOrNull()
        return ConfigureUiState(
            images = images,
            widthText = first?.width?.toString() ?: "",
            heightText = first?.height?.toString() ?: "",
        )
    }

    @OptIn(FlowPreview::class)
    private fun observeSettingsForPreview() {
        viewModelScope.launch {
            _state
                .map { s -> if (s.canStart) s.toOptions() else null }
                .distinctUntilChanged()
                // Wait until a slider drag or typing settles before doing real encodes.
                .debounce(PREVIEW_DEBOUNCE_MS)
                .collect { options -> if (options != null) renderPreviews(options) }
        }
    }

    /** Re-renders every image with [options]. Any render in progress is cancelled first. */
    private fun renderPreviews(options: ProcessingOptions) {
        renderJob?.cancel()
        renderedOptions = null
        val images = _state.value.images
        _previews.update { current -> images.associate { it.id to PreviewState.Rendering(current[it.id]?.last) } }
        renderJob = viewModelScope.launch {
            // One image at a time, in display order, to bound memory and show the first result quickly.
            for (image in images) {
                val next = try {
                    PreviewState.Ready(previewRenderer.render(image, options))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ImageProcessingException) {
                    PreviewState.Failed(e.message ?: "Preview unavailable", _previews.value[image.id]?.last)
                } catch (e: OutOfMemoryError) {
                    PreviewState.Failed("Not enough memory for a preview", _previews.value[image.id]?.last)
                }
                val replaced = _previews.value[image.id]?.last
                _previews.update { it + (image.id to next) }
                if (next is PreviewState.Ready) replaced?.outputFile?.takeIf { it != next.result.outputFile }?.delete()
            }
            renderedOptions = options
        }
    }

    /** Called when the screen becomes visible again, e.g. after returning from Results. */
    fun refreshPreviewsIfNeeded() {
        val s = _state.value
        if (!s.canStart) return
        val options = s.toOptions()
        if (renderedOptions != options && renderJob?.isActive != true) renderPreviews(options)
    }

    fun applyDefaults(quality: Int, format: FormatOption) {
        _state.update { it.copy(quality = quality.coerceIn(ProcessingOptions.MIN_QUALITY, ProcessingOptions.MAX_QUALITY), format = format) }
    }

    fun setTab(tab: CompressionTab) = _state.update { it.copy(tab = tab) }
    fun setQuality(q: Int) = _state.update { it.copy(quality = q.coerceIn(ProcessingOptions.MIN_QUALITY, ProcessingOptions.MAX_QUALITY)) }
    fun setPreventLarger(prevent: Boolean) = _state.update { it.copy(preventLarger = prevent) }
    fun selectPreset(preset: CompressionPreset) = setQuality(preset.quality)
    fun setTargetText(text: String) = _state.update { it.copy(targetText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8)) }
    fun setTargetUnit(unit: SizeUnit) = _state.update { it.copy(targetUnit = unit) }
    fun setAllowDownscale(allow: Boolean) = _state.update { it.copy(allowDownscale = allow) }
    fun setResizeMode(mode: ResizeMode) = _state.update { it.copy(resizeMode = mode) }
    fun setPercent(p: Int) = _state.update { it.copy(percent = p.coerceIn(1, 100)) }
    fun setDimensionPreset(p: DimensionPreset) = _state.update { it.copy(preset = p) }
    fun setFormat(f: FormatOption) = _state.update { it.copy(format = f) }
    fun setBackground(b: BackgroundOption) = _state.update { it.copy(background = b) }

    fun setWidth(text: String) = _state.update { it.withWidthText(text) }
    fun setHeight(text: String) = _state.update { it.withHeightText(text) }
    fun setAspectLocked(locked: Boolean) = _state.update { it.withAspectLocked(locked) }

    /** Starts processing; returns false if the settings are invalid. */
    fun start(): Boolean {
        val s = _state.value
        if (!s.canStart) return false
        // Don't compete with the batch for CPU and memory; previews resume when the user returns.
        if (renderJob?.isActive == true) {
            renderJob?.cancel()
            renderedOptions = null
        }
        val options = s.toOptions()
        batchProcessor.start(s.images, options)
        telemetry.logEvent(
            Events.COMPRESSION_STARTED,
            mapOf(
                Events.P_IMAGE_COUNT to s.images.size,
                Events.P_MODE to s.tab.name.lowercase(),
                Events.P_FORMAT to s.format.name.lowercase(),
                Events.P_RESIZE to when (options.resize) {
                    ResizeSpec.None -> "none"
                    is ResizeSpec.Percentage -> "percent"
                    is ResizeSpec.FitWithin -> if (s.resizeMode == ResizeMode.PRESET) "preset" else "custom"
                    is ResizeSpec.Exact -> "exact"
                },
            ),
        )
        return true
    }

    override fun onCleared() {
        renderJob?.cancel()
        previewRenderer.clear()
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 300L
    }
}
