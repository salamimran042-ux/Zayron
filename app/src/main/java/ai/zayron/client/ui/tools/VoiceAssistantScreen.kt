package ai.zayron.client.ui.tools

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Local voice utilities. Speech recognition availability and language support
 * depend on the Android device's installed recognition service.
 * This screen does not send audio or text to a Zayron AI backend.
 */
@Composable
fun VoiceAssistantScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Your microphone is off.") }
    var ttsReady by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        status = if (granted) "Microphone permission granted. You can start speaking."
        else "Microphone permission was not granted."
    }

    val recognitionAvailable = remember {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    val recognizer = remember(hasPermission, recognitionAvailable) {
        if (hasPermission && recognitionAvailable) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(recognizer) {
        if (recognizer != null) {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    listening = true
                    status = "Listening… speak clearly."
                }

                override fun onBeginningOfSpeech() {
                    listening = true
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    listening = false
                    status = "Processing your speech…"
                }

                override fun onError(error: Int) {
                    listening = false
                    status = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please try again."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech service stopped. Please try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The device speech service needs a network connection."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Please try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Wait a moment and retry."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected. Try speaking a little sooner."
                        else -> "Speech recognition failed (code $error)."
                    }
                }

                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        transcript = listOf(transcript.trim(), spoken.trim())
                            .filter { it.isNotEmpty() }
                            .joinToString(" ")
                        status = "Speech captured on this device."
                    } else {
                        status = "No words were returned. Please try again."
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) status = "Hearing: $partial"
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
        }
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Voice Assistant", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Use your phone's speech service to dictate text, then listen to that text aloud.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(status, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (recognizer != null) {
                                try {
                                    recognizer.startListening(speechIntent)
                                    listening = true
                                    status = "Starting microphone…"
                                } catch (_: Exception) {
                                    listening = false
                                    status = "Could not start speech recognition. Please retry."
                                }
                            }
                        },
                        enabled = !listening && (!hasPermission || recognizer != null)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.padding(start = 3.dp))
                        Text(if (hasPermission) "Start speaking" else "Allow microphone")
                    }
                    if (listening) {
                        OutlinedButton(onClick = {
                            recognizer?.stopListening()
                            listening = false
                        }) {
                            androidx.compose.material3.Icon(Icons.Default.Stop, contentDescription = null)
                            Text("Stop")
                        }
                    }
                }
                if (hasPermission && !recognitionAvailable) {
                    Text(
                        "No speech recognition service is available on this device.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        OutlinedTextField(
            value = transcript,
            onValueChange = { transcript = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("Recognized text") },
            placeholder = { Text("Your dictated words will appear here…") },
            minLines = 5
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val text = transcript.trim()
                    if (text.isNotEmpty() && ttsReady) {
                        tts?.language = Locale.getDefault()
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zayron-read-aloud")
                        status = "Reading your text aloud."
                    } else {
                        status = if (text.isEmpty()) "Enter or dictate some text first."
                        else "Text-to-speech is not ready on this device."
                    }
                },
                enabled = transcript.isNotBlank() && ttsReady
            ) {
                androidx.compose.material3.Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(Modifier.padding(start = 3.dp))
                Text("Read aloud")
            }
            OutlinedButton(
                onClick = {
                    tts?.stop()
                    transcript = ""
                    status = "Text cleared. Your microphone is off."
                },
                enabled = transcript.isNotEmpty()
            ) { Text("Clear") }
        }

        Text(
            "This is local dictation and text-to-speech only. AI conversation and spoken AI answers still require the Zayron backend.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
    }
}
