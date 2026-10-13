package uz.husniddin.imagecompressor.data.output

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Builds Sharesheet / viewer intents using content URIs only (never raw file paths). */
object ShareHelper {

    fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareIntent(context: Context, uris: List<Uri>, mimeType: String): Intent {
        require(uris.isNotEmpty())
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = mimeType
        send.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, if (uris.size == 1) "Share image" else "Share ${uris.size} images")
    }

    fun viewIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Common MIME type for a share, falling back to image/\* when mixed. */
    fun commonMimeType(types: List<String>): String = types.distinct().singleOrNull() ?: "image/*"
}
