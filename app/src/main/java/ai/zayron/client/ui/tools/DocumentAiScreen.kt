package ai.zayron.client.ui.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

private const val MAX_PREVIEW_CHARS = 12_000

/**
 * Local document preparation only. Plain-text files can be previewed on-device.
 * PDF and DOCX selection/metadata are supported, but text extraction, indexing,
 * citations, uploads and AI answers require later implementation/backend work.
 */
@Composable
fun DocumentAiScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedName by remember { mutableStateOf<String?>(null) }
    var selectedMime by remember { mutableStateOf<String?>(null) }
    var selectedSize by remember { mutableStateOf<Long?>(null) }
    var preview by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Choose a document to inspect it on this device.") }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            loading = true
            preview = ""
            status = "Reading document details…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    inspectDocument(context, uri)
                }
                selectedName = result.name
                selectedMime = result.mime
                selectedSize = result.size
                preview = result.preview
                status = result.message
                loading = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                androidx.compose.foundation.layout.Box(Modifier.padding(12.dp)) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Document AI", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Select a file and inspect its local details or text preview.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(selectedName ?: "No document selected", style = MaterialTheme.typography.titleMedium)
                if (selectedName != null) {
                    Text(
                        "Type: ${selectedMime ?: "Unknown"}  •  Size: ${formatFileSize(selectedSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            picker.launch(
                                arrayOf(
                                    "text/plain",
                                    "text/*",
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                )
                            )
                        },
                        enabled = !loading
                    ) { Text(if (selectedName == null) "Choose document" else "Choose another") }
                    if (selectedName != null) {
                        OutlinedButton(onClick = {
                            selectedName = null
                            selectedMime = null
                            selectedSize = null
                            preview = ""
                            status = "Document cleared from this screen."
                        }) { Text("Remove") }
                    }
                }
            }
        }

        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (preview.isNotBlank()) {
            Text("Local text preview", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = preview,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                minLines = 6,
                maxLines = 14
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                "Privacy and capability: the selected file is read locally only. Plain-text preview is available now. PDF and DOCX files can be selected and identified, but their text extraction is not implemented yet. AI questions, citations, indexing and cloud upload are not connected.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private data class DocumentInspection(
    val name: String,
    val mime: String?,
    val size: Long?,
    val preview: String,
    val message: String
)

private fun inspectDocument(context: Context, uri: Uri): DocumentInspection {
    var name = "Selected document"
    var size: Long? = null
    var mime: String? = null

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    mime = context.contentResolver.getType(uri)
    val isText = mime?.startsWith("text/") == true ||
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
        setOf("txt", "md", "csv", "json", "xml", "log")

    if (!isText) {
        val message = when {
            mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) ->
                "PDF selected. File details are available; PDF text extraction is a future milestone."
            name.endsWith(".docx", ignoreCase = true) ->
                "DOCX selected. File details are available; Word text extraction is a future milestone."
            else -> "File selected. Local text preview is currently available for plain-text formats."
        }
        return DocumentInspection(name, mime, size, "", message)
    }

    return try {
        val output = StringBuilder()
        var truncated = false
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                val buffer = CharArray(2048)
                while (output.length < MAX_PREVIEW_CHARS) {
                    val remaining = MAX_PREVIEW_CHARS - output.length
                    val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count < 0) break
                    output.append(buffer, 0, count)
                }
                if (reader.read() != -1) truncated = true
            }
        } ?: throw IllegalStateException("The document could not be opened.")
        val text = output.toString()
        val message = if (truncated) {
            "Plain-text file opened locally. Preview limited to the first $MAX_PREVIEW_CHARS characters."
        } else {
            "Plain-text file opened locally. No upload was performed."
        }
        DocumentInspection(name, mime, size, text, message)
    } catch (error: Exception) {
        DocumentInspection(
            name,
            mime,
            size,
            "",
            "The file was selected, but its text could not be read: ${error.message ?: "unknown error"}"
        )
    }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes < 0L) return "Unknown"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
