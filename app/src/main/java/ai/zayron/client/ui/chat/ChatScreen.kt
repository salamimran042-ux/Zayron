package ai.zayron.client.ui.chat

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

    val sendDraft = {
        val message = draft.trim()
        if (message.isNotEmpty() && !chatViewModel.isSending) {
            val routedSkill = SkillIntentRouter.classify(message)
            if (routedSkill != null) {
                activeSkill = routedSkill.workspaceTitle
                activeSkillRequest = message
            } else {
                chatViewModel.sendMessage(message)
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
                            Text(
                                message.text,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    enabled = chatViewModel.isSending || draft.isNotBlank(),
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(
                        if (chatViewModel.isSending || draft.isNotBlank()) ChatPurple else ChatPurple.copy(alpha = 0.35f)
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
            "Your messages stay on this device unless a configured Zayron backend accepts them. Local-only messages are never uploaded as chat history.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
    }
}
