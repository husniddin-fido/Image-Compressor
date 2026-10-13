package uz.husniddin.imagecompressor.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Metadata about a saved result. The image itself lives at [outputUri]; nothing is duplicated. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val outputName: String,
    val outputUri: String,
    val locationLabel: String,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val width: Int,
    val height: Int,
    val formatLabel: String,
    val mimeType: String,
    val settingsSummary: String,
    val createdAt: Long,
)
