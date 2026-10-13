package uz.husniddin.imagecompressor.data.preferences

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uz.husniddin.imagecompressor.domain.model.AppSettings
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.domain.model.ThemeMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Each call simulates a fresh app process reading the same file. */
    private fun <T> withRepository(file: File, block: suspend (SettingsRepository) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            runBlocking { block(SettingsRepository(store, scope)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaults_whenNothingStored() {
        val file = File(tmp.root, "a.preferences_pb")
        assertEquals(AppSettings(), withRepository(file) { it.settings.first() })
    }

    @Test
    fun values_persistAcrossInstances() {
        val file = File(tmp.root, "b.preferences_pb")
        withRepository(file) {
            it.setDefaultQuality(60)
            it.setDefaultFormat(FormatOption.WEBP)
            it.setThemeMode(ThemeMode.DARK)
            it.setSaveLocation(SaveLocation.Folder(Uri.parse("content://tree/abc"), "Downloads"))
        }
        // Wait for the first DataStore's scope to be cancelled before opening the file again.
        Thread.sleep(100)
        val restored = withRepository(file) { it.settings.first() }
        assertEquals(60, restored.defaultQuality)
        assertEquals(FormatOption.WEBP, restored.defaultFormat)
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals(SaveLocation.Folder(Uri.parse("content://tree/abc"), "Downloads"), restored.saveLocation)
    }

    @Test
    fun quality_isClampedToValidRange() {
        val file = File(tmp.root, "c.preferences_pb")
        val s = withRepository(file) {
            it.setDefaultQuality(500)
            it.settings.first()
        }
        assertEquals(100, s.defaultQuality)
    }

    @Test
    fun current_isPreloadedWithoutWaitingForASubscriber() {
        val file = File(tmp.root, "e.preferences_pb")
        withRepository(file) { it.setThemeMode(ThemeMode.DARK) }
        Thread.sleep(100)
        val loaded = withRepository(file) { repo ->
            // No collector here: the eager in-memory copy loads on its own.
            withTimeout(2_000) { repo.current.filterNotNull().first() }
        }
        assertEquals(ThemeMode.DARK, loaded.themeMode)
    }

    @Test
    fun switchingBackToPictures_clearsFolder() {
        val file = File(tmp.root, "d.preferences_pb")
        val s = withRepository(file) {
            it.setSaveLocation(SaveLocation.Folder(Uri.parse("content://tree/x"), "X"))
            it.setSaveLocation(SaveLocation.Pictures)
            it.settings.first()
        }
        assertEquals(SaveLocation.Pictures, s.saveLocation)
    }
}
