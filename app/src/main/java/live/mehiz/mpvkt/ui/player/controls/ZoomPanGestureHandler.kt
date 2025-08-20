package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.update
import live.mehiz.mpvkt.ui.player.PlayerViewModel
import kotlin.math.max
import kotlin.math.min

@Composable
fun ZoomPanGestureHandler(
    viewModel: PlayerViewModel,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Zoom limits: 0.5x (50%) to 2.0x (200%)
    val minZoom = 0.5f
    val maxZoom = 2.0f
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures(
                    panZoomLock = false,
                    onGesture = { _, pan, zoom, _ ->
                        // Update scale with limits
                        val newScale = (scale * zoom).coerceIn(minZoom, maxZoom)
                        
                        // Calculate pan limits based on current scale
                        val maxPanX = (size.width * (newScale - 1)) / 2
                        val maxPanY = (size.height * (newScale - 1)) / 2
                        
                        // Update offset with limits to prevent over-panning
                        val newOffset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                        
                        // Apply transformations
                        scale = newScale
                        offset = newOffset
                        
                        // Update video transformation through MPV
                        viewModel.updateVideoTransformation(scale, offset)
                    }
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    ) {
        content()
    }
}

// Reset function for zoom and pan
@Composable
fun rememberZoomPanState(
    onReset: () -> Unit = {}
): ZoomPanState {
    return remember {
        ZoomPanState(onReset)
    }
}

class ZoomPanState(
    private val onReset: () -> Unit
) {
    fun reset() {
        onReset()
    }
}
