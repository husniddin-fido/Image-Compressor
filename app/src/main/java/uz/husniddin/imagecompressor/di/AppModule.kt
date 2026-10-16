package uz.husniddin.imagecompressor.di

import android.content.ContentResolver
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uz.husniddin.imagecompressor.data.processing.ImageEngine
import uz.husniddin.imagecompressor.image.ImageInspector
import uz.husniddin.imagecompressor.telemetry.FirebaseTelemetry
import uz.husniddin.imagecompressor.telemetry.Telemetry
import uz.husniddin.imagecompressor.image.ImageProcessor
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

    @Provides @Singleton
    fun provideImageProcessor(resolver: ContentResolver, @DefaultDispatcher dispatcher: CoroutineDispatcher) =
        ImageProcessor(resolver, dispatcher)

    @Provides
    fun provideTelemetry(impl: FirebaseTelemetry): Telemetry = impl

    @Provides
    fun provideImageEngine(processor: ImageProcessor): ImageEngine = ImageEngine(processor::process)

    @Provides @Singleton
    fun provideImageInspector(resolver: ContentResolver, @IoDispatcher io: CoroutineDispatcher) =
        ImageInspector(resolver, io)
}
