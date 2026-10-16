package uz.husniddin.imagecompressor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import uz.husniddin.imagecompressor.data.preferences.SettingsRepository
import uz.husniddin.imagecompressor.domain.model.CompressionMode
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.ResizeSpec
import uz.husniddin.imagecompressor.domain.model.ThemeMode
import uz.husniddin.imagecompressor.navigation.AppNavHost
import uz.husniddin.imagecompressor.navigation.ComparisonRoute
import uz.husniddin.imagecompressor.navigation.ConfigureRoute
import uz.husniddin.imagecompressor.navigation.HistoryRoute
import uz.husniddin.imagecompressor.navigation.HomeRoute
import uz.husniddin.imagecompressor.navigation.ResultsRoute
import uz.husniddin.imagecompressor.navigation.SelectionRoute
import uz.husniddin.imagecompressor.navigation.SettingsRoute
import uz.husniddin.imagecompressor.telemetry.Telemetry
import uz.husniddin.imagecompressor.ui.theme.ImageCompressorTheme
import uz.husniddin.imagecompressor.ui.theme.isDark
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var telemetry: Telemetry
    @Inject lateinit var selectionRepository: uz.husniddin.imagecompressor.data.session.SelectionRepository
    @Inject lateinit var batchProcessor: uz.husniddin.imagecompressor.data.processing.BatchProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val demoScreen = intent?.getStringExtra("demo_screen")
        val destination = if (demoScreen != null) prepareDemoData(demoScreen) else HomeRoute

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = (settings?.themeMode ?: ThemeMode.SYSTEM).isDark()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            ImageCompressorTheme(darkTheme = dark) {
                AppNavHost(
                    onScreenView = telemetry::logScreen,
                    startDestination = destination,
                )
            }
        }
    }

    private fun prepareDemoData(screen: String): Any {
        val cacheDir = cacheDir
        val sample1 = File(cacheDir, "demo_mountain.jpg")
        val sample2 = File(cacheDir, "demo_city.jpg")
        if (!sample1.exists() || !sample2.exists()) {
            val bmp1 = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
            val c1 = Canvas(bmp1)
            val p1 = Paint().apply {
                shader = LinearGradient(0f, 0f, 2400f, 1600f, Color.parseColor("#1A237E"), Color.parseColor("#00E5FF"), Shader.TileMode.CLAMP)
            }
            c1.drawRect(0f, 0f, 2400f, 1600f, p1)
            FileOutputStream(sample1).use { bmp1.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bmp1.recycle()

            val bmp2 = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            val c2 = Canvas(bmp2)
            val p2 = Paint().apply {
                shader = LinearGradient(0f, 0f, 1920f, 1080f, Color.parseColor("#D500F9"), Color.parseColor("#FF6D00"), Shader.TileMode.CLAMP)
            }
            c2.drawRect(0f, 0f, 1920f, 1080f, p2)
            FileOutputStream(sample2).use { bmp2.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmp2.recycle()
        }

        selectionRepository.clear()
        selectionRepository.add(listOf(Uri.fromFile(sample1), Uri.fromFile(sample2)))

        return when (screen) {
            "settings" -> SettingsRoute
            "history" -> HistoryRoute
            "selection" -> SelectionRoute
            "configure" -> ConfigureRoute
            "results", "comparison" -> {
                runBlocking {
                    var waited = 0
                    while (selectionRepository.readyImages.isEmpty() && waited < 2000) {
                        delay(100)
                        waited += 100
                    }
                }
                if (selectionRepository.readyImages.isNotEmpty()) {
                    batchProcessor.start(
                        images = selectionRepository.readyImages,
                        options = ProcessingOptions(
                            format = FormatOption.ORIGINAL,
                            mode = CompressionMode.Quality(65),
                            resize = ResizeSpec.None,
                            preventLarger = true,
                        ),
                    )
                    runBlocking {
                        var waited = 0
                        while (batchProcessor.state.value?.isRunning == true && waited < 4000) {
                            delay(100)
                            waited += 100
                        }
                    }
                }
                if (screen == "comparison") {
                    val firstId = batchProcessor.state.value?.items?.firstOrNull()?.id
                    if (firstId != null) ComparisonRoute(firstId) else ResultsRoute
                } else {
                    ResultsRoute
                }
            }
            else -> HomeRoute
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
