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
    onTap: () -> Unit = {},
    lazyListState: LazyListState? = null,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // State to track multi-touch for cross-element pinch
    var pinchStartDistance by remember { mutableFloatStateOf(0f) }
    var pinchStartScale by remember { mutableFloatStateOf(1f) }
    var isPinching by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                // Interception layer: detect multi-touch BEFORE children process events
                // This enables pinch-to-zoom even when fingers land on different pages
                awaitPointerEventScope {
                    while (true) {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)

                        // Wait briefly to see if a second finger arrives
                        var secondPointer: PointerInputChange? = null
                        val deadline = System.nanoTime() + 150_000_000L // 150ms
                        while (System.nanoTime() < deadline) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                secondPointer = pressed.firstOrNull { it.id != firstDown.id }
                                break
                            }
                            if (!firstDown.pressed) break
                        }

                        if (secondPointer != null) {
                            // Multi-touch detected: consume events to prevent child handling
                            isPinching = true
                            pinchStartDistance = (firstDown.position - secondPointer.position).getDistance()
                            pinchStartScale = scale

                            // Track and consume all events from both pointers
                            do {
                                val event = awaitPointerEvent()
                                val p1 = event.changes.firstOrNull { it.id == firstDown.id }
                                val p2 = event.changes.firstOrNull { it.id == secondPointer.id }

                                if (p1 != null && p2 != null && p1.pressed && p2.pressed) {
                                    val currentDist = (p1.position - p2.position).getDistance()
                                    if (pinchStartDistance > 0) {
                                        val zoomFactor = currentDist / pinchStartDistance
                                        val newScale = (pinchStartScale * zoomFactor).coerceIn(minScale, maxScale)
                                        scale = newScale
                                        onScaleChanged(scale)
                                    }
                                    p1.consume()
                                    p2.consume()
                                }
                            } while (event.changes.any {
                                (it.id == firstDown.id || it.id == secondPointer.id) && it.pressed
                            })
                            isPinching = false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                    if (newScale != scale) {
                        scale = newScale
                        onScaleChanged(scale)
                    }

                    if (scale > 1f) {
                        val maxOffsetX = (size.width * (scale - 1f)) / 2f
                        val maxOffsetY = (size.height * (scale - 1f)) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                            offsetX = 0f
                            offsetY = 0f
                        }
                        onScaleChanged(scale)
                    },
                    onTap = {
                        onTap()
                    }
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
    ) {
        content()
    }
}

