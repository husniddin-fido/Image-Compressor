package uz.husniddin.imagecompressor.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/** One-off message for a snackbar, optionally with an action. */
data class UiMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

@Composable
fun CollectMessages(messages: Flow<UiMessage>, host: SnackbarHostState) {
    LaunchedEffect(messages, host) {
        messages.collect { msg ->
            val result = host.showSnackbar(
                msg.text,
                actionLabel = msg.actionLabel,
                withDismissAction = msg.actionLabel == null,
                duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
        }
    }
}
