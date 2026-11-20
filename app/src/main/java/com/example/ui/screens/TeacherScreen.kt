package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.StudyToolsViewModel

@Composable
fun TeacherScreen(viewModel: StudyToolsViewModel) {
    val teacherState by viewModel.teacherState.collectAsStateWithLifecycle()
    val weakTopics by viewModel.weakTopics.collectAsStateWithLifecycle()

    val modes = listOf(
        "Explain Chapter",
        "Step-by-Step Solve",
        "MCQ Test",
        "Short Notes",
        "Topic Summary",
        "Weak Topic Coach"
    )
    var selectedMode by remember { mutableStateOf(modes.first()) }
    var promptInput by remember { mutableStateOf("") }
    var useBooksFirst by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("SUKOON Personal Teacher Studio", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Targeted study tools, MCQ drills & weak topic coaching for Owner",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Weak Topics detected from Owner's real PYQ data
        if (weakTopics.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text("Weak Topics Detected (Unsolved/Hard PYQs)", style = MaterialTheme.typography.titleSmall)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weakTopics.take(5).forEach { (topic, count) ->
                            AssistChip(
                                onClick = {
                                    selectedMode = "Weak Topic Coach"
                                    promptInput = "Coach me on weak topic: $topic ($count unsolved/hard PYQs)"
                                },
                                label = { Text("$topic ($count)") }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = { Text(mode) }
                )
            }
        }

        OutlinedTextField(
            value = promptInput,
            onValueChange = { promptInput = it },
            label = { Text("Chapter, Topic, or Question for $selectedMode") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().testTag("teacher_input_field")
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useBooksFirst, onCheckedChange = { useBooksFirst = it })
            Text("Search Owner's local books first (Book-First RAG)", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { viewModel.runTeacherTask(selectedMode, promptInput, useBooksFirst) },
            enabled = promptInput.isNotBlank() && !teacherState.isLoading,
            modifier = Modifier.fillMaxWidth().testTag("teacher_generate_button")
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Run $selectedMode")
        }

        if (teacherState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        teacherState.error?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (teacherState.outputText.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (teacherState.citationsText.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = teacherState.citationsText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    MarkdownText(markdown = teacherState.outputText)
                }
            }
        }
    }
}
