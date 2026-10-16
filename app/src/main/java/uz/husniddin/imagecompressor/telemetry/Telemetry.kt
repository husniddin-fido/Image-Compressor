package uz.husniddin.imagecompressor.telemetry

/**
 * Anonymous diagnostics. Implementations must never receive images, file names, URIs or other
 * personal data — only coarse, non-identifying values (counts, modes, formats, percentages).
 */
interface Telemetry {
    /** Turns analytics and crash reporting on or off (the user's "Share diagnostics" setting). */
    fun setCollectionEnabled(enabled: Boolean)

    fun logEvent(name: String, params: Map<String, Any> = emptyMap())

    fun logScreen(screenName: String)

    /** Reports an unexpected, handled error (never user content). */
    fun recordNonFatal(throwable: Throwable)
}

/** Used in tests and when Firebase isn't configured. */
object NoOpTelemetry : Telemetry {
    override fun setCollectionEnabled(enabled: Boolean) = Unit
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun logScreen(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}

/** Event names and parameters, in one place so they stay consistent (snake_case, ≤ 40 chars). */
object Events {
    const val COMPRESSION_STARTED = "compression_started"
    const val COMPRESSION_FINISHED = "compression_finished"
    const val IMAGES_SAVED = "images_saved"
    const val IMAGES_SHARED = "images_shared"

    const val P_IMAGE_COUNT = "image_count"
    const val P_MODE = "mode"
    const val P_FORMAT = "format"
    const val P_RESIZE = "resize"
    const val P_SUCCEEDED = "succeeded"
    const val P_FAILED = "failed"
    const val P_CANCELLED = "cancelled"
    const val P_SAVED_PERCENT = "saved_percent"
    const val P_DESTINATION = "destination"
    const val P_COUNT = "count"
}
