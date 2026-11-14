package ai.zayron.client.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ai.zayron.client.ui.tools.ToolWorkspaceScreen
import ai.zayron.client.data.skills.SkillIntentRouter
import androidx.lifecycle.viewmodel.compose.viewModel

private val ChatPurple = Color(0xFF5146D8)

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = viewModel()
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var activeSkill by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSkillRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val messages = chatViewModel.messages
    val pendingAttachments = chatViewModel.pendingAttachments
    val canSend = !chatViewModel.isSending &&
        pendingAttachments.none { it.isUploading || it.error != null } &&
        (draft.isNotBlank() || pendingAttachments.any { it.uploaded != null })
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) chatViewModel.addAttachments(selectedUris)
    }

    val sendDraft = {
        val message = draft.trim()
        val attachments = pendingAttachments.mapNotNull { it.uploaded }
        if ((message.isNotEmpty() || attachments.isNotEmpty()) && !chatViewModel.isSending &&
            pendingAttachments.none { it.isUploading || it.error != null }
        ) {
            val routedSkill = if (attachments.isEmpty()) SkillIntentRouter.classify(message) else null
            if (routedSkill != null) {
                activeSkill = routedSkill.workspaceTitle
                activeSkillRequest = message
            } else {
                chatViewModel.sendMessage(
                    message.ifBlank { "Please review the attached file(s)." },
                    attachments
                )
                chatViewModel.clearPendingAttachments()
            }
            draft = ""
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local conversation?") },
            text = { Text("This permanently removes the messages saved on this device. It does not affect any online account.") },
            confirmButton = {
                TextButton(onClick = {
                    chatViewModel.clearConversation()
                    confirmClear = false
                }) { Text("Clear messages") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep messages") }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeSkill != null) {
                IconButton(onClick = { activeSkill = null; activeSkillRequest = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to conversation")
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Zayron AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    activeSkill?.let { "$it • inside your conversation" } ?: "One assistant. Many capabilities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (messages.isNotEmpty() && activeSkill == null) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear conversation")
                }
            }
        }

        chatViewModel.serviceMessage?.let { notice ->
            Text(
                notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
        }

        if (activeSkill != null) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ToolWorkspaceScreen(
                    mode = activeSkill!!,
                    initialRequest = activeSkillRequest,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(ChatPurple.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ChatPurple, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text("What can I help you with?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Chat naturally. Zayron brings its creative and productivity skills into this same conversation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { activeSkill = "Video Generation"; activeSkillRequest = null }, modifier = Modifier.weight(1f)) {
                            Text("Create a video")
                        }
                        OutlinedButton(onClick = { activeSkill = "Image Editing"; activeSkillRequest = null }, modifier = Modifier.weight(1f)) {
                            Text("Edit an image")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { activeSkill = "Document AI"; activeSkillRequest = null }, modifier = Modifier.weight(1f)) {
                            Text("Read a document")
                        }
                        OutlinedButton(onClick = { activeSkill = "App Builder"; activeSkillRequest = null }, modifier = Modifier.weight(1f)) {
                            Text("Build an app")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { activeSkill = "Voice Assistant"; activeSkillRequest = null }, modifier = Modifier.weight(1f)) {
                            Text("Use my voice")
                        }
                        OutlinedButton(onClick = { draft = "I have a question. Please help me." }, modifier = Modifier.weight(1f)) {
                            Text("Ask anything")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role == ChatRole.USER
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            Modifier.widthIn(max = 320.dp).clip(
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isUser) 18.dp else 5.dp,
                                    bottomEnd = if (isUser) 5.dp else 18.dp
                                )
                            ).background(if (isUser) ChatPurple else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 15.dp, vertical = 11.dp)
                        ) {
                            if (message.text.isNotBlank()) {
                                Text(
                                    message.text,
                                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            message.attachments.forEach { attachment ->
                                Text(
                                    "📎 ${attachment.displayName}",
                                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    message.deliveryState == DeliveryState.SENT -> if (isUser) "Sent securely" else "Response received"
                                    message.deliveryState == DeliveryState.PENDING -> if (isUser) "Sending…" else "Generating…"
                                    message.deliveryState == DeliveryState.FAILED -> if (isUser) "Not delivered" else "Response interrupted"
                                    isUser -> "Saved on this device • not sent"
                                    else -> "Local assistant message"
                                },
                                color = if (isUser) Color.White.copy(alpha = 0.75f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        if (pendingAttachments.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                pendingAttachments.forEach { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(item.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    when {
                                        item.isUploading -> "Uploading ${(item.progress * 100).toInt()}%"
                                        item.error != null -> item.error
                                        else -> "Private upload complete • ready to send"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                if (item.isUploading) {
                                    LinearProgressIndicator(
                                        progress = item.progress,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    )
                                }
                            }
                            if (item.error != null) {
                                IconButton(onClick = { chatViewModel.retryAttachment(item.localId) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry upload")
                                }
                            }
                            IconButton(onClick = { chatViewModel.removeAttachment(item.localId) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 5.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        attachmentPicker.launch(
                            arrayOf(
                                "application/pdf", "text/plain", "text/markdown", "text/csv",
                                "application/json", "image/png", "image/jpeg", "image/webp"
                            )
                        )
                    },
                    enabled = !chatViewModel.isSending
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach files")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Zayron AI...") },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        if (chatViewModel.isSending) chatViewModel.cancelCurrentRequest() else sendDraft()
                    },
                    enabled = chatViewModel.isSending || canSend,
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(
                        if (chatViewModel.isSending || canSend) ChatPurple else ChatPurple.copy(alpha = 0.35f)
                    )
                ) {
                    if (chatViewModel.isSending) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stop generation",
                            tint = Color.White
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = Color.White)
                    }
                }
            }
        }

        Text(
            "Files upload privately to your Zayron account. They are added to the local chat message when you send it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
    }
}
