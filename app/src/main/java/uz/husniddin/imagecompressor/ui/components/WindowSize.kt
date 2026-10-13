package uz.husniddin.imagecompressor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** True on short windows (e.g. phones in landscape), where stacked bottom actions waste space. */
@Composable
fun isCompactHeight(): Boolean {
    val heightPx = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { heightPx.toDp() } < 480.dp
}
