package com.karnadigital.omnisuite.feature.utility

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class TtsController(
    private val context: Context,
    private val onSpeakingStateChanged: (isSpeaking: Boolean, isPaused: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    var isInitialized by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1.0f)
    var pitch by mutableFloatStateOf(1.0f)

    private var currentTextChunks: List<String> = emptyList()
    private var currentChunkIndex = 0

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    isPaused = false
                    onSpeakingStateChanged(true, false)
                }

                override fun onDone(utteranceId: String?) {
                    if (currentChunkIndex < currentTextChunks.size - 1) {
                        currentChunkIndex++
                        speakChunk(currentChunkIndex)
                    } else {
                        isSpeaking = false
                        isPaused = false
                        onSpeakingStateChanged(false, false)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    isPaused = false
                    onSpeakingStateChanged(false, false)
                }
            })
            isInitialized = true
        } else {
            isInitialized = false
        }
    }

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) {
            if (!isInitialized) {
                Toast.makeText(context, "TTS engine initializing...", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Split long text into manageable sentence chunks (<= 400 chars)
        currentTextChunks = text.split(Regex("(?<=[.!?\\n])\\s+")).filter { it.isNotBlank() }
        if (currentTextChunks.isEmpty()) {
            currentTextChunks = listOf(text)
        }
        currentChunkIndex = 0

        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)
        speakChunk(0)
    }

    private fun speakChunk(index: Int) {
        if (index in currentTextChunks.indices) {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "chunk_$index")
            }
            tts?.speak(currentTextChunks[index], TextToSpeech.QUEUE_FLUSH, params, "chunk_$index")
        }
    }

    fun pause() {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            isPaused = true
            onSpeakingStateChanged(false, true)
        }
    }

    fun resume() {
        if (isPaused && currentChunkIndex in currentTextChunks.indices) {
            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)
            speakChunk(currentChunkIndex)
        }
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        isPaused = false
        currentChunkIndex = 0
        onSpeakingStateChanged(false, false)
    }

    fun updateSpeed(newSpeed: Float) {
        speed = newSpeed
        tts?.setSpeechRate(speed)
        if (isSpeaking) {
            speakChunk(currentChunkIndex)
        }
    }

    fun updatePitch(newPitch: Float) {
        pitch = newPitch
        tts?.setPitch(pitch)
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tts = null
    }
}

@Composable
fun ReadAloudButton(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPlayerBar by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    val controller = remember(context) {
        TtsController(context) { speaking, paused ->
            isSpeaking = speaking
            isPaused = paused
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                if (showPlayerBar) {
                    if (isSpeaking) controller.stop()
                    showPlayerBar = false
                } else {
                    showPlayerBar = true
                    if (text.isNotBlank()) {
                        controller.speak(text)
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                contentDescription = if (isSpeaking) "Read Aloud Active" else "Read Aloud",
                tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (showPlayerBar) {
        ReadAloudFloatingBar(
            text = text,
            controller = controller,
            onClose = {
                controller.stop()
                showPlayerBar = false
            }
        )
    }
}

@Composable
fun ReadAloudFloatingBar(
    text: String,
    controller: TtsController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = if (controller.isSpeaking) "Reading..." else if (controller.isPaused) "Paused" else "Read Aloud",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Play / Pause / Play-all toggle
                IconButton(
                    onClick = {
                        if (controller.isSpeaking) {
                            controller.pause()
                        } else if (controller.isPaused) {
                            controller.resume()
                        } else {
                            controller.speak(text)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (controller.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (controller.isSpeaking) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Stop button
                IconButton(
                    onClick = { controller.stop() },
                    enabled = controller.isSpeaking || controller.isPaused,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = if (controller.isSpeaking || controller.isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Speed toggle
                TextButton(
                    onClick = {
                        val nextIdx = (speeds.indexOf(controller.speed) + 1) % speeds.size
                        controller.updateSpeed(speeds[nextIdx])
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.2fx", controller.speed).removeSuffix(".00x")}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Read Aloud", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ReadAloudControls(
    isSpeaking: Boolean,
    pitch: Float,
    speed: Float,
    onPitchChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onStop, enabled = isSpeaking) {
            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Speed", style = MaterialTheme.typography.labelSmall)
                Text("${String.format(Locale.US, "%.1f", speed)}x", style = MaterialTheme.typography.labelSmall)
            }
            Slider(value = speed, onValueChange = onSpeedChange, valueRange = 0.5f..2.0f)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pitch", style = MaterialTheme.typography.labelSmall)
                Text("${String.format(Locale.US, "%.1f", pitch)}", style = MaterialTheme.typography.labelSmall)
            }
            Slider(value = pitch, onValueChange = onPitchChange, valueRange = 0.5f..2.0f)
        }
    }
}
