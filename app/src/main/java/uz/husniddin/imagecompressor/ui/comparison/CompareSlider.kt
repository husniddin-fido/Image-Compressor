package uz.husniddin.imagecompressor.ui.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import uz.husniddin.imagecompressor.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Before/after slider: the original is shown left of the handle, the real output file right of it.
 * Both images use the same Fit scaling inside one box, so they line up pixel-for-pixel when the
 * aspect ratio was preserved.
 */
@Composable
fun CompareSlider(
    original: Any,
    output: Any,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    var fraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    // Height is capped (e.g. in landscape); aspectRatio then derives the width from the height.
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .heightIn(max = 520.dp)
                .aspectRatio(aspectRatio.coerceIn(0.65f, 1.8f))
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .semantics {
                    contentDescription = "Before and after comparison. Adjust to reveal more of the original or the compressed image."
                    stateDescription = "${(fraction * 100).roundToInt()}% original"
                    setProgress { value -> fraction = value.coerceIn(0f, 1f); true }
                },
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val gestures = Modifier
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, drag ->
                        change.consume()
                        fraction = (fraction + drag / widthPx).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(widthPx) {
                    detectTapGestures { offset -> fraction = (offset.x / widthPx).coerceIn(0f, 1f) }
                }
            val light = MaterialTheme.colorScheme.surfaceContainerLow
            val dark = MaterialTheme.colorScheme.surfaceContainerHigh
            Box(Modifier.fillMaxSize().then(gestures)) {
                // Each layer paints its own checkerboard so transparent pixels never reveal the other layer.
                AsyncImage(
                    model = output,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().drawBehind { drawCheckerboard(light, dark) },
                )
                AsyncImage(
                    model = original,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = size.width * fraction) {
                                drawCheckerboard(light, dark)
                                this@drawWithContent.drawContent()
                            }
                        },
                )
                val density = LocalDensity.current
                val handleX = with(density) { (widthPx * fraction).toDp() }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .offset(x = handleX - 1.dp)
                        .background(Color.White),
                )
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = Color(0xFF0F172A),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = handleX - 20.dp)
                        .size(40.dp)
                        .shadow(4.dp, CircleShape)
                        .border(1.dp, Color(0x1F000000), CircleShape),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Tag("Original", Modifier.align(Alignment.TopStart))
                Tag("Compressed", Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier) {
    Surface(
        color = Color(0x99000000),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.padding(Spacing.xs),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp))
    }
}

private fun DrawScope.drawCheckerboard(light: Color, dark: Color) {
    drawRect(light)
    val cell = 12.dp.toPx()
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            if ((row + col) % 2 == 1) {
                drawRect(dark, topLeft = Offset(col * cell, row * cell), size = Size(cell, cell))
            }
        }
    }
}
