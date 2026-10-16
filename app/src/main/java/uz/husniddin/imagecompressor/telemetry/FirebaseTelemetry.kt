package uz.husniddin.imagecompressor.telemetry

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Analytics + Crashlytics. If the build has no google-services.json, Firebase isn't
 * initialised and every call is a no-op, so the app works the same without it.
 */
@Singleton
class FirebaseTelemetry @Inject constructor(
    @ApplicationContext private val context: Context,
) : Telemetry {

    private val available: Boolean by lazy {
        val configured = FirebaseApp.getApps(context).isNotEmpty()
        if (!configured) Log.i(TAG, "Firebase not configured (no google-services.json); telemetry disabled")
        configured
    }
    private val analytics: FirebaseAnalytics? by lazy { if (available) FirebaseAnalytics.getInstance(context) else null }
    private val crashlytics: FirebaseCrashlytics? by lazy { if (available) FirebaseCrashlytics.getInstance() else null }

    @Volatile private var enabled = false

    override fun setCollectionEnabled(enabled: Boolean) {
        this.enabled = enabled
        analytics?.setAnalyticsCollectionEnabled(enabled)
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    override fun logEvent(name: String, params: Map<String, Any>) {
        if (!enabled) return
        analytics?.logEvent(name, params.toBundle())
    }

    override fun logScreen(screenName: String) {
        if (!enabled) return
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
            },
        )
    }

    override fun recordNonFatal(throwable: Throwable) {
        if (!enabled) return
        crashlytics?.recordException(throwable)
    }

    private fun Map<String, Any>.toBundle() = Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Double -> putDouble(key, value)
                is Boolean -> putString(key, value.toString())
                else -> putString(key, value.toString().take(100))
            }
        }
    }

    private companion object {
        const val TAG = "Telemetry"
    }
}
