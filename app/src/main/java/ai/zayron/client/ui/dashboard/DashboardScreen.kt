package ai.zayron.client.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    DashboardTool("Conversational AI", "Ask questions, explore ideas and work through tasks.", "Live AI connection pending", Icons.Default.AutoAwesome, "AI Chat"),
    DashboardTool("Image-to-Video", "Turn a still image into a planned moving scene.", "Workspace preview • service pending", Icons.Default.VideoLibrary, "Video Generation"),
    DashboardTool("AI Inpaint & Eraser", "Select areas of a photo for editing or removal.", "Local mask tools • AI pending", Icons.Default.Image, "Image Editing"),
    DashboardTool("Document AI", "Open supported files and inspect extracted text.", "Local reading • AI analysis pending", Icons.Default.Description, "Document AI"),
    DashboardTool("AI App Builder", "Describe the Android app you want to create.", "Project brief • build service pending", Icons.Default.PhoneAndroid, "App Builder"),
    DashboardTool("Voice Assistant", "Dictate text and listen to it read aloud.", "Device speech tools • AI pending", Icons.Default.Mic, "Voice Assistant")
)

@Composable
fun DashboardScreen(
    onToolSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your AI workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "One place to think, create, edit, build and explore.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    Text("Start with a conversation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(
                    "Ask a question, develop an idea or plan your next creation with Zayron AI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Button(onClick = { onToolSelected("AI Chat") }) {
                    Text("Open Conversational AI")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Explore your tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { onToolSelected("AI Chat") }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Chat")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 155.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(dashboardTools, key = { it.destination }) { tool ->
                Card(
                    onClick = { onToolSelected(tool.destination) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(tool.icon, contentDescription = null, tint = DashboardPurple, modifier = Modifier.size(25.dp))
                        Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(tool.readiness, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
