package uz.husniddin.imagecompressor.ui.configure

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.domain.model.SourceImage
import uz.husniddin.imagecompressor.ui.theme.AppTheme
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters

private val BadgeBackground = Color(0xD90F172A)
private val OriginalSizeColor = Color(0xFFFB923C)
private val SmallerSizeColor = Color(0xFF60A5FA)

/** Top preview area: one large live preview, or a grid when several images are selected. */
@Composable
fun LivePreviewPane(
    images: List<SourceImage>,
    previews: Map<String, PreviewState>,
    modifier: Modifier = Modifier,
) {
    if (images.size == 1) {
        val image = images.first()
        SingleLivePreview(image, previews[image.id], modifier)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(images, key = { it.id }) { image ->
                GridLivePreview(image, previews[image.id])
            }
        }
    }
}

@Composable
private fun SingleLivePreview(image: SourceImage, preview: PreviewState?, modifier: Modifier) {
    val context = LocalContext.current
    var holdingOriginal by remember { mutableStateOf(false) }
    var toggledOriginal by remember { mutableStateOf(false) }
    val showOriginal = holdingOriginal || toggledOriginal

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val shown: Any? = if (showOriginal) image.uri else preview?.last?.outputFile ?: image.uri
    Box(
        modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {
                boxSize = size
                // Pinch to zoom into details (where compression artifacts show), drag to pan.
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    val maxX = boxSize.width * (scale - 1) / 2
                    val maxY = boxSize.height * (scale - 1) / 2
                    offset = Offset(
                        (offset.x + pan.x).coerceIn(-maxX, maxX),
                        (offset.y + pan.y).coerceIn(-maxY, maxY),
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .semantics {
                contentDescription = if (showOriginal) "Original image" else "Live preview of the compressed image"
            },
    ) {
        AsyncImage(
            // Decode up to 2048 px so zooming in still shows real detail, without loading 40 MP bitmaps.
            model = ImageRequest.Builder(context).data(shown).size(PREVIEW_DECODE_PX).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        OriginalChip(
            showing = showOriginal,
            onHoldChange = { holdingOriginal = it },
            onToggle = { toggledOriginal = !toggledOriginal },
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs),
        )
        if (preview is PreviewState.Rendering) {
            Box(Modifier.align(Alignment.TopEnd).padding(Spacing.sm)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }
        SizeBadge(
            image = image,
            preview = preview,
            large = true,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun GridLivePreview(image: SourceImage, preview: PreviewState?) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(preview?.last?.outputFile ?: image.uri).build(),
            contentDescription = "Preview of ${image.displayName}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (preview is PreviewState.Rendering) {
            Box(Modifier.align(Alignment.TopEnd).padding(Spacing.xs)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }
        SizeBadge(image, preview, large = false, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** "877 KB → 48 KB" badge with the real output size; shows progress or errors honestly. */
@Composable
private fun SizeBadge(image: SourceImage, preview: PreviewState?, large: Boolean, modifier: Modifier) {
    val style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium
    val result: ProcessedImage? = (preview as? PreviewState.Ready)?.result
    Surface(
        color = BadgeBackground,
        contentColor = Color.White,
        // Flush with the preview's bottom-right corner (the parent clips the outer corner).
        shape = RoundedCornerShape(topStart = if (large) 16.dp else 12.dp),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(horizontal = if (large) Spacing.md else Spacing.xs, vertical = if (large) Spacing.xs else Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Formatters.bytes(image.sizeBytes), style = style, color = OriginalSizeColor, maxLines = 1)
            Spacer(Modifier.width(Spacing.xxs))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "to",
                modifier = Modifier.size(if (large) 18.dp else 12.dp),
            )
            Spacer(Modifier.width(Spacing.xxs))
            when {
                result != null -> Text(
                    Formatters.bytes(result.sizeBytes),
                    style = style,
                    color = if (result.isSmaller) SmallerSizeColor else AppTheme.status.warning,
                    maxLines = 1,
                )
                preview is PreviewState.Failed -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = preview.message,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(if (large) 18.dp else 14.dp),
                )
                else -> Text("…", style = style, color = SmallerSizeColor, overflow = TextOverflow.Clip)
            }
        }
    }
}

/**
 * Hold to peek at the original (released = back to the preview); a quick tap toggles it instead,
 * which also makes it usable with TalkBack.
 */
@Composable
private fun OriginalChip(
    showing: Boolean,
    onHoldChange: (Boolean) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        color = if (showing) Color.White else BadgeBackground,
        contentColor = if (showing) Color(0xFF0F172A) else Color.White,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldChange(true)
                        tryAwaitRelease()
                        onHoldChange(false)
                    },
                    // Present so that a long hold does not also count as a tap on release.
                    onLongPress = {},
                    onTap = { onToggle() },
                )
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (showing) "Showing original" else "Showing compressed preview"
                onClick(label = "Toggle original") { onToggle(); true }
            },
    ) {
        Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xxs))
            Text(if (showing) "Original" else "Hold for original", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private const val PREVIEW_DECODE_PX = 2048
