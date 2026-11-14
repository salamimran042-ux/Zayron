package ai.zayron.client.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Offline-first career tools. Draft generation stays on-device. */
@Composable
fun CareerAssistantScreen(modifier: Modifier = Modifier) {
    var documentType by rememberSaveable { mutableStateOf("CV") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var targetRole by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var skills by rememberSaveable { mutableStateOf("") }
    var experience by rememberSaveable { mutableStateOf("") }
    var education by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var jobDescription by rememberSaveable { mutableStateOf("") }

    val clipboard = LocalClipboardManager.current
    val output = remember(
        documentType, fullName, email, phone, location, targetRole,
        summary, skills, experience, education, company, jobDescription
    ) {
        val profile = CareerProfile(
            fullName, email, phone, location, targetRole,
            summary, skills, experience, education
        )
        if (documentType == "CV") CareerDocumentGenerator.buildResume(profile)
        else CareerDocumentGenerator.buildCoverLetter(profile, company, jobDescription)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Career Assistant", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Create a professional CV or cover-letter draft offline. Your details stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (documentType == "CV") Button(onClick = { documentType = "CV" }) { Text("Build CV") }
            else OutlinedButton(onClick = { documentType = "CV" }) { Text("Build CV") }
            if (documentType == "Cover letter") Button(onClick = { documentType = "Cover letter" }) { Text("Cover letter") }
            else OutlinedButton(onClick = { documentType = "Cover letter" }) { Text("Cover letter") }
        }

        Text("Your details", style = MaterialTheme.typography.titleMedium)
        CareerInput(fullName, { fullName = it }, "Full name")
        CareerInput(email, { email = it }, "Email address")
        CareerInput(phone, { phone = it }, "Phone number")
        CareerInput(location, { location = it }, "City / location")
        CareerInput(targetRole, { targetRole = it }, "Target job title")

        if (documentType == "CV") {
            Text("CV content", style = MaterialTheme.typography.titleMedium)
            CareerInput(summary, { summary = it }, "Professional summary", 3)
            CareerInput(skills, { skills = it }, "Skills (separate with commas)", 2)
            CareerInput(experience, { experience = it }, "Work experience and achievements", 4)
            CareerInput(education, { education = it }, "Education and qualifications", 3)
        } else {
            Text("Job application", style = MaterialTheme.typography.titleMedium)
            CareerInput(company, { company = it }, "Company name")
            CareerInput(jobDescription, { jobDescription = it }, "Job description (optional)", 4)
            CareerInput(summary, { summary = it }, "Why you are a good fit", 3)
            CareerInput(skills, { skills = it }, "Relevant skills", 2)
            CareerInput(experience, { experience = it }, "Relevant experience and achievements", 3)
            CareerInput(education, { education = it }, "Relevant education (optional)", 2)
        }

        Text("Your draft", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Text(output, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { clipboard.setText(AnnotatedString(output)) },
            enabled = output.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy " + if (documentType == "CV") "CV" else "cover letter")
        }
        Text(
            "Zayron only uses details you enter. Review every section before sending. AI tailoring, job matching, PDF export and cloud sync are future steps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CareerInput(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        maxLines = if (minLines == 1) 1 else 8
    )
}
