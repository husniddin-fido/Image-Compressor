package uz.husniddin.imagecompressor.data.session

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.husniddin.imagecompressor.di.ApplicationScope
import uz.husniddin.imagecompressor.domain.model.SourceImage
import uz.husniddin.imagecompressor.image.ImageInspector
import uz.husniddin.imagecompressor.image.ImageProcessingException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SelectionItem {
    val id: String
    val uri: Uri

    data class Loading(override val uri: Uri) : SelectionItem {
        override val id: String get() = uri.toString()
    }

    data class Ready(val image: SourceImage) : SelectionItem {
        override val id: String get() = image.id
        override val uri: Uri get() = image.uri
    }

    data class Failed(override val uri: Uri, val message: String) : SelectionItem {
        override val id: String get() = uri.toString()
    }
}

/** Holds the images picked for the current compression session. */
@Singleton
class SelectionRepository @Inject constructor(
    private val inspector: ImageInspector,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<SelectionItem>>(emptyList())
    val items: StateFlow<List<SelectionItem>> = _items.asStateFlow()

    val readyImages: List<SourceImage>
        get() = _items.value.filterIsInstance<SelectionItem.Ready>().map { it.image }

    /** Adds new URIs (duplicates are ignored) and loads their metadata in the background. */
    fun add(uris: List<Uri>) {
        val existing = _items.value.map { it.id }.toSet()
        val fresh = uris.distinct().filter { it.toString() !in existing }
        if (fresh.isEmpty()) return
        _items.update { it + fresh.map { uri -> SelectionItem.Loading(uri) } }
        fresh.forEach { uri ->
            scope.launch {
                val result = try {
                    SelectionItem.Ready(inspector.inspect(uri))
                } catch (e: ImageProcessingException) {
                    SelectionItem.Failed(uri, e.message ?: "This image couldn't be read.")
                }
                // Only replace the item if it is still part of the selection.
                _items.update { list -> list.map { if (it.id == uri.toString()) result else it } }
            }
        }
    }

    fun replaceWith(uris: List<Uri>) {
        clear()
        add(uris)
    }

    fun remove(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
    }

    fun clear() {
        _items.value = emptyList()
    }
}
