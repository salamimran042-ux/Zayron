package ai.zayron.client.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keeps document inspection and career drafting together in one Document AI workspace. */
@Composable
fun DocumentCareerWorkspaceScreen(modifier: Modifier = Modifier) {
    var selectedWorkspace by rememberSaveable { mutableStateOf("Documents") }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedWorkspace == "Documents") Button(onClick = { selectedWorkspace = "Documents" }) { Text("Documents") }
            else OutlinedButton(onClick = { selectedWorkspace = "Documents" }) { Text("Documents") }
            if (selectedWorkspace == "Career") Button(onClick = { selectedWorkspace = "Career" }) { Text("Career tools") }
            else OutlinedButton(onClick = { selectedWorkspace = "Career" }) { Text("Career tools") }
        }
        if (selectedWorkspace == "Career") CareerAssistantScreen(Modifier.weight(1f))
        else DocumentAiScreen(Modifier.weight(1f))
    }
}
