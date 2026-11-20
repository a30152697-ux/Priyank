package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.identity.SukoonIdentity
import com.example.core.preferences.ThemePreference
import com.example.data.ai.AiProviderType
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var editingKeyProvider by remember { mutableStateOf<AiProviderType?>(null) }
    var keyInput by remember { mutableStateOf("") }
    var customModelInput by remember(prefs.customModelId) { mutableStateOf(prefs.customModelId) }
    var proxyUrlInput by remember(prefs.proxyBaseUrl) { mutableStateOf(prefs.proxyBaseUrl) }
    var proxyTokenInput by remember { mutableStateOf("") }
    var includeFilesInBackup by remember { mutableStateOf(true) }
    var calcInput by remember { mutableStateOf("12 * (4 + 6) / 3") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackup(it, includeFilesInBackup) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.inspectBackupBeforeRestore(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Permanent Identity Banner
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${SukoonIdentity.APP_NAME} — Permanent Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("AI Name: ${SukoonIdentity.AI_NAME}  |  Permanent Owner: ${SukoonIdentity.OWNER_NAME}", style = MaterialTheme.typography.bodySmall)
                Text("Prompt-injection protection active: documents & chats cannot override SUKOON's identity.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        // Theme Mode Selector
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePreference.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themePreference == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        // Multi-Provider AI Setup
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Multi-Provider AI Configuration", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Switch between Google Gemini, OpenAI, and OpenRouter anytime without losing chat history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProviderType.entries.forEach { provider ->
                        FilterChip(
                            selected = prefs.activeProvider == provider,
                            onClick = { viewModel.selectProvider(provider) },
                            label = { Text(provider.displayName) },
                            modifier = Modifier.testTag("provider_chip_${provider.name}")
                        )
                    }
                }

                // Curated models for active provider
                Text("Select Model (${prefs.activeProvider.displayName})", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    prefs.activeProvider.suggestedModels.forEach { model ->
                        FilterChip(
                            selected = !prefs.useCustomModel && prefs.selectedModel == model,
                            onClick = { viewModel.selectModel(model) },
                            label = { Text(model) }
                        )
                    }
                }

                // Custom Model ID
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        label = { Text("Custom Model ID") },
                        placeholder = { Text(prefs.activeProvider.defaultModel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        viewModel.setCustomModel(customModelInput, customModelInput.isNotBlank())
                    }) {
                        Text(if (prefs.useCustomModel) "Active" else "Use")
                    }
                }

                // Encrypted Key Configuration for each provider
                AiProviderType.entries.forEach { provider ->
                    val preview = uiState.keyPreviews[provider] ?: "Not configured"
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
                                Text("Keystore Encrypted: $preview", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = {
                                editingKeyProvider = provider
                                keyInput = ""
                            }) {
                                Text("Set Key")
                            }
                            if (preview != "Not configured") {
                                TextButton(onClick = { viewModel.clearApiKey(provider) }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.testActiveConnection() },
                    enabled = !uiState.isTesting,
                    modifier = Modifier.fillMaxWidth().testTag("test_connection_button")
                ) {
                    Text(if (uiState.isTesting) "Testing ${prefs.activeProvider.displayName}..." else "Test Connection (${prefs.activeProvider.displayName})")
                }

                uiState.testResult?.let { res ->
                    Surface(
                        color = if (res.success) StatusSuccess.copy(alpha = 0.14f) else StatusError.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (res.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = if (res.success) StatusSuccess else StatusError
                            )
                            Text(
                                text = "${res.message}${if (res.latencyMs > 0) " (${res.latencyMs}ms)" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Security & Production Proxy Architecture
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("Security & Backend Proxy", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Local Testing vs Production: Direct client API calls store keys locally via Android Keystore AES-256-GCM, which is convenient for personal testing but cannot prevent traffic inspection on a compromised device. For production distribution, route calls through a secure backend API proxy so provider keys never reside on the client.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use Secure Backend Proxy", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = prefs.useProxyBackend,
                        onCheckedChange = { enabled ->
                            viewModel.saveProxyConfig(proxyUrlInput, enabled, null)
                        }
                    )
                }
                OutlinedTextField(
                    value = proxyUrlInput,
                    onValueChange = { proxyUrlInput = it },
                    label = { Text("Proxy Base URL (HTTPS)") },
                    placeholder = { Text("https://your-sukoon-proxy.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = proxyTokenInput,
                    onValueChange = { proxyTokenInput = it },
                    label = { Text("Proxy Auth Token (optional, stored encrypted)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = {
                    viewModel.saveProxyConfig(
                        proxyUrlInput,
                        prefs.useProxyBackend,
                        proxyTokenInput.takeIf { it.isNotBlank() }
                    )
                    proxyTokenInput = ""
                }) {
                    Text("Save Proxy Settings")
                }
            }
        }

        // RAG, Privacy & Study Behavior Toggles
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Study, RAG & Privacy Controls", style = MaterialTheme.typography.titleMedium)
                SettingSwitchRow(
                    title = "Book-First RAG Search",
                    subtitle = "Search local books & notes before answering",
                    checked = prefs.bookFirstRagEnabled,
                    onCheckedChange = { viewModel.setBookFirstRag(it) }
                )
                SettingSwitchRow(
                    title = "Concise Answers by Default",
                    subtitle = "Short direct answers unless Owner asks for details",
                    checked = prefs.conciseAnswersDefault,
                    onCheckedChange = { viewModel.setConciseDefault(it) }
                )
                SettingSwitchRow(
                    title = "Ask Before Sending Book Snippets Online",
                    subtitle = "Show exact doc & page permissions before sending",
                    checked = prefs.askBeforeSendingBookContext,
                    onCheckedChange = { viewModel.setAskBookTransfer(it) }
                )
                SettingSwitchRow(
                    title = "Ask Before Sending Images to Vision AI",
                    subtitle = "Explicit permission before uploading photos/PDF pages",
                    checked = prefs.askBeforeSendingImagesOnline,
                    onCheckedChange = { viewModel.setAskImageTransfer(it) }
                )
                SettingSwitchRow(
                    title = "Ask Permission Before Saving Corrections",
                    subtitle = "Confirm before adding chat corrections to Memory",
                    checked = prefs.askBeforeSavingMemory,
                    onCheckedChange = { viewModel.setAskMemorySave(it) }
                )
            }
        }

        // Manual Backup & Restore
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manual Backup & Restore (Local SAF)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Exports chats, memories, PYQs, videos, study plans, reports, document metadata & indexed pages. API keys are NEVER exported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeFilesInBackup, onCheckedChange = { includeFilesInBackup = it })
                    Text("Include imported private document files (<=10MB/file)", style = MaterialTheme.typography.bodySmall)
                }
                if (uiState.backupBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                uiState.backupMessage?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { exportLauncher.launch("sukoon_backup_${System.currentTimeMillis()}.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore")
                    }
                }
            }
        }

        // Modular Plugins & Custom Tools Registry
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("Modular Plugins & Offline Tools", style = MaterialTheme.typography.titleMedium)
                }
                uiState.plugins.forEach { plugin ->
                    Text("• ${plugin.name} (${plugin.category}): ${plugin.description}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = calcInput,
                        onValueChange = { calcInput = it },
                        label = { Text("Quick Math Tool") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.runPlugin("tool_formula_eval", calcInput) }) {
                        Text("Eval")
                    }
                }
                uiState.pluginOutput?.let { out ->
                    Text(out, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Offline & Battery Execution Limitations Notice
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Offline Mode & Background Execution Notes", style = MaterialTheme.typography.titleSmall)
                Text(
                    "• Offline Access: Library, PDFs, Notes, Hindi/English OCR, PYQ Vault, Saved Videos, Memories, Study Plans & Reports work 100% offline.\n" +
                        "• Online AI: Cloud providers (Gemini, OpenAI, OpenRouter) require internet unless a local on-device LLM plugin is added.\n" +
                        "• Battery & Doze: Android Doze mode and OEM battery savers can defer WorkManager study reminders slightly unless battery optimization is exempted for SUKOON AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    editingKeyProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { editingKeyProvider = null },
            title = { Text("Configure ${provider.displayName} API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Stored locally using Android Keystore AES-256-GCM encryption. Never hardcoded or exported in backups.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API Key (${provider.keyHint})") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveApiKey(provider, keyInput)
                    editingKeyProvider = null
                }) { Text("Save Encrypted") }
            },
            dismissButton = {
                TextButton(onClick = { editingKeyProvider = null }) { Text("Cancel") }
            }
        )
    }

    uiState.pendingRestoreSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingRestore() },
            title = { Text("Confirm Backup Restore") },
            text = {
                Text(
                    "Validated SUKOON AI Backup (v${summary.schemaVersion}):\n" +
                        "• ${summary.chatsCount} chats (${summary.messagesCount} messages)\n" +
                        "• ${summary.documentsCount} docs (${summary.chunksCount} indexed pages, ${summary.embeddedFilesCount} files)\n" +
                        "• ${summary.memoriesCount} memories, ${summary.pyqsCount} PYQs, ${summary.videosCount} videos\n" +
                        "• ${summary.tasksCount} tasks, ${summary.goalsCount} goals, ${summary.activityLogsCount} activity logs\n\n" +
                        "Choose whether to merge into existing records or overwrite existing data:"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmRestore(overwriteExisting = false) }) {
                    Text("Merge Safely")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { viewModel.confirmRestore(overwriteExisting = true) }) {
                        Text("Overwrite All", color = StatusError)
                    }
                    TextButton(onClick = { viewModel.cancelPendingRestore() }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
