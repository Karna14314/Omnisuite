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
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
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
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        var zoom = 1f
                        var pan = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop

                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (!pastTouchSlop) {
                                    zoom *= zoomChange
                                    pan += panChange

                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = kotlin.math.abs(1 - zoom) * centroidSize
                                    val panMotion = pan.getDistance()

                                    if (zoomMotion > touchSlop || (scale > 1.05f && panMotion > touchSlop)) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop) {
                                    // When multi-touch pinch or zoomed in, consume and apply transforms
                                    if (zoomChange != 1f || (scale > 1.05f && panChange != Offset.Zero)) {
                                        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
                                        if (newScale != scale) {
                                            scale = newScale
                                            onScaleChanged(scale)
                                        }

                                        if (scale > 1.05f) {
                                            val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                            val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                            offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }

                                        event.changes.forEach {
                                            if (it.positionChanged()) {
                                                it.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })
                    }
                }
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

