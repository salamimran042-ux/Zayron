package ai.zayron.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private data class ZayronTool(
    val title: String,
    val description: String,
    val route: String,
    val icon: ImageVector
)

private val tools = listOf(
    ZayronTool("Conversational AI", "Chat, ask questions and create", "chat", Icons.Default.Chat),
    ZayronTool("Image-to-Video", "Turn images into motion", "video", Icons.Default.Image),
    ZayronTool("AI Inpaint & Eraser", "Edit and remove image elements", "photo", Icons.Default.Photo),
    ZayronTool("Document AI", "Understand and ask about files", "documents", Icons.Default.Description),
    ZayronTool("AI App Builder", "Plan and generate app projects", "builder", Icons.Default.AutoAwesome),
    ZayronTool("Voice Assistant", "Speak and listen naturally", "voice", Icons.Default.Mic)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZayronTheme { ZayronApp() } }
    }
}

@Composable
private fun ZayronTheme(content: @Composable () -> Unit) {
    var darkMode by rememberSaveable { mutableStateOf(false) }
    val light = lightColorScheme(
        primary = Color(0xFF5146D8),
        secondary = Color(0xFF168C83),
        background = Color(0xFFF7F8FC),
        surface = Color.White
    )
    val dark = darkColorScheme()
    MaterialTheme(colorScheme = if (darkMode) dark else light, content = content)
}

@Composable
private fun ZayronApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            DashboardScreen(onToolSelected = { nav.navigate(it) })
        }
        tools.forEach { tool ->
            composable(tool.route) {
                ToolPlaceholderScreen(tool = tool, onBack = { nav.popBackStack() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(onToolSelected: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ZAYRON AI", fontWeight = FontWeight.Bold)
                        Text("Think Beyond Limits.", style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Your AI workspace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Choose a tool to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tools) { tool ->
                    Card(
                        modifier = Modifier.fillMaxWidth().height(166.dp).clickable { onToolSelected(tool.route) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    tool.icon,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(tool.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolPlaceholderScreen(tool: ZayronTool, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(tool.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(tool.description, style = MaterialTheme.typography.bodyLarge)
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Foundation screen", fontWeight = FontWeight.SemiBold)
                    Text(
                        "This screen is part of the Zayron AI app foundation. Its AI workflow is not connected yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (AiServiceConfig.isConfigured) "Backend URL configured; service integration still required."
                        else "Backend not configured. No AI requests are being sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
