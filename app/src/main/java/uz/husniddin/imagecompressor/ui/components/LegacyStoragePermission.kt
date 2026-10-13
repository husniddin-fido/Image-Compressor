package uz.husniddin.imagecompressor.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Runs [action] once saving to Pictures is allowed. Only Android 9 (API 28) needs the legacy
 * WRITE_EXTERNAL_STORAGE permission; on newer versions [action] runs immediately.
 */
@Composable
fun rememberPicturesWriteGate(onDenied: () -> Unit): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted) action?.invoke() else onDenied()
    }
    return remember(context, launcher) {
        { action ->
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pending = action
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                action()
            }
        }
    }
}
