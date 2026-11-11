package ai.zayron.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import ai.zayron.client.ui.dashboard.DashboardScreen
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
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
import ai.zayron.client.data.auth.SignUpResult
import ai.zayron.client.data.chat.SharedPreferencesCloudConversationIdStore
import ai.zayron.client.ui.chat.ChatScreen
import ai.zayron.client.ui.settings.SettingsScreen
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
    Image(
        painter = painterResource(R.drawable.zayron_launcher),
        contentDescription = "Zayron AI logo",
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun ZayronRoot(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val authRepository = remember(context) {
        BackendAuthRepository(AndroidKeystoreSessionStore(context.applicationContext))
    }
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var restoringSession by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val cloudConversationIds = remember(context) {
        SharedPreferencesCloudConversationIdStore(context.applicationContext)
    }

    LaunchedEffect(authRepository) {
        session = authRepository.restoreSession()
        restoringSession = false
    }

    when {
        restoringSession -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ZayronLogo(112)
                    Spacer(Modifier.height(14.dp))
                    Text("ZAYRON AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Think Beyond Limits.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(color = Color(0xFF16BFFF))
                Spacer(Modifier.height(12.dp))
                Text("Preparing your workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        session != null -> Workspace(
            session = session!!,
            darkTheme = darkTheme,
            onToggleTheme = onToggleTheme,
            onSignOut = {
                scope.launch {
                    try {
                        authRepository.signOut()
                    } finally {
                        cloudConversationIds.clear()
                        session = null
                    }
                }
            }
        )
        else -> SignIn(
            authRepository = authRepository,
            onAuthenticated = {
                cloudConversationIds.clear()
                session = it
            }
        )
    }
}


@Composable
private fun WorkspaceInfoScreen(
    section: String,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (section) {
        "Credits" -> "Credits & usage"
        else -> section
    }
    val description = when (section) {
        "Projects" -> "Keep related conversations, files and AI work together in one place."
        "History" -> "Your conversations belong to your Zayron account and will appear here as history sync is completed."
        "Plugins" -> "Connect trusted services and tools to extend what Zayron AI can do."
        else -> "Review your available credits, usage and transaction history."
    }
    val status = when (section) {
        "Projects" -> "Project workspace is being connected"
        "History" -> "Conversation history sync is being connected"
        "Plugins" -> "Plugin connections are not active yet"
        else -> "Live wallet and credit balance are not connected yet"
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "This area is available in the workspace navigation. We will only show real account data and working features here; no sample credit balance, fake transactions or pretend integrations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Return to Zayron AI")
        }
    }
}

@Composable
private fun SignIn(
    authRepository: BackendAuthRepository,
    onAuthenticated: (AuthSession) -> Unit
) {
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var isPasswordRecovery by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ZayronLogo(104)
                Spacer(Modifier.height(10.dp))
                Text("ZAYRON AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Think Beyond Limits.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.height(32.dp))
            Text(
                when {
                    isPasswordRecovery -> "Reset your password"
                    isSignUp -> "Create your account"
                    else -> "Welcome back"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                when {
                    isPasswordRecovery -> "We'll send a password reset link to your email."
                    isSignUp -> "Sign up to start using your Zayron AI workspace."
                    else -> "Sign in to your AI workspace"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            if (isSignUp && !isPasswordRecovery) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; errorMessage = null; statusMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your name (optional)") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null; statusMessage = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            if (!isPasswordRecovery) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null; statusMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
            if (isSignUp && !isPasswordRecovery) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text(
                    "Use at least 8 characters.",
                    modifier = Modifier.align(Alignment.Start).padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isSignUp && !isPasswordRecovery) {
                TextButton(
                    onClick = { isPasswordRecovery = true; statusMessage = null; errorMessage = null },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Forgot password?") }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    errorMessage = null
                    statusMessage = null
                    when {
                        isPasswordRecovery -> scope.launch {
                            isLoading = true
                            val failure = authRepository.requestPasswordReset(email.trim())
                            if (failure == null) {
                                statusMessage = "If an account exists for this email, a password reset link will be sent."
                            } else errorMessage = failure.toUserMessage()
                            isLoading = false
                        }
                        isSignUp -> {
                            if (password.length < 8) {
                                errorMessage = "Your password must be at least 8 characters."
                            } else if (password != confirmPassword) {
                                errorMessage = "The passwords do not match."
                            } else {
                                val submittedPassword = password.toCharArray()
                                password = ""
                                confirmPassword = ""
                                scope.launch {
                                    isLoading = true
                                    when (val result = authRepository.signUp(email.trim(), submittedPassword, displayName)) {
                                        is SignUpResult.Registered -> {
                                            if (result.session != null) {
                                                onAuthenticated(result.session)
                                            } else {
                                                isSignUp = false
                                                statusMessage = "Your account has been created. Check your email to verify it, then sign in."
                                            }
                                        }
                                        is SignUpResult.Failed -> errorMessage = result.reason.toUserMessage()
                                    }
                                    isLoading = false
                                }
                            }
                        }
                        else -> {
                            val submittedPassword = password.toCharArray()
                            password = ""
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
                        }
                    }
                },
                enabled = !isLoading && email.contains("@") &&
                    (isPasswordRecovery || password.isNotEmpty()) &&
                    (!isSignUp || (displayName.length <= 80 && confirmPassword.isNotEmpty())),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Please wait…")
                } else {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isPasswordRecovery -> "Send reset link"
                            isSignUp -> "Create account"
                            else -> "Sign in"
                        }
                    )
                }
            }
            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp))
            }
            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                when {
                    isPasswordRecovery -> isPasswordRecovery = false
                    else -> isSignUp = !isSignUp
                }
                password = ""
                confirmPassword = ""
                errorMessage = null
                statusMessage = null
            }) {
                Text(
                    when {
                        isPasswordRecovery -> "Back to sign in"
                        isSignUp -> "Already have an account? Sign in"
                        else -> "New to Zayron AI? Create an account"
                    }
                )
            }
            if (!AiServiceConfig.isConfigured) {
                Text(
                    "The secure backend is not connected yet. Account access will be available when its HTTPS service is ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
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
private fun Workspace(
    session: AuthSession,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("AI Chat") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = mode != "AI Chat") {
        mode = "AI Chat"
    }

    val sections = listOf(
        "YOUR ASSISTANT" to listOf(
            "Home" to "Home",
            "Zayron AI" to "AI Chat"
        ),
        "YOUR WORK" to listOf(
            "Projects" to "Projects",
            "History" to "History",
            "Plugins" to "Plugins"
        ),
        "ACCOUNT" to listOf(
            "Credits & usage" to "Credits",
            "Settings" to "Settings"
        )
    )

    fun iconFor(route: String) = when (route) {
        "Home" -> Icons.Default.Home
        "AI Chat" -> Icons.Default.ChatBubbleOutline
        "Video Generation" -> Icons.Default.VideoLibrary
        "Image Editing" -> Icons.Default.Image
        "App Builder" -> Icons.Default.PhoneAndroid
        "Document AI" -> Icons.Default.Description
        "Settings" -> Icons.Default.Settings
        "Projects" -> Icons.Default.FolderOpen
        "History" -> Icons.Default.History
        "Plugins" -> Icons.Default.Extension
        "Credits" -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.Mic
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 24.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ZayronLogo(44)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("ZAYRON AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Your AI workspace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    sections.forEach { (heading, destinations) ->
                        Text(
                            heading,
                            modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        destinations.forEach { (label, route) ->
                            NavigationDrawerItem(
                                icon = { Icon(iconFor(route), contentDescription = null) },
                                label = { Text(label) },
                                selected = mode == route,
                                onClick = {
                                    mode = route
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Text(
                        "More workspace areas will appear here as they are completed.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ZayronLogo(34)
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (mode) {
                    "Home" -> DashboardScreen(
                        onToolSelected = { selected -> mode = selected },
                        modifier = Modifier.fillMaxSize()
                    )
                    "AI Chat" -> ChatScreen(Modifier.fillMaxSize())
                    "Projects", "History", "Plugins", "Credits" -> WorkspaceInfoScreen(
                        section = mode,
                        onOpenChat = { mode = "AI Chat" },
                        modifier = Modifier.fillMaxSize()
                    )
                    "Settings" -> SettingsScreen(
                        darkTheme = darkTheme,
                        accountEmail = session.email,
                        onToggleTheme = onToggleTheme,
                        onSignOut = onSignOut,
                        onOpenCredits = { mode = "Credits" },
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> ToolWorkspaceScreen(mode = mode, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
