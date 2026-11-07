package ai.zayron.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.zayron.client.data.auth.AndroidKeystoreSessionStore
import ai.zayron.client.data.auth.AuthFailureCode
import ai.zayron.client.data.auth.AuthRepository
import ai.zayron.client.data.auth.AuthResult
import ai.zayron.client.data.auth.AuthSession
import ai.zayron.client.data.auth.BackendAuthRepository
import ai.zayron.client.data.auth.SignInCredentials
import ai.zayron.client.ui.chat.ChatScreen
import ai.zayron.client.ui.dashboard.DashboardScreen
import ai.zayron.client.ui.tools.ToolWorkspaceScreen
import kotlinx.coroutines.launch

private val ZayronPurple = Color(0xFF5146D8)
private val ZayronModes = listOf(
    "Home",
    "AI Chat",
    "Video Generation",
    "Image Editing",
    "Document AI",
    "App Builder",
    "Voice Assistant"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkTheme by rememberSaveable { mutableStateOf(false) }
            val colors = if (darkTheme) {
                darkColorScheme(primary = Color(0xFFB8B0FF))
            } else {
                lightColorScheme(primary = ZayronPurple, background = Color(0xFFFAFAFD))
            }
            MaterialTheme(colorScheme = colors) {
                ZayronRoot(darkTheme = darkTheme, onToggleTheme = { darkTheme = !darkTheme })
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
private fun ZayronRoot(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val authRepository = remember(context) {
        BackendAuthRepository(AndroidKeystoreSessionStore(context.applicationContext))
    }
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var restoringSession by remember { mutableStateOf(true) }
    var preview by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(authRepository) {
        session = authRepository.restoreSession()
        restoringSession = false
    }

    when {
        restoringSession -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        session != null || preview -> Workspace(
            darkTheme = darkTheme,
            onToggleTheme = onToggleTheme
        )
        else -> SignIn(
            authRepository = authRepository,
            onAuthenticated = { session = it },
            onPreview = { preview = true }
        )
    }
}

@Composable
private fun SignIn(
    authRepository: AuthRepository,
    onAuthenticated: (AuthSession) -> Unit,
    onPreview: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val submittedPassword = password.toCharArray()
                    password = ""
                    errorMessage = null
                    scope.launch {
                        isLoading = true
                        when (val result = authRepository.signIn(
                            SignInCredentials(email.trim(), submittedPassword)
                        )) {
                            is AuthResult.Authenticated -> onAuthenticated(result.session)
                            is AuthResult.Failed -> errorMessage = result.reason.toUserMessage()
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading && email.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in…")
                } else {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in")
                }
            }
            errorMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            if (!AiServiceConfig.isConfigured) {
                Text(
                    "The secure backend is not deployed and connected yet. Sign-in will become available when its HTTPS address is configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            TextButton(onClick = onPreview) { Text("Preview workspace") }
        }
    }
}

private fun AuthFailureCode.toUserMessage(): String = when (this) {
    AuthFailureCode.INVALID_CREDENTIALS -> "The email or password is incorrect."
    AuthFailureCode.ACCOUNT_DISABLED -> "This account is currently unavailable."
    AuthFailureCode.EMAIL_NOT_VERIFIED -> "Please verify your email before signing in."
    AuthFailureCode.SESSION_EXPIRED -> "Your session has expired. Please sign in again."
    AuthFailureCode.NETWORK_UNAVAILABLE -> "Unable to reach the server. Check your internet connection."
    AuthFailureCode.SERVICE_UNAVAILABLE -> "The authentication service is not ready yet. Please try again later."
    AuthFailureCode.UNKNOWN -> "We couldn't complete sign-in. Please try again."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Workspace(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("Home") }
    var menu by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme"
                        )
                    }
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.padding(end = 8.dp)) {
                            Text(mode, maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, "Select workspace")
                        }
                        DropdownMenu(menu, { menu = false }) {
                            ZayronModes.forEach { item ->
                                DropdownMenuItem(text = { Text(item) }, onClick = { mode = item; menu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (mode) {
                "Home" -> DashboardScreen(
                    onToolSelected = { selectedTool -> mode = selectedTool },
                    modifier = Modifier.fillMaxSize()
                )
                "AI Chat" -> ChatScreen(Modifier.fillMaxSize())
                else -> ToolWorkspaceScreen(mode = mode, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
