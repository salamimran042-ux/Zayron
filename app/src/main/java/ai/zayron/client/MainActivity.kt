package ai.zayron.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private val ZayronPurple = Color(0xFF5146D8)
private val ZayronModes = listOf("AI Chat", "Video Generation", "Image Editing", "Document AI", "App Builder", "Voice Assistant")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = ZayronPurple, background = Color(0xFFFAFAFD))) {
                ZayronRoot()
            }
        }
    }
}

@Composable
private fun ZayronLogo(size: Int = 56) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(ZayronPurple), Alignment.Center) {
        Text("Z", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun ZayronRoot() {
    var preview by rememberSaveable { mutableStateOf(false) }
    if (preview) Workspace() else SignIn(onPreview = { preview = true })
}

@Composable
private fun SignIn(onPreview: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ZayronLogo(78)
            Spacer(Modifier.height(16.dp))
            Text("ZAYRON AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Think Beyond Limits.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(40.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Sign in to your AI workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(16.dp))
            Button({}, enabled = false, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Sign in")
            }
            Text("Secure sign-in requires the authentication backend, which is not connected yet.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp))
            TextButton(onClick = onPreview) { Text("Preview workspace") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Workspace() {
    var mode by rememberSaveable { mutableStateOf("AI Chat") }
    var menu by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZayronLogo(36)
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text("ZAYRON AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Think Beyond Limits.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.padding(end = 8.dp)) {
                            Text(mode, maxLines = 1); Icon(Icons.Default.ArrowDropDown, "Select mode")
                        }
                        DropdownMenu(menu, { menu = false }) {
                            ZayronModes.forEach { item ->
                                DropdownMenuItem(text = { Text(item) }, onClick = { mode = item; menu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFFAFAFD)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mode == "AI Chat") {
                if (messages.isEmpty()) {
                    Spacer(Modifier.height(58.dp))
                    Icon(Icons.Default.AutoAwesome, null, tint = ZayronPurple, modifier = Modifier.size(34.dp))
                    Text("What can I help you with?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Your AI workspace starts here. Ask a question, explore an idea or create something new.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(messages) { message ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Text(message, Modifier.padding(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f, fill = false))
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Message Zayron AI...") }, shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        TextButton(enabled = draft.isNotBlank(), onClick = {
                            messages.add(draft.trim()); draft = ""
                        }) { Text("Send") }
                    }
                )
                Text("Interface preview — AI replies activate after the secure backend is connected.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp))
            } else {
                Spacer(Modifier.height(48.dp))
                Text(mode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("This unified workspace mode is selected. Its full tool workflow and secure service connection are the next implementation step.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text("Mode switch is active. This tool is not connected yet.", Modifier.padding(18.dp))
                }
            }
        }
    }
}
