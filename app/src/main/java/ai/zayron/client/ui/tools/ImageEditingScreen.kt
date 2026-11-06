package ai.zayron.client.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MaskStroke(
    val points: List<Offset>,
    /** Brush diameter as a fraction of the displayed image width. */
    val widthFraction: Float
)

/**
 * Local mask-preparation workspace. The photo and brush strokes stay in memory
 * on this device; no upload or AI image transformation occurs here.
 */
@Composable
fun ImageEditingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val image: ImageBitmap? = remember(sourceBitmap) { sourceBitmap?.asImageBitmap() }
    var loadError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Choose a photo to begin preparing a mask.") }
    var brushSize by remember { mutableStateOf(28f) }
    val strokes = remember { mutableStateListOf<MaskStroke>() }
    var activeStroke by remember { mutableStateOf<MaskStroke?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        strokes.clear()
        activeStroke = null
        status = if (uri == null) "No photo selected." else "Loading photo…"
    }

    val maskExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destination ->
        val bitmap = sourceBitmap
        if (destination != null && bitmap != null && strokes.isNotEmpty()) {
            val snapshot = strokes.toList()
            status = "Exporting full-resolution mask…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { writeMaskPng(context, destination, bitmap.width, bitmap.height, snapshot) }
                }
                status = result.fold(
                    onSuccess = { "Mask exported as a PNG. Black = keep; white = edit/remove." },
                    onFailure = { "Mask export failed: ${it.message ?: "unable to write file"}" }
                )
            }
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        sourceBitmap = null
        loadError = null
        if (uri != null) {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                }
                if (bitmap != null) {
                    sourceBitmap = bitmap
                    status = "Photo ready. Paint over the area you want to edit."
                } else {
                    loadError = "This image could not be opened. Try another photo."
                    status = loadError!!
                }
            } catch (_: Exception) {
                loadError = "This image could not be opened. Try another photo."
                status = loadError!!
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI Inpaint & Eraser", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose a photo and paint over the area you want to remove or replace. The mask stays on this device.",
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
                modifier = Modifier.fillMaxSize().padding(8.dp).pointerInput(image, brushSize, density) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val bitmap = sourceBitmap
                            val rect = bitmap?.let { fittedImageRect(size.width.toFloat(), size.height.toFloat(), it.width, it.height) }
                            val normalized = rect?.let { normalizePoint(start, it) }
                            activeStroke = if (normalized != null) {
                                MaskStroke(listOf(normalized), (brushSize * density / rect.width()).coerceAtLeast(0.001f))
                            } else null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val current = activeStroke
                            val bitmap = sourceBitmap
                            val rect = bitmap?.let { fittedImageRect(size.width.toFloat(), size.height.toFloat(), it.width, it.height) }
                            val normalized = rect?.let { normalizePoint(change.position, it) }
                            if (current != null && normalized != null) {
                                activeStroke = current.copy(points = current.points + normalized)
                            }
                        },
                        onDragEnd = {
                            activeStroke?.let { if (it.points.isNotEmpty()) strokes.add(it) }
                            activeStroke = null
                        },
                        onDragCancel = { activeStroke = null }
                    )
                }
            ) {
                val bitmap = sourceBitmap
                val rect = bitmap?.let { fittedImageRect(size.width, size.height, it.width, it.height) }
                if (image != null && rect != null) {
                    drawImage(
                        image,
                        dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                        dstSize = IntSize(rect.width().toInt().coerceAtLeast(1), rect.height().toInt().coerceAtLeast(1))
                    )
                    strokes.forEach { drawNormalizedMask(it, rect) }
                    activeStroke?.let { drawNormalizedMask(it, rect) }
                } else {
                    drawRect(Color(0xFFF1F0F7))
                }
            }
        }

        if (loadError != null) {
            Text(loadError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (image == null) {
            Text(
                "Your photo will appear here. The preview preserves its aspect ratio; brush marks are mapped to the original image.",
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
                onClick = { strokes.clear(); activeStroke = null; status = "Mask cleared." },
                enabled = strokes.isNotEmpty() || activeStroke != null,
                modifier = Modifier.weight(1f)
            ) { Text("Clear mask") }
        }

        Button(
            onClick = { maskExporter.launch("zayron-edit-mask.png") },
            enabled = sourceBitmap != null && strokes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export mask PNG") }

        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Export creates a full-resolution PNG mask: white marks the edit area and black marks the area to keep. AI inpainting and object removal still require the Zayron backend.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun fittedImageRect(viewWidth: Float, viewHeight: Float, imageWidth: Int, imageHeight: Int): RectF {
    val scale = minOf(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (viewWidth - width) / 2f
    val top = (viewHeight - height) / 2f
    return RectF(left, top, left + width, top + height)
}

private fun normalizePoint(point: Offset, rect: RectF): Offset? {
    if (!rect.contains(point.x, point.y)) return null
    return Offset(
        ((point.x - rect.left) / rect.width()).coerceIn(0f, 1f),
        ((point.y - rect.top) / rect.height()).coerceIn(0f, 1f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNormalizedMask(
    stroke: MaskStroke,
    rect: RectF
) {
    if (stroke.points.isEmpty()) return
    val points = stroke.points.map { Offset(rect.left + it.x * rect.width(), rect.top + it.y * rect.height()) }
    val width = (stroke.widthFraction * rect.width()).coerceAtLeast(1f)
    if (points.size == 1) {
        drawCircle(Color(0xB3FF3B30), radius = width / 2f, center = points.first())
    } else {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, Color(0xB3FF3B30), style = Stroke(width = width, cap = StrokeCap.Round))
    }
}

private fun writeMaskPng(
    context: Context,
    destination: Uri,
    imageWidth: Int,
    imageHeight: Int,
    strokes: List<MaskStroke>
) {
    val mask = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(mask)
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.strokeWidth = (stroke.widthFraction * imageWidth).coerceAtLeast(1f)
            val points = stroke.points.map { Offset(it.x * imageWidth, it.y * imageHeight) }
            if (points.size == 1) {
                canvas.drawCircle(points.first().x, points.first().y, paint.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
                paint.style = Paint.Style.STROKE
            } else {
                val path = android.graphics.Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                canvas.drawPath(path, paint)
            }
        }
        val output = context.contentResolver.openOutputStream(destination)
            ?: throw IllegalStateException("The selected destination could not be opened")
        output.use {
            check(mask.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed" }
        }
    } finally {
        mask.recycle()
    }
}
