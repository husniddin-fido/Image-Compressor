package uz.husniddin.imagecompressor.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

@Stable
class ImagePicker internal constructor(
    val pickMultiple: () -> Unit,
    val pickSingle: () -> Unit,
)

/**
 * Android Photo Picker (falls back to the system document picker on devices without it).
 * No storage permission is required. Cancellation yields no callback.
 */
@Composable
fun rememberImagePicker(onPicked: (List<Uri>) -> Unit): ImagePicker {
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(listOf(uri))
    }
    return remember(multiple, single) {
        ImagePicker(pickMultiple = { multiple.launch(request) }, pickSingle = { single.launch(request) })
    }
}
