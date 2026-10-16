package uz.husniddin.imagecompressor.ui.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import uz.husniddin.imagecompressor.data.history.HistoryRepository
import uz.husniddin.imagecompressor.data.preferences.SettingsRepository
import uz.husniddin.imagecompressor.domain.model.AppSettings
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.domain.model.ThemeMode
import uz.husniddin.imagecompressor.ui.components.UiMessage
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val history: HistoryRepository,
    private val resolver: ContentResolver,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.current

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setShareDiagnostics(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShareDiagnostics(enabled) }
    fun setDefaultQuality(q: Int) = viewModelScope.launch { settingsRepository.setDefaultQuality(q) }
    fun setDefaultFormat(f: FormatOption) = viewModelScope.launch { settingsRepository.setDefaultFormat(f) }
    fun usePictures() = viewModelScope.launch {
        releaseFolderPermission()
        settingsRepository.setSaveLocation(SaveLocation.Pictures)
    }

    fun useFolder(treeUri: Uri) = viewModelScope.launch {
        try {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            _messages.send(UiMessage("That folder can't be used for saving. Please choose another one."))
            return@launch
        }
        releaseFolderPermission(except = treeUri)
        settingsRepository.setSaveLocation(SaveLocation.Folder(treeUri, folderLabel(treeUri)))
    }

    fun clearHistory() = viewModelScope.launch {
        history.clear()
        _messages.send(UiMessage("History cleared. Your saved images were not deleted."))
    }

    private fun releaseFolderPermission(except: Uri? = null) {
        val current = (settings.value?.saveLocation as? SaveLocation.Folder)?.treeUri ?: return
        if (current == except) return
        runCatching {
            resolver.releasePersistableUriPermission(current, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun folderLabel(treeUri: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        resolver.query(doc, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "Selected folder"
}
