package com.karnadigital.omnisuite.feature.utility

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun ReadAloudButton(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSpeaking by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    val tts = remember {
        var ttsInstance: android.speech.tts.TextToSpeech? = null
        ttsInstance = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale.getDefault()
            }
        }
        ttsInstance
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    IconButton(
        onClick = {
            if (isSpeaking) {
                tts?.stop()
                isSpeaking = false
            } else {
                if (text.isNotBlank()) {
                    tts?.language = Locale.getDefault()
                    tts?.setPitch(pitch)
                    tts?.setSpeechRate(speed)
                    tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "readaloud")
                    isSpeaking = true
                }
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
            contentDescription = if (isSpeaking) "Stop reading" else "Read aloud",
            tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
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
                Text("${String.format("%.1f", speed)}x", style = MaterialTheme.typography.labelSmall)
            }
            Slider(value = speed, onValueChange = onSpeedChange, valueRange = 0.5f..2.0f)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pitch", style = MaterialTheme.typography.labelSmall)
                Text("${String.format("%.1f", pitch)}", style = MaterialTheme.typography.labelSmall)
            }
            Slider(value = pitch, onValueChange = onPitchChange, valueRange = 0.5f..2.0f)
        }
    }
}
