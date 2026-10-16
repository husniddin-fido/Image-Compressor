package uz.husniddin.imagecompressor.data.preferences

import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uz.husniddin.imagecompressor.di.ApplicationScope
import uz.husniddin.imagecompressor.domain.model.AppSettings
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.domain.model.ThemeMode

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)

    /**
     * Settings kept in memory for the whole app session. DataStore is read once at startup
     * (the theme needs it anyway), so screens opened later get the values immediately instead
     * of waiting for another disk read. Null only during the first few milliseconds after launch.
     */
    val current: StateFlow<AppSettings?> = settings.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setDefaultQuality(quality: Int) = dataStore.edit {
        it[QUALITY] = quality.coerceIn(ProcessingOptions.MIN_QUALITY, ProcessingOptions.MAX_QUALITY)
    }

    suspend fun setDefaultFormat(format: FormatOption) = dataStore.edit { it[FORMAT] = format.name }

    suspend fun setShareDiagnostics(enabled: Boolean) = dataStore.edit { it[DIAGNOSTICS] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME] = mode.name }

    suspend fun setSaveLocation(location: SaveLocation) = dataStore.edit {
        when (location) {
            SaveLocation.Pictures -> {
                it.remove(FOLDER_URI)
                it.remove(FOLDER_LABEL)
            }
            is SaveLocation.Folder -> {
                it[FOLDER_URI] = location.treeUri.toString()
                it[FOLDER_LABEL] = location.label
            }
        }
    }

    private fun toSettings(p: Preferences): AppSettings {
        val defaults = AppSettings()
        val folderUri = p[FOLDER_URI]
        return AppSettings(
            defaultQuality = p[QUALITY] ?: defaults.defaultQuality,
            defaultFormat = p[FORMAT]?.let { name -> FormatOption.entries.firstOrNull { it.name == name } } ?: defaults.defaultFormat,
            themeMode = p[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            shareDiagnostics = p[DIAGNOSTICS] ?: defaults.shareDiagnostics,
            saveLocation = if (folderUri != null) {
                SaveLocation.Folder(folderUri.toUri(), p[FOLDER_LABEL] ?: "Selected folder")
            } else {
                SaveLocation.Pictures
            },
        )
    }

    private companion object {
        val QUALITY = intPreferencesKey("default_quality")
        val FORMAT = stringPreferencesKey("default_format")
        val THEME = stringPreferencesKey("theme_mode")
        val DIAGNOSTICS = booleanPreferencesKey("share_diagnostics")
        val FOLDER_URI = stringPreferencesKey("save_folder_uri")
        val FOLDER_LABEL = stringPreferencesKey("save_folder_label")
    }
}
