package ai.zayron.client.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val DashboardPurple = Color(0xFF5146D8)

private data class DashboardTool(
    val title: String,
    val description: String,
    val readiness: String,
    val icon: ImageVector,
    val destination: String
)

private val dashboardTools = listOf(
    DashboardTool(
        "Conversational AI",
        "Ask questions, explore ideas and work through tasks.",
        "Backend connection required for live AI",
        Icons.Default.AutoAwesome,
        "AI Chat"
    ),
    DashboardTool(
        "Image-to-Video",
        "Choose an image and prepare a motion prompt.",
        "Workspace preview • generation service pending",
        Icons.Default.VideoLibrary,
        "Video Generation"
    ),
    DashboardTool(
        "AI Inpaint & Eraser",
        "Select a photo and prepare an area mask.",
        "Local mask tools • AI processing pending",
        Icons.Default.Image,
        "Image Editing"
    ),
    DashboardTool(
        "Document AI",
        "Open supported files and inspect extracted text.",
        "Local document reading • AI analysis pending",
        Icons.Default.Description,
        "Document AI"
    ),
    DashboardTool(
        "AI App Builder",
        "Describe the app you want to create.",
        "Project brief preview • build service pending",
        Icons.Default.PhoneAndroid,
        "App Builder"
    ),
    DashboardTool(
        "Voice Assistant",
        "Dictate text and listen to it read aloud.",
        "Device speech tools • AI voice service pending",
        Icons.Default.Mic,
        "Voice Assistant"
    )
)

@Composable
fun DashboardScreen(
    onToolSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Your AI workspace", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose a tool to get started. Some AI services are still being connected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 155.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(dashboardTools, key = { it.destination }) { tool ->
                Card(
                    onClick = { onToolSelected(tool.destination) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 174.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = DashboardPurple
                        )
                        Text(tool.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            tool.readiness,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
