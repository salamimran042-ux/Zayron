package ai.zayron.client.ui.tools

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ToolPurple = Color(0xFF5146D8)

/**
 * Unified workspace panels for the five non-chat tools.
 *
 * File selection is performed locally with Android's system picker. Processing
 * buttons remain disabled until the corresponding secure backend is integrated.
 */
@Composable
fun ToolWorkspaceScreen(
    mode: String,
    modifier: Modifier = Modifier
) {
    // Keep each tool's draft and selected file isolated when the user switches modes.
    var selectedImage by remember(mode) { mutableStateOf<Uri?>(null) }
    var prompt by remember(mode) { mutableStateOf("") }
    var appBrief by remember(mode) { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedImage = uri }

    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (mode) {
            "Video Generation" -> {
                ToolHeading(
                    icon = { Icon(Icons.Default.VideoLibrary, null, tint = ToolPurple) },
                    title = "Image-to-Video",
                    subtitle = "Prepare a source image and describe the motion you want."
                )
                SelectionCard(
                    title = if (selectedImage == null) "No source image selected" else "Source image selected",
                    detail = "Choose an image from your phone. It stays on this device until a service is connected.",
                    buttonText = "Choose image",
                    onClear = { selectedImage = null },
                    onClick = { imagePicker.launch("image/*") }
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Motion prompt") },
                    placeholder = { Text("Describe the movement, camera motion and atmosphere…") },
                    minLines = 3
                )
                ServiceNotice("Video generation is not connected yet. Your image and prompt are not uploaded.")
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Generation service not connected") }
            }

            "Image Editing" -> ImageEditingScreen(Modifier.fillMaxSize())

            "Document AI" -> DocumentAiScreen(Modifier.fillMaxSize())

            "App Builder" -> {
                ToolHeading(
                    icon = { Icon(Icons.Default.PhoneAndroid, null, tint = ToolPurple) },
                    title = "AI App Builder",
                    subtitle = "Describe the Android app you want to create."
                )
                OutlinedTextField(
                    value = appBrief,
                    onValueChange = { appBrief = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("App idea") },
                    placeholder = { Text("Explain the app's purpose, users, main screens and features…") },
                    minLines = 5
                )
                ServiceNotice("This field is a local project brief only. Code generation, preview, build workers and APK export are not connected.")
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Build service not connected")
                }
            }

            "Voice Assistant" -> VoiceAssistantScreen(Modifier.fillMaxSize())

            else -> {
                ToolHeading(
                    icon = { Icon(Icons.Default.AutoAwesome, null, tint = ToolPurple) },
                    title = mode,
                    subtitle = "Select a tool from the workspace switcher."
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ToolHeading(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = ToolPurple.copy(alpha = 0.09f)) {
            androidx.compose.foundation.layout.Box(Modifier.padding(12.dp)) { icon() }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectionCard(
    title: String,
    detail: String,
    buttonText: String,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClick) {
                    Text(buttonText)
                }
                if (onClear != null) {
                    TextButton(onClick = onClear) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceNotice(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
