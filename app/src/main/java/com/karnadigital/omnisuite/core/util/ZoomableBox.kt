package com.karnadigital.omnisuite.core.util

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 4.0f,
    onScaleChanged: (Float) -> Unit = {},
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                var lastTapTime = 0L
                var lastTapPos = Offset.Zero
                
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val now = System.currentTimeMillis()
                    val isDoubleTap = now - lastTapTime < 300 &&
                            (down.position - lastTapPos).getDistance() < 100
                    
                    if (isDoubleTap) {
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
                        lastTapTime = 0L
                        down.consume()
                    } else {
                        lastTapTime = now
                        lastTapPos = down.position
                    }
                    
                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.size
                            if (pointerCount >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                
                                if (!pastTouchSlop) {
                                    zoom *= zoomChange
                                    pan += panChange
                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = abs(1 - zoom) * centroidSize
                                    val panMotion = pan.getDistance()
                                    
                                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }
                                
                                if (pastTouchSlop) {
                                    scale = (scale * zoomChange).coerceIn(minScale, maxScale)
                                    onScaleChanged(scale)
                                    
                                    if (scale > 1f) {
                                        val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                        val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                        offsetX = (offsetX + panChange.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                                        offsetY = (offsetY + panChange.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                    
                                    event.changes.forEach {
                                        it.consume()
                                    }
                                }
                            } else if (pointerCount == 1 && scale > 1f) {
                                val panChange = event.calculatePan()
                                if (panChange != Offset.Zero) {
                                    val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                    offsetX = (offsetX + panChange.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + panChange.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                                    
                                    event.changes.forEach {
                                        it.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
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

