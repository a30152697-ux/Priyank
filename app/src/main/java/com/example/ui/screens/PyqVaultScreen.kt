package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.PyqEntity
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.StudyToolsViewModel

@Composable
fun PyqVaultScreen(viewModel: StudyToolsViewModel) {
    val pyqs by viewModel.pyqs.collectAsStateWithLifecycle()
    val ocrDraft by viewModel.ocrExtractedPyqDraft.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") } // All | Unsolved | Solved | Bookmarked
    var difficultyFilter by remember { mutableStateOf("All") } // All | Easy | Medium | Hard

    var showAddDialog by remember { mutableStateOf(false) }
    var qText by remember { mutableStateOf("") }
    var qYear by remember { mutableStateOf("2024") }
    var qSubject by remember { mutableStateOf("Physics") }
    var qChapter by remember { mutableStateOf("") }
    var qMarks by remember { mutableStateOf("5") }
    var qDifficulty by remember { mutableStateOf("Medium") }
    var qAnswer by remember { mutableStateOf("") }
    var qExplanation by remember { mutableStateOf("") }
    var qSource by remember { mutableStateOf("Manual") }

    var inPracticeMode by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.extractPyqFromPhoto(it) }
    }

    LaunchedEffect(ocrDraft) {
        if (!ocrDraft.isNullOrBlank()) {
            qText = ocrDraft.orEmpty()
            qSource = "Photo"
            showAddDialog = true
            viewModel.clearPyqDraft()
        }
    }

    val filtered = remember(pyqs, searchQuery, statusFilter, difficultyFilter) {
        pyqs.filter { item ->
            val qOk = searchQuery.isBlank() ||
                item.question.contains(searchQuery, ignoreCase = true) ||
                item.subject.contains(searchQuery, ignoreCase = true) ||
                item.chapter.contains(searchQuery, ignoreCase = true) ||
                item.examYear.contains(searchQuery, ignoreCase = true)
            val sOk = when (statusFilter) {
                "Solved" -> item.isSolved
                "Unsolved" -> !item.isSolved
                "Bookmarked" -> item.isBookmarked
                else -> true
            }
            val dOk = difficultyFilter == "All" || item.difficulty.equals(difficultyFilter, ignoreCase = true)
            qOk && sOk && dOk
        }
    }

    if (inPracticeMode && filtered.isNotEmpty()) {
        BackHandler { inPracticeMode = false }
        PyqPracticeTestView(
            items = filtered,
            onMarkSolved = { viewModel.togglePyqSolved(it) },
            onExit = { inPracticeMode = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search PYQs by keyword, year, subject, chapter...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pyq_search_field")
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Unsolved", "Solved", "Bookmarked").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st) }
                        )
                    }
                    listOf("Easy", "Medium", "Hard").forEach { diff ->
                        FilterChip(
                            selected = difficultyFilter == diff,
                            onClick = { difficultyFilter = if (difficultyFilter == diff) "All" else diff },
                            label = { Text(diff) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${filtered.size} questions • ${filtered.count { it.isSolved }} solved",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (filtered.isNotEmpty()) {
                        Button(onClick = { inPracticeMode = true }) {
                            Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Practice Test")
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Owner's PYQ Vault is Empty", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add previous-year exam questions manually, scan from photos via OCR, or save directly from SUKOON Chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        PyqItemCard(
                            item = item,
                            onToggleSolved = { viewModel.togglePyqSolved(item) },
                            onToggleBookmark = { viewModel.togglePyqBookmark(item) },
                            onDelete = { viewModel.deletePyq(item.id) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = "Scan PYQ from photo")
            }
            FloatingActionButton(
                onClick = {
                    qSource = "Manual"
                    showAddDialog = true
                },
                modifier = Modifier.testTag("add_pyq_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add PYQ manually")
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add PYQ to Vault") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = qText,
                        onValueChange = { qText = it },
                        label = { Text("Question") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = qYear,
                            onValueChange = { qYear = it },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = qMarks,
                            onValueChange = { qMarks = it },
                            label = { Text("Marks") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = qSubject,
                        onValueChange = { qSubject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = qChapter,
                        onValueChange = { qChapter = it },
                        label = { Text("Chapter") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Easy", "Medium", "Hard").forEach { d ->
                            FilterChip(
                                selected = qDifficulty == d,
                                onClick = { qDifficulty = d },
                                label = { Text(d) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = qAnswer,
                        onValueChange = { qAnswer = it },
                        label = { Text("Answer") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = qExplanation,
                        onValueChange = { qExplanation = it },
                        label = { Text("Explanation / Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (qText.isNotBlank()) {
                        viewModel.addOrUpdatePyq(
                            PyqEntity(
                                question = qText.trim(),
                                examYear = qYear.trim(),
                                subject = qSubject.trim().ifBlank { "General" },
                                chapter = qChapter.trim(),
                                marks = qMarks.toIntOrNull() ?: 5,
                                difficulty = qDifficulty,
                                answer = qAnswer.trim(),
                                explanation = qExplanation.trim(),
                                sourceType = qSource
                            )
                        )
                        qText = ""
                        qAnswer = ""
                        qExplanation = ""
                        showAddDialog = false
                    }
                }) { Text("Save PYQ") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PyqItemCard(
    item: PyqEntity,
    onToggleSolved: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDelete: () -> Unit
) {
    var showAnswer by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${item.subject} ${if (item.examYear.isNotBlank()) "• ${item.examYear}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${item.marks}M • ${item.difficulty}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleSolved) {
                    Icon(
                        imageVector = if (item.isSolved) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = "Mark solved",
                        tint = if (item.isSolved) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (item.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Bookmark PYQ",
                        tint = if (item.isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete PYQ")
                }
            }

            if (item.chapter.isNotBlank()) {
                Text("Chapter: ${item.chapter}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }

            Text(item.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

            if (item.answer.isNotBlank() || item.explanation.isNotBlank()) {
                TextButton(onClick = { showAnswer = !showAnswer }) {
                    Text(if (showAnswer) "Hide Answer & Explanation" else "Show Answer & Explanation")
                }
                if (showAnswer) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (item.answer.isNotBlank()) {
                                Text("Answer: ${item.answer}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (item.explanation.isNotBlank()) {
                                Text(item.explanation, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PyqPracticeTestView(
    items: List<PyqEntity>,
    onMarkSolved: (PyqEntity) -> Unit,
    onExit: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    var reveal by remember { mutableStateOf(false) }
    val current = items[index.coerceIn(0, items.lastIndex)]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit practice test")
            }
            Text(
                "Practice Test • Question ${index + 1} of ${items.size}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${current.subject} (${current.examYear}) • ${current.marks} Marks • ${current.difficulty}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(current.question, style = MaterialTheme.typography.titleMedium)

                if (reveal) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Answer: ${current.answer.ifBlank { "No answer recorded yet." }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            if (current.explanation.isNotBlank()) {
                                Text(current.explanation, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { reveal = !reveal }, modifier = Modifier.weight(1f)) {
                Text(if (reveal) "Hide Answer" else "Reveal Answer")
            }
            Button(
                onClick = { if (!current.isSolved) onMarkSolved(current) },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (current.isSolved) "Solved ✓" else "Mark Solved")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AssistChip(
                onClick = {
                    if (index > 0) {
                        index--
                        reveal = false
                    }
                },
                enabled = index > 0,
                label = { Text("Previous") }
            )
            Button(
                onClick = {
                    if (index < items.lastIndex) {
                        index++
                        reveal = false
                    } else {
                        onExit()
                    }
                }
            ) {
                Text(if (index < items.lastIndex) "Next Question" else "Finish Test")
            }
        }
    }
}
