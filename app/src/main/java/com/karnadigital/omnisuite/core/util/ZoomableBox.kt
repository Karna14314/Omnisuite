package com.karnadigital.omnisuite.core.util

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 4.0f,
    onScaleChanged: (Float) -> Unit = {},
    lazyListState: LazyListState? = null,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                // Double-tap to zoom toggle
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                        } else {
                            scale = 2.5f
                            offsetX = 0f
                        }
                        onScaleChanged(scale)
                    }
                )
            }
            .pointerInput(Unit) {
                // Pinch-to-zoom and pan when zoomed
                detectTransformGestures { _, pan, zoom, _ ->
                    if (scale > 1f || zoom != 1f) {
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                        onScaleChanged(scale)
                    }

                    if (scale > 1f) {
                        val maxOffsetX = (size.width * (scale - 1f)) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)

                        // Forward vertical pan to LazyColumn for scrolling when zoomed
                        if (pan.y != 0f) {
                            lazyListState?.dispatchRawDelta(-pan.y)
                        }
                    } else {
                        offsetX = 0f
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = 0f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            )
    ) {
        content()
    }
}

