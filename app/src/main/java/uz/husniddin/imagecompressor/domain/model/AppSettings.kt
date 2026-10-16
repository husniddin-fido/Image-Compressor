package uz.husniddin.imagecompressor.domain.model

import android.net.Uri

sealed interface SaveLocation {
    val label: String

    data object Pictures : SaveLocation {
        override val label = "Pictures/Image Compressor"
    }

    /** A folder chosen with the system picker; access is persisted across restarts. */
    data class Folder(val treeUri: Uri, override val label: String) : SaveLocation
}

data class AppSettings(
    val defaultQuality: Int = CompressionPreset.BALANCED.quality,
    val defaultFormat: FormatOption = FormatOption.ORIGINAL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val saveLocation: SaveLocation = SaveLocation.Pictures,
    /** Anonymous usage statistics and crash reports (Firebase). Never includes images or file names. */
    val shareDiagnostics: Boolean = true,
)
