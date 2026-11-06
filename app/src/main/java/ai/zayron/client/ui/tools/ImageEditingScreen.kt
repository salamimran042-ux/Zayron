package ai.zayron.client.ui.tools

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local mask-preparation workspace. The photo and brush strokes stay in memory
 * on this device; no upload or AI image transformation occurs here.
 */
@Composable
fun ImageEditingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var brushSize by remember { mutableStateOf(28f) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        strokes.clear()
        activeStroke = emptyList()
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        image = null
        loadError = null
        if (uri != null) {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                if (bitmap != null) image = bitmap.asImageBitmap()
                else loadError = "This image could not be opened. Try another photo."
            } catch (_: Exception) {
                loadError = "This image could not be opened. Try another photo."
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI Inpaint & Eraser", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Select a photo, then paint over the area you want to mark for removal or replacement.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUri == null) "Choose photo" else "Choose another photo")
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize().padding(8.dp).pointerInput(image, brushSize, strokes.size) {
                    detectDragGestures(
                        onDragStart = { start -> activeStroke = listOf(start) },
                        onDrag = { change, _ ->
                            change.consume()
                            activeStroke = activeStroke + change.position
                        },
                        onDragEnd = {
                            if (activeStroke.isNotEmpty()) strokes.add(activeStroke)
                            activeStroke = emptyList()
                        },
                        onDragCancel = { activeStroke = emptyList() }
                    )
                }
            ) {
                val bitmap = image
                if (bitmap != null) {
                    drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                } else {
                    drawRect(Color(0xFFF1F0F7))
                }

                fun drawMask(points: List<Offset>) {
                    if (points.size == 1) {
                        drawCircle(
                            color = Color(0x99FF3B30),
                            radius = brushSize / 2f,
                            center = points.first()
                        )
                    } else if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = Color(0xB3FF3B30),
                            style = Stroke(width = brushSize, cap = StrokeCap.Round)
                        )
                    }
                }
                strokes.forEach(::drawMask)
                drawMask(activeStroke)
            }
        }

        if (loadError != null) {
            Text(loadError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (image == null) {
            Text(
                "Your selected photo will appear here. Brush over it to create a visible mask.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Brush size", style = MaterialTheme.typography.labelLarge)
        Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 8f..72f)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Undo") }
            OutlinedButton(
                onClick = { strokes.clear(); activeStroke = emptyList() },
                enabled = strokes.isNotEmpty() || activeStroke.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Clear mask") }
        }

        Text(
            "Mask drawing is a local preparation feature. The photo is not uploaded or changed; AI inpainting and object removal still require the Zayron backend.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
