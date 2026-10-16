package uz.husniddin.imagecompressor.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uz.husniddin.imagecompressor.MainActivity
import uz.husniddin.imagecompressor.domain.model.CompressionMode
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.ResizeSpec
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.image.TestImages
import uz.husniddin.imagecompressor.ui.comparison.CompareSlider
import uz.husniddin.imagecompressor.ui.components.AppCard
import uz.husniddin.imagecompressor.ui.components.AppTopBar
import uz.husniddin.imagecompressor.ui.components.Banner
import uz.husniddin.imagecompressor.ui.components.BannerKind
import uz.husniddin.imagecompressor.ui.configure.ConfigureRoute
import uz.husniddin.imagecompressor.ui.results.ResultsScreen
import uz.husniddin.imagecompressor.ui.settings.SettingsRoute
import uz.husniddin.imagecompressor.ui.theme.AppTheme
import uz.husniddin.imagecompressor.ui.theme.ImageCompressorTheme
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CaptureScreenshotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun capture(name: String) {
        Thread.sleep(1500)
        val bitmap = uiAutomation.takeScreenshot()
        val dir = File(context.getExternalFilesDir(null), "screenshots_raw").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun captureAllFlows() {
        val activity = compose.activity
        val selection = activity.selectionRepository
        val processor = activity.batchProcessor

        // 1. Capture Home Screen
        compose.waitForIdle()
        capture("01_home_screen")

        // 2. Capture Settings Screen
        compose.setContent {
            ImageCompressorTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsRoute(onBack = {})
                }
            }
        }
        compose.waitForIdle()
        capture("05_settings_screen")

        // Create 2 realistic sample photos
        val cacheDir = context.cacheDir
        val sample1 = File(cacheDir, "sample_landscape.jpg")
        val sample2 = File(cacheDir, "sample_city.jpg")
        val bmp1 = TestImages.photo(2400, 1600, seed = 42)
        val bmp2 = TestImages.photo(1920, 1080, seed = 99)
        TestImages.write(bmp1, sample1, Bitmap.CompressFormat.JPEG, 96)
        TestImages.write(bmp2, sample2, Bitmap.CompressFormat.JPEG, 94)
        bmp1.recycle()
        bmp2.recycle()

        // Populate selection with realistic images
        selection.clear()
        selection.add(listOf(Uri.fromFile(sample1), Uri.fromFile(sample2)))

        // Wait for inspection to finish
        runBlocking {
            var waited = 0
            while (selection.readyImages.size < 2 && waited < 5000) {
                delay(200)
                waited += 200
            }
        }

        // 3. Capture Configure Screen
        compose.setContent {
            ImageCompressorTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ConfigureRoute(onBack = {}, onStarted = {})
                }
            }
        }
        compose.waitForIdle()
        capture("02_configure_screen")

        // 4. Run batch compression to get real results
        processor.start(
            images = selection.readyImages,
            options = ProcessingOptions(
                format = FormatOption.ORIGINAL,
                mode = CompressionMode.Quality(65),
                resize = ResizeSpec.None,
                preventLarger = true,
            ),
        )

        // Wait for batch to complete
        runBlocking {
            var waited = 0
            while (processor.state.value?.isRunning == true && waited < 10000) {
                delay(300)
                waited += 300
            }
        }

        // Capture Results Screen
        compose.setContent {
            ImageCompressorTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ResultsScreen(
                        state = processor.state.value,
                        saving = false,
                        snackbar = remember { SnackbarHostState() },
                        onBack = {},
                        onDone = {},
                        onOpenItem = {},
                        onRetry = {},
                        saveLocation = SaveLocation.Pictures,
                        onSaveAll = {},
                        onSaveAllToFolder = {},
                        onShareAll = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        capture("03_results_screen")

        // 5. Capture Comparison Screen
        val item = processor.state.value?.items?.firstOrNull()
        val result = item?.result
        if (result != null) {
            compose.setContent {
                ImageCompressorTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Scaffold(
                            topBar = { AppTopBar("Compare", onBack = {}) },
                        ) { padding ->
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                CompareSlider(
                                    original = result.source.uri,
                                    output = result.outputFile,
                                    aspectRatio = result.width.toFloat() / result.height,
                                )
                                Text(
                                    "Drag the handle to compare. The right side shows the actual output file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AppCard {
                                    val saved = result.savedBytes
                                    Text(
                                        if (saved > 0) "${Formatters.bytes(saved)} saved" else "${Formatters.bytes(-saved)} larger",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = if (saved > 0) AppTheme.status.success else AppTheme.status.warning,
                                    )
                                    Text(
                                        Formatters.percentChange(result.originalSizeBytes, result.sizeBytes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(Spacing.md))
                                    StatRowItem("Size", Formatters.bytes(result.originalSizeBytes), Formatters.bytes(result.sizeBytes))
                                    StatRowItem(
                                        "Dimensions",
                                        Formatters.dimensions(result.source.width, result.source.height),
                                        Formatters.dimensions(result.width, result.height),
                                    )
                                    StatRowItem("Format", result.source.formatLabel, result.format.label)
                                    StatRowItem("Quality", "—", result.quality?.toString() ?: "Lossless")
                                }
                                Banner("Target size met with 78% compression ratio", BannerKind.Info)
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            capture("04_comparison_screen")
        }
    }

    @Composable
    private fun StatRowItem(label: String, original: String, output: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
            Text(original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(output, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
    }
}
