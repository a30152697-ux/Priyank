package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.DocumentEntity
import com.example.ui.components.MarkdownText
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showImportDialog by remember { mutableStateOf(false) }
    var importSubject by remember { mutableStateOf("Physics") }
    var copyPrivate by remember { mutableStateOf(true) }
    var runOcr by remember { mutableStateOf(true) }

    var showNoteDialog by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteSubject by remember { mutableStateOf("Personal Notes") }
    var noteBody by remember { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var renameSubject by remember { mutableStateOf("") }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importDocumentUri(
                uri = it,
                subjectFolder = importSubject,
                copyIntoPrivateStorage = copyPrivate,
                runOcrIfScanned = runOcr
            )
        }
    }

    // Reader Sub-Screen if a document is open
    val activeDoc = uiState.activeDocument
    if (activeDoc != null) {
        BackHandler { viewModel.closeDocument() }
        DocumentReaderView(
            viewModel = viewModel,
            doc = activeDoc,
            currentPage = uiState.currentPageIndex,
            pages = uiState.activePages,
            annotations = uiState.activeAnnotations,
            renderedBitmap = uiState.renderedPageBitmap,
            isProcessing = uiState.isProcessing,
            visionExplanation = uiState.visionExplanation
        )
        return
    }

    val folders = remember(uiState.documents) {
        listOf("All") + uiState.documents.map { it.subjectFolder }.distinct().sorted()
    }

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search & Quick Import Bar
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search books, notes, Hindi/English OCR text...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("library_search_field")
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folders.forEach { folder ->
                        FilterChip(
                            selected = uiState.selectedFolder == folder,
                            onClick = { viewModel.selectFolder(folder) },
                            label = { Text(folder) },
                            leadingIcon = {
                                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }

                if (uiState.isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                uiState.statusMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearMessages() }) { Text("OK") }
                        }
                    }
                }
                uiState.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearMessages() }) { Text("OK") }
                        }
                    }
                }
            }

            if (uiState.documents.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Owner's Private Library is Empty",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Import PDFs, scanned books (Hindi & English OCR), text files, or write personal study notes. Everything stays locally on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showImportDialog = true }) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import PDF / TXT")
                        }
                        OutlinedButton(onClick = { showNoteDialog = true }) {
                            Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Note")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.documents, key = { it.id }) { doc ->
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openDocument(doc) }
                                .testTag("doc_card_${doc.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (doc.fileType) {
                                        "PDF" -> Icons.Filled.PictureAsPdf
                                        "NOTE" -> Icons.Filled.NoteAdd
                                        else -> Icons.Outlined.Description
                                    },
                                    contentDescription = doc.fileType,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${doc.subjectFolder} • ${doc.pageCount} pages • Last read p.${doc.lastReadPage}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (doc.ocrStatus == "OCR_LOW_CONFIDENCE") {
                                        Text(
                                            text = "Low OCR confidence (${(doc.ocrConfidence * 100).toInt()}%) — verify scanned text",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusWarning
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.toggleDocumentBookmark(doc) }) {
                                    Icon(
                                        imageVector = if (doc.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = "Bookmark document",
                                        tint = if (doc.isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    renameTarget = doc
                                    renameTitle = doc.title
                                    renameSubject = doc.subjectFolder
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename document")
                                }
                                IconButton(onClick = { viewModel.deleteDocument(doc) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete document")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom-right FABs for Note & Document Import
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingActionButton(
                onClick = { showNoteDialog = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.testTag("add_note_fab")
            ) {
                Icon(Icons.Filled.NoteAdd, contentDescription = "Add personal study note")
            }
            FloatingActionButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.testTag("import_doc_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Import PDF or text document")
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Study Document (SAF)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = importSubject,
                        onValueChange = { importSubject = it },
                        label = { Text("Subject Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copyPrivate, onCheckedChange = { copyPrivate = it })
                        Text("Copy file into app-private offline storage", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = runOcr, onCheckedChange = { runOcr = it })
                        Text("Auto-run English + Hindi OCR on scanned PDF pages", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showImportDialog = false
                    safLauncher.launch(arrayOf("application/pdf", "text/plain", "text/*"))
                }) {
                    Text("Choose File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Create Local Study Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Note Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteSubject,
                        onValueChange = { noteSubject = it },
                        label = { Text("Subject Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteBody,
                        onValueChange = { noteBody = it },
                        label = { Text("Study Content") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createPersonalNote(noteTitle, noteSubject, noteBody)
                    noteTitle = ""
                    noteBody = ""
                    showNoteDialog = false
                }) { Text("Save Note") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameTitle,
                        onValueChange = { renameTitle = it },
                        label = { Text("Document Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = renameSubject,
                        onValueChange = { renameSubject = it },
                        label = { Text("Subject Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameDocument(target, renameTitle, renameSubject)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DocumentReaderView(
    viewModel: LibraryViewModel,
    doc: DocumentEntity,
    currentPage: Int,
    pages: List<com.example.data.db.DocumentPageChunkEntity>,
    annotations: List<com.example.data.db.DocumentAnnotationEntity>,
    renderedBitmap: android.graphics.Bitmap?,
    isProcessing: Boolean,
    visionExplanation: String?
) {
    var showAnnotationDialog by remember { mutableStateOf(false) }
    var annotationKind by remember { mutableStateOf("HIGHLIGHT") }
    var excerpt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var askVisionConsent by remember { mutableStateOf(false) }

    val pageChunk = pages.firstOrNull { it.pageNumber == currentPage }
    val pageAnnotations = annotations.filter { it.pageNumber == currentPage }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Reader Header
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.closeDocument() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Page $currentPage of ${doc.pageCount} • ${doc.subjectFolder}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.goToPage(currentPage - 1) }, enabled = currentPage > 1) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
                }
                IconButton(onClick = { viewModel.goToPage(currentPage + 1) }, enabled = currentPage < doc.pageCount) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
                }
            }
        }

        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Action Chips for Page OCR, Highlight/Annotation, Vision AI
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        annotationKind = "HIGHLIGHT"
                        excerpt = pageChunk?.content?.take(120).orEmpty()
                        showAnnotationDialog = true
                    },
                    label = { Text("Highlight / Note") },
                    leadingIcon = { Icon(Icons.Filled.Highlight, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                if (doc.fileType == "PDF" && doc.localFilePath.isNotBlank()) {
                    AssistChip(
                        onClick = { viewModel.rerunOcrOnCurrentPage() },
                        label = { Text("Re-run Hindi/Eng OCR") },
                        leadingIcon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { askVisionConsent = true },
                        label = { Text("Analyze Page Diagram/Formula (AI)") },
                        leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            if (renderedBitmap != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = renderedBitmap.asImageBitmap(),
                        contentDescription = "Rendered PDF page $currentPage",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!visionExplanation.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SUKOON Vision Analysis (Page $currentPage)", style = MaterialTheme.typography.titleSmall)
                        MarkdownText(markdown = visionExplanation)
                    }
                }
            }

            // Extracted / OCR Text
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Indexed Page Text", style = MaterialTheme.typography.titleSmall)
                        if (pageChunk?.isOcrDerived == true) {
                            Text(
                                text = "OCR (${(pageChunk.confidence * 100).toInt()}% conf.)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pageChunk.confidence < 0.65f) StatusWarning else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Text(
                        text = pageChunk?.content?.ifBlank { "No text extracted on this page. Try running OCR or Vision AI." }
                            ?: "Page not indexed.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (pageAnnotations.isNotEmpty()) {
                Text("Highlights & Annotations (Page $currentPage)", style = MaterialTheme.typography.titleSmall)
                pageAnnotations.forEach { ann ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ann.kind, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                if (ann.excerpt.isNotBlank()) {
                                    Text("\"${ann.excerpt}\"", style = MaterialTheme.typography.bodySmall)
                                }
                                if (ann.note.isNotBlank()) {
                                    Text(ann.note, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteAnnotation(ann) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete annotation")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAnnotationDialog) {
        AlertDialog(
            onDismissRequest = { showAnnotationDialog = false },
            title = { Text("Add Highlight / Annotation (p.$currentPage)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("HIGHLIGHT", "NOTE", "BOOKMARK").forEach { kind ->
                            FilterChip(
                                selected = annotationKind == kind,
                                onClick = { annotationKind = kind },
                                label = { Text(kind) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = excerpt,
                        onValueChange = { excerpt = it },
                        label = { Text("Highlighted Excerpt") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Owner's Note") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addAnnotation(annotationKind, excerpt, note)
                    note = ""
                    showAnnotationDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAnnotationDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (askVisionConsent) {
        AlertDialog(
            onDismissRequest = { askVisionConsent = false },
            title = { Text("Send Page Image to Vision AI?") },
            text = {
                Text("Owner, analyzing diagrams, graphs, tables, or handwritten formulas sends a rendered image of Page $currentPage online to your selected AI provider. Proceed?")
            },
            confirmButton = {
                Button(onClick = {
                    askVisionConsent = false
                    viewModel.analyzeCurrentPdfPageWithVisionAi()
                }) { Text("Allow & Analyze") }
            },
            dismissButton = {
                TextButton(onClick = { askVisionConsent = false }) { Text("Cancel") }
            }
        )
    }
}
