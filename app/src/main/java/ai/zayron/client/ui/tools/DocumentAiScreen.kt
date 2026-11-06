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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.io.FileOutputStream
import java.io.Writer
import java.util.Locale
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

private const val MAX_PREVIEW_CHARS = 12_000

/**
 * Local document preparation only. Plain-text files can be previewed on-device.
 * Plain-text, DOCX and selectable PDF text previews are extracted locally.
 * OCR, indexing, citations, uploads and AI answers require later implementation/backend work.
 */
@Composable
fun DocumentAiScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedName by remember { mutableStateOf<String?>(null) }
    var selectedMime by remember { mutableStateOf<String?>(null) }
    var selectedSize by remember { mutableStateOf<Long?>(null) }
    var preview by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatchIndex by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("Choose a document to inspect it on this device.") }
    var loading by remember { mutableStateOf(false) }
    var inspectionJob by remember { mutableStateOf<Job?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            inspectionJob?.cancel()
            loading = true
            preview = ""
            searchQuery = ""
            searchMatchIndex = 0
            status = "Reading document details…"
            inspectionJob = scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        inspectDocument(context, uri)
                    }
                    selectedName = result.name
                    selectedMime = result.mime
                    selectedSize = result.size
                    preview = result.preview
                    status = result.message
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    if (inspectionJob === coroutineContext[Job]) {
                        loading = false
                        inspectionJob = null
                    }
                }
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
                            inspectionJob?.cancel()
                            inspectionJob = null
                            loading = false
                            selectedName = null
                            selectedMime = null
                            selectedSize = null
                            preview = ""
                            searchQuery = ""
                            searchMatchIndex = 0
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
            Text("Find text in this document", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it.take(120)
                    searchMatchIndex = 0
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search extracted text") },
                placeholder = { Text("Enter a word or phrase") }
            )
            if (searchQuery.isNotBlank()) {
                val result = findDocumentMatches(preview, searchQuery)
                Text(
                    when {
                        result.count == 0 -> "No matches in the extracted preview."
                        result.count == 1 -> "1 match found in the extracted preview."
                        else -> "${result.count} matches found in the extracted preview."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.matchStarts.isNotEmpty()) {
                    val safeIndex = searchMatchIndex.coerceIn(0, result.matchStarts.lastIndex)
                    val snippet = documentMatchSnippet(
                        preview,
                        searchQuery,
                        result.matchStarts[safeIndex]
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { searchMatchIndex = (safeIndex - 1).coerceAtLeast(0) },
                            enabled = safeIndex > 0
                        ) { Text("Previous") }
                        OutlinedButton(
                            onClick = {
                                searchMatchIndex = (safeIndex + 1).coerceAtMost(result.matchStarts.lastIndex)
                            },
                            enabled = safeIndex < result.matchStarts.lastIndex
                        ) { Text("Next") }
                        Text(
                            "Match ${safeIndex + 1} of ${result.count}",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Text(
                            "Selected match: $snippet",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
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
                "Privacy and capability: selected files are read locally only. Plain-text, DOCX and selectable PDF text previews are available. PDF processing is limited to 25 MB, 30 pages and 12,000 preview characters. Scanned-PDF OCR, AI questions, citations, indexing and cloud upload are not connected.",
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
    return try {
        inspectDocumentUnsafe(context, uri)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DocumentInspection(
            "Selected document", null, null, "",
            "The selected document could not be inspected. Check that it is accessible, supported and not damaged, then try again."
        )
    }
}

private fun inspectDocumentUnsafe(context: Context, uri: Uri): DocumentInspection {
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

    val isDocx = name.endsWith(".docx", ignoreCase = true) ||
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    if (isDocx) {
        return try {
            val text = extractDocxText(context, uri)
            val preview = text.take(MAX_PREVIEW_CHARS)
            val truncated = text.length > MAX_PREVIEW_CHARS
            val message = when {
                text.isBlank() -> "DOCX opened locally, but no readable paragraph text was found."
                truncated -> "Word text extracted locally. Preview limited to the first $MAX_PREVIEW_CHARS characters."
                else -> "Word text extracted locally. No upload was performed."
            }
            DocumentInspection(name, mime, size, preview, message)
        } catch (error: Exception) {
            DocumentInspection(
                name, mime, size, "",
                "The Word document could not be extracted safely. It may be damaged, unsupported or exceed the local processing limits."
            )
        }
    }

    val isPdf = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
    if (isPdf) {
        return try {
            val extraction = extractPdfText(context, uri)
            val message = when {
                extraction.text.isBlank() ->
                    "PDF opened locally, but no selectable text was found. Scanned/image-only PDFs need OCR, which is not connected yet."
                extraction.pageLimitReached ->
                    "PDF text extracted locally. Processing was limited to the first $MAX_PDF_PAGES pages."
                extraction.truncated ->
                    "PDF text extracted locally. Preview limited to the first $MAX_PREVIEW_CHARS characters."
                else -> "PDF text extracted locally. No upload was performed."
            }
            DocumentInspection(name, mime, size, extraction.text, message)
        } catch (error: Exception) {
            DocumentInspection(name, mime, size, "",
                "The PDF could not be read safely. It may be password-protected, damaged, or larger than the supported limit.")
        }
    }

    if (!isText) {
        val message = if (name.endsWith(".docx", ignoreCase = true)) {
            "DOCX selected. File details are available; Word text extraction is a future milestone."
        } else {
            "File selected. Local text preview is currently available for plain-text formats."
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
            "The file was selected, but its text could not be read. Check file access and encoding, then try again."
        )
    }
}

private const val MAX_PDF_FILE_BYTES = 25L * 1024L * 1024L
private const val MAX_PDF_PAGES = 30

private data class PdfExtraction(val text: String, val truncated: Boolean, val pageLimitReached: Boolean)

private class LimitedTextWriter(private val maxChars: Int) : Writer() {
    val content = StringBuilder()
    var truncated: Boolean = false
        private set
    override fun write(buffer: CharArray, offset: Int, length: Int) {
        val available = maxChars - content.length
        val accepted = minOf(length, available.coerceAtLeast(0))
        if (accepted > 0) content.append(buffer, offset, accepted)
        if (length > accepted) truncated = true
    }
    override fun flush() = Unit
    override fun close() = Unit
}

private object PdfBoxInitialization {
    @Volatile private var initialized = false
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }
}

private fun extractPdfText(context: Context, uri: Uri): PdfExtraction {
    val tempPdf = File.createTempFile("zayron-document-", ".pdf", context.cacheDir)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempPdf).use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_PDF_FILE_BYTES) { "PDF exceeds supported size" }
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw IllegalStateException("The PDF could not be opened.")
        PdfBoxInitialization.ensureInitialized(context)
        PDDocument.load(tempPdf).use { document ->
            val pageCount = document.numberOfPages
            val writer = LimitedTextWriter(MAX_PREVIEW_CHARS)
            if (pageCount > 0) {
                val stripper = PDFTextStripper()
                stripper.setStartPage(1)
                stripper.setEndPage(minOf(pageCount, MAX_PDF_PAGES))
                stripper.writeText(document, writer)
            }
            return PdfExtraction(writer.content.toString().trim(), writer.truncated, pageCount > MAX_PDF_PAGES)
        }
    } finally {
        tempPdf.delete()
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


/**
 * Reads only the DOCX main document part. Both compressed input and expanded
 * ZIP content are bounded; entry traversal is capped to resist ZIP bombs.
 * XML document declarations/DOCTYPE processing are disabled and rejected.
 */
private const val MAX_DOCX_ARCHIVE_BYTES = 10L * 1024L * 1024L
private const val MAX_DOCX_TOTAL_EXPANDED_BYTES = 8L * 1024L * 1024L
private const val MAX_DOCX_XML_BYTES = 4 * 1024 * 1024
private const val MAX_DOCX_ENTRIES = 256

private class ArchiveByteLimitInputStream(
    input: java.io.InputStream,
    private val maxBytes: Long
) : java.io.FilterInputStream(input) {
    private var bytesRead = 0L
    override fun read(): Int {
        if (bytesRead >= maxBytes) {
            val extra = super.read()
            if (extra < 0) return -1
            throw java.io.IOException("DOCX archive exceeds the safe input limit")
        }
        val value = super.read()
        if (value >= 0) bytesRead++
        return value
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRead >= maxBytes) {
            val extra = super.read()
            if (extra < 0) return -1
            throw java.io.IOException("DOCX archive exceeds the safe input limit")
        }
        val allowed = minOf(length.toLong(), maxBytes - bytesRead).toInt()
        val count = super.read(buffer, offset, allowed)
        if (count > 0) bytesRead += count
        return count
    }
}

private fun extractDocxText(context: Context, uri: Uri): String {
    val xmlBytes = context.contentResolver.openInputStream(uri)?.use { raw ->
        java.util.zip.ZipInputStream(ArchiveByteLimitInputStream(raw, MAX_DOCX_ARCHIVE_BYTES)).use { zip ->
            var found: ByteArray? = null
            var totalExpanded = 0L
            var entryCount = 0
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                require(entryCount <= MAX_DOCX_ENTRIES) { "DOCX contains too many archive entries" }
                val isDocumentXml = entry.name == "word/document.xml" && !entry.isDirectory
                require(!isDocumentXml || found == null) { "DOCX contains duplicate main document parts" }
                val output = if (isDocumentXml) java.io.ByteArrayOutputStream() else null
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    totalExpanded += count
                    require(totalExpanded <= MAX_DOCX_TOTAL_EXPANDED_BYTES) {
                        "DOCX expanded content exceeds the safe limit"
                    }
                    if (output != null) {
                        require(output.size().toLong() + count <= MAX_DOCX_XML_BYTES) {
                            "DOCX document XML exceeds the safe extraction limit"
                        }
                        output.write(buffer, 0, count)
                    }
                }
                if (output != null) found = output.toByteArray()
                zip.closeEntry()
                entry = zip.nextEntry
            }
            found ?: throw IllegalArgumentException("Word document content was not found")
        }
    } ?: throw IllegalStateException("The document could not be opened")

    val parser = android.util.Xml.newPullParser()
    parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    parser.setInput(java.io.ByteArrayInputStream(xmlBytes), "UTF-8")
    val result = StringBuilder()
    var event = parser.eventType
    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && result.length < MAX_PREVIEW_CHARS + 1) {
        when (event) {
            org.xmlpull.v1.XmlPullParser.DOCDECL ->
                throw IllegalArgumentException("DOCX XML document declarations are not supported")
            org.xmlpull.v1.XmlPullParser.START_TAG -> if (parser.name == "t") {
                val text = parser.nextText()
                val remaining = MAX_PREVIEW_CHARS + 1 - result.length
                if (remaining > 0) result.append(text.take(remaining))
            }
            org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "p") {
                if (result.length < MAX_PREVIEW_CHARS + 1) result.append('\n')
            }
        }
        event = parser.next()
    }
    return result.toString().take(MAX_PREVIEW_CHARS).trim()
}
