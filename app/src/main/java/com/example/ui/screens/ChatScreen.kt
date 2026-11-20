package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.identity.SukoonIdentity
import com.example.data.db.ChatMessageEntity
import com.example.data.db.ChatSessionEntity
import com.example.data.pdf.RagCitation
import com.example.data.pdf.RagEngine
import com.example.ui.components.MarkdownText
import com.example.ui.components.TransferConsentDialog
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenCitationDoc: (Long, Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameValue by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.attachImageFromPicker(it) }
    }

    LaunchedEffect(uiState.messages.size, uiState.streamingText.length) {
        val total = uiState.messages.size + if (uiState.isStreaming) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Active Provider, Session & Book-First RAG strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { showHistorySheet = true },
                    label = {
                        Text(
                            text = uiState.activeSession?.title ?: "Chats",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.History, contentDescription = "Chat history", modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("chat_history_chip")
                )

                AssistChip(
                    onClick = onOpenSettings,
                    label = {
                        Text(
                            text = "${prefs.activeProvider.displayName} • ${prefs.effectiveModelId().take(18)}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Active AI provider", modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("active_provider_chip")
                )

                IconButton(
                    onClick = { viewModel.startNewChat() },
                    modifier = Modifier.testTag("new_chat_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Chat")
                }
            }
        }

        // Teaching mode & RAG status row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(
                "Default (Concise)",
                "Step-by-Step Teacher",
                "Detailed Deep Dive",
                "Quick Revision Notes",
                "MCQ Practice"
            )
            presets.forEach { preset ->
                FilterChip(
                    selected = uiState.activeTeachingPreset == preset,
                    onClick = { viewModel.setTeachingPreset(preset) },
                    label = { Text(preset, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // Rolling context-window summary notice when active
        val summary = uiState.activeSession?.rollingSummary.orEmpty()
        if (summary.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Context Window Managed: ${uiState.activeSession?.summarizedCount ?: 0} earlier turns summarized locally.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Status / Error banners
        AnimatedVisibility(visible = uiState.errorMessage != null) {
            uiState.errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (err.contains("Settings", ignoreCase = true)) {
                            TextButton(onClick = onOpenSettings) {
                                Text("Settings")
                            }
                        }
                        IconButton(onClick = { viewModel.dismissError() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss error")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = uiState.statusBanner != null) {
            uiState.statusBanner?.let { info ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissStatus() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Main Chat Messages or Welcome Sanctuary
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.messages.isEmpty() && !uiState.isStreaming) {
                WelcomeStudyCard(
                    isOnline = isOnline,
                    hasBooksHint = prefs.bookFirstRagEnabled,
                    onQuickPrompt = { prompt ->
                        inputText = prompt
                    },
                    onOpenSettings = onOpenSettings
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        val isLastAssistant =
                            msg.role == "assistant" && msg.id == uiState.messages.lastOrNull { it.role == "assistant" }?.id
                        ChatBubble(
                            message = msg,
                            isLastAssistant = isLastAssistant,
                            onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                            onRegenerate = { viewModel.regenerateLastResponse() },
                            onSaveMemory = { viewModel.requestSaveMessageToMemory(msg.content) },
                            onSavePyq = {
                                val prevUser = uiState.messages.lastOrNull { it.role == "user" && it.id < msg.id }
                                viewModel.saveQuestionToPyq(
                                    questionText = prevUser?.content ?: "Saved study question",
                                    answerText = msg.content
                                )
                            },
                            onOpenCitation = { cit -> onOpenCitationDoc(cit.documentId, cit.pageNumber) }
                        )
                    }

                    if (uiState.isStreaming) {
                        item(key = "streaming_bubble") {
                            StreamingAssistantCard(
                                text = uiState.streamingText,
                                citations = uiState.streamingCitations,
                                providerLabel = "${prefs.activeProvider.displayName} • ${prefs.effectiveModelId()}",
                                onOpenCitation = { cit -> onOpenCitationDoc(cit.documentId, cit.pageNumber) }
                            )
                        }
                    }
                }
            }
        }

        // Attached Image OCR Preview Strip
        if (uiState.pendingImageBase64 != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.pendingImageLowConfidence) {
                                "Image Attached (Low OCR confidence — Vision AI recommended)"
                            } else {
                                "Image Attached + On-device OCR Ready"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                        val preview = uiState.pendingImageOcrText.orEmpty().replace("\n", " ").take(90)
                        if (preview.isNotBlank()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.clearAttachedImage() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove attached image")
                    }
                }
            }
        }

        // Composer Bar
        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = "Attach study photo or handwritten note"
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask SUKOON, Owner...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                if (uiState.isStreaming) {
                    FilledIconButton(
                        onClick = { viewModel.stopStreaming() },
                        modifier = Modifier.size(48.dp).testTag("stop_streaming_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop generating")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            val toSend = inputText
                            inputText = ""
                            viewModel.sendMessage(toSend)
                        },
                        enabled = inputText.isNotBlank() || uiState.pendingImageBase64 != null,
                        modifier = Modifier.size(48.dp).testTag("send_message_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                    }
                }
            }
        }
    }

    // Permission-Based Transfer Consent Dialog
    uiState.pendingTransferConsent?.let { pending ->
        TransferConsentDialog(
            pending = pending,
            providerLabel = "${prefs.activeProvider.displayName} (${prefs.effectiveModelId()})",
            onApprove = { allowBooks, allowImg ->
                viewModel.resolveTransferConsent(allowBooks, allowImg)
            },
            onCancel = { viewModel.cancelTransferConsent() }
        )
    }

    // Permission-Based Memory Save Dialog
    uiState.pendingMemoryConsent?.let { pendingMem ->
        var editableContent by remember(pendingMem) { mutableStateOf(pendingMem.proposedContent) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingMemory() },
            icon = { Icon(Icons.Filled.Psychology, contentDescription = "Memory permission") },
            title = { Text("Save to Owner Memory?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pendingMem.reason, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = editableContent,
                        onValueChange = { editableContent = it },
                        label = { Text("Memory (${pendingMem.category})") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.approvePendingMemory(editableContent, pendingMem.category) }) {
                    Text("Save Memory")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingMemory() }) {
                    Text("Skip")
                }
            }
        )
    }

    // Chat History, Search, Rename & Delete Sheet
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Owner's Study Chats", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = {
                        viewModel.startNewChat()
                        showHistorySheet = false
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Chat")
                    }
                }

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search chat titles or messages...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.sessions, key = { it.id }) { sess ->
                        val isSelected = sess.id == uiState.activeSession?.id
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectSession(sess.id)
                                    showHistorySheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sess.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (sess.lastProviderName.isNotBlank()) {
                                        Text(
                                            text = "${sess.lastProviderName} • ${sess.lastModelId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    renameTarget = sess
                                    renameValue = sess.title
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename chat")
                                }
                                IconButton(onClick = { viewModel.deleteSession(sess.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete chat")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Chat Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameSession(target.id, renameValue)
                    renameTarget = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun WelcomeStudyCard(
    isOnline: Boolean,
    hasBooksHint: Boolean,
    onQuickPrompt: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_study_sanctuary),
            contentDescription = "SUKOON Study Sanctuary",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .height(165.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Namaste Owner, I am ${SukoonIdentity.AI_NAME}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isOnline) {
                "Your personal AI study teacher, book-first RAG researcher, and productivity companion. ${if (hasBooksHint) "Book-First RAG is active." else ""}"
            } else {
                "You are currently Offline. Your local Library, OCR notes, PYQ Vault, Videos, Memories & Planner remain 100% accessible."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val starters = listOf(
                "Explain Newton's 2nd Law concisely",
                "Check my uploaded books for key formulas",
                "Quiz me with 3 MCQs on current topic"
            )
            starters.forEach { s ->
                OutlinedButton(onClick = { onQuickPrompt(s) }) {
                    Text(s, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) {
            Text("Configure OpenRouter / OpenAI / Gemini API Keys")
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    isLastAssistant: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onSaveMemory: () -> Unit,
    onSavePyq: () -> Unit,
    onOpenCitation: (RagCitation) -> Unit
) {
    val isUser = message.role == "user"
    val citations = remember(message.citationsJson) {
        RagEngine.deserializeCitations(message.citationsJson)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (isUser) 0.dp else 2.dp,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isUser) "Owner (${SukoonIdentity.OWNER_NAME})" else SukoonIdentity.AI_NAME,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                    if (!isUser && message.providerUsed.isNotBlank()) {
                        Text(
                            text = "• ${message.providerUsed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    MarkdownText(markdown = message.content)
                }

                // Book Citations Block
                if (citations.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sources from Owner's Library:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        citations.forEach { cit ->
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { onOpenCitation(cit) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "${cit.documentTitle} • p.${cit.pageNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Row on Assistant Messages
                if (!isUser) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy response", modifier = Modifier.size(16.dp))
                        }
                        if (isLastAssistant) {
                            IconButton(onClick = onRegenerate, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Regenerate answer", modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(onClick = onSaveMemory, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Psychology, contentDescription = "Save to Memory", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onSavePyq, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save to PYQ Vault", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantCard(
    text: String,
    citations: List<RagCitation>,
    providerLabel: String,
    onOpenCitation: (RagCitation) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Text(
                    text = "${SukoonIdentity.AI_NAME} streaming • $providerLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            if (text.isNotBlank()) {
                MarkdownText(markdown = text)
            } else {
                Text(
                    text = "Thinking for Owner...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (citations.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    citations.forEach { cit ->
                        AssistChip(
                            onClick = { onOpenCitation(cit) },
                            label = { Text("${cit.documentTitle} p.${cit.pageNumber}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}
