package uz.husniddin.imagecompressor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uz.husniddin.imagecompressor.data.output.OutputStorage
import uz.husniddin.imagecompressor.di.ApplicationScope
import javax.inject.Inject

@HiltAndroidApp
class ImageCompressorApp : Application() {

    @Inject lateinit var outputStorage: OutputStorage
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Sessions don't survive process death, so earlier temporary outputs are orphaned.
        val startedAt = System.currentTimeMillis()
        appScope.launch(Dispatchers.IO) {
            outputStorage.cleanUpOlderThan(startedAt)
            outputStorage.cleanPreviews()
        }
    }
}
