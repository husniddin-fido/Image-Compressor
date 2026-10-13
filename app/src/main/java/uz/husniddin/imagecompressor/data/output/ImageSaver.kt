package uz.husniddin.imagecompressor.data.output

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import uz.husniddin.imagecompressor.data.processing.SavedLocation
import uz.husniddin.imagecompressor.di.IoDispatcher
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.image.ImageWriteException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface SaveDestination {
    /** Pictures/Image Compressor via MediaStore. */
    data object Pictures : SaveDestination
    /** A file the user created with the system "Save as" dialog. */
    data class Document(val uri: Uri) : SaveDestination
    /** A folder the user granted access to (OpenDocumentTree). */
    data class Folder(val treeUri: Uri) : SaveDestination

    companion object {
        fun from(location: SaveLocation): SaveDestination = when (location) {
            SaveLocation.Pictures -> Pictures
            is SaveLocation.Folder -> Folder(location.treeUri)
        }
    }
}

/** Copies processed outputs to permanent, user-visible locations. Never overwrites existing files. */
@Singleton
class ImageSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun save(file: File, fileName: String, mimeType: String, destination: SaveDestination): SavedLocation =
        withContext(io) {
            if (!file.exists()) throw ImageWriteException("The processed file is no longer available. Please process the image again.")
            try {
                when (destination) {
                    SaveDestination.Pictures ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveMediaStore(file, fileName, mimeType) else saveLegacyPictures(file, fileName)
                    is SaveDestination.Document -> saveToDocument(file, destination.uri)
                    is SaveDestination.Folder -> saveToFolder(file, fileName, mimeType, destination.treeUri)
                }
            } catch (e: ImageWriteException) {
                throw e
            } catch (e: SecurityException) {
                val hint = if (destination is SaveDestination.Folder) " Choose the folder again in Settings." else ""
                throw ImageWriteException("Permission to write to that location was denied.$hint", e)
            } catch (e: FileNotFoundException) {
                throw ImageWriteException("The destination couldn't be opened.", e)
            } catch (e: IOException) {
                throw ImageWriteException(writeErrorMessage(e), e)
            } catch (e: IllegalArgumentException) {
                throw ImageWriteException("That location can't be written to.", e)
            } catch (e: IllegalStateException) {
                throw ImageWriteException("Saving failed. The storage may be unavailable.", e)
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveMediaStore(file: File, fileName: String, mimeType: String): SavedLocation {
        ensureSpace(Environment.getExternalStorageDirectory(), file.length())
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw ImageWriteException("The gallery refused the new file.")
        try {
            copy(file, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        // MediaStore renames duplicates (e.g. "name (1).jpg"); report the actual name.
        val actual = queryDisplayName(uri) ?: fileName
        return SavedLocation(uri, actual, "Pictures/$FOLDER")
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacyPictures(file: File, fileName: String): SavedLocation {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw ImageWriteException("Couldn't create the Pictures/$FOLDER folder.")
        ensureSpace(dir, file.length())
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "jpg")
        val target = OutputStorage.uniqueFile(dir, base, ext)
        try {
            file.copyTo(target, overwrite = false)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        val uri = suspendCancellableCoroutine<Uri?> { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null) { _, scanned -> cont.resume(scanned) }
        } ?: throw ImageWriteException("The file was written but couldn't be added to the gallery.")
        return SavedLocation(uri, target.name, "Pictures/$FOLDER")
    }

    private fun saveToDocument(file: File, uri: Uri): SavedLocation {
        copy(file, uri)
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "image"
        return SavedLocation(uri, name, "Chosen location")
    }

    private fun saveToFolder(file: File, fileName: String, mimeType: String, treeUri: Uri): SavedLocation {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val created = DocumentsContract.createDocument(resolver, parent, mimeType, fileName)
            ?: throw ImageWriteException("The selected folder refused the new file.")
        try {
            copy(file, created)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw e
        }
        val folderName = queryDisplayName(parent) ?: "Selected folder"
        return SavedLocation(created, queryDisplayName(created) ?: fileName, folderName)
    }

    /** Copies and then verifies the byte count, so success is only reported for complete files. */
    private fun copy(file: File, target: Uri) {
        val written = resolver.openOutputStream(target, "wt")?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: throw ImageWriteException("The destination couldn't be opened for writing.")
        if (written != file.length()) throw ImageWriteException("The file was only partially written.")
    }

    private fun ensureSpace(dir: File, bytes: Long) {
        val available = runCatching { StatFs(dir.path).availableBytes }.getOrNull() ?: return
        if (available < bytes + SPACE_MARGIN) throw ImageWriteException("Not enough storage space to save this image.")
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    }.getOrNull()

    private fun writeErrorMessage(e: IOException): String {
        val msg = e.message.orEmpty()
        return if (msg.contains("ENOSPC") || msg.contains("No space", ignoreCase = true)) {
            "Not enough storage space to save this image."
        } else {
            "The image couldn't be saved."
        }
    }

    companion object {
        const val FOLDER = "Image Compressor"
        private const val SPACE_MARGIN = 2L * 1024 * 1024
    }
}
