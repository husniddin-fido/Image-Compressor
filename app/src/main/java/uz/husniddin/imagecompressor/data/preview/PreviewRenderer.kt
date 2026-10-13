package uz.husniddin.imagecompressor.data.preview

import uz.husniddin.imagecompressor.data.output.OutputStorage
import uz.husniddin.imagecompressor.data.processing.ImageEngine
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.SourceImage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders live previews with the *same* engine used for the final output, so the preview image
 * and its size are exactly what the user will get — not an estimate.
 */
@Singleton
class PreviewRenderer @Inject constructor(
    private val engine: ImageEngine,
    private val storage: OutputStorage,
) {
    suspend fun render(image: SourceImage, options: ProcessingOptions): ProcessedImage {
        val file = storage.newPreviewFile(options.format.resolve(image.mimeType))
        return engine.process(image, options, file)
    }

    fun clear() = storage.cleanPreviews()
}
