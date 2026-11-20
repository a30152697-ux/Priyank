package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.network.ConnectivityMonitor
import com.example.core.preferences.UserPreferencesRepository
import com.example.data.ai.AiRouter
import com.example.data.db.DocumentAnnotationEntity
import com.example.data.db.DocumentEntity
import com.example.data.db.DocumentPageChunkEntity
import com.example.data.db.LibraryDao
import com.example.data.db.PlannerDao
import com.example.data.db.StudyActivityLogEntity
import com.example.data.pdf.PdfAndOcrProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class LibraryUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val selectedFolder: String = "All",
    val searchQuery: String = "",
    val activeDocument: DocumentEntity? = null,
    val activePages: List<DocumentPageChunkEntity> = emptyList(),
    val activeAnnotations: List<DocumentAnnotationEntity> = emptyList(),
    val currentPageIndex: Int = 1,
    val renderedPageBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val visionExplanation: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val libraryDao: LibraryDao,
    private val plannerDao: PlannerDao,
    private val pdfAndOcrProcessor: PdfAndOcrProcessor,
    private val aiRouter: AiRouter,
    private val prefsRepo: UserPreferencesRepository,
    private val connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    private val _selectedFolder = MutableStateFlow("All")
    private val _searchQuery = MutableStateFlow("")
    private val _matchingDocIds = MutableStateFlow<Set<Long>?>(null)
    private val _activeDocId = MutableStateFlow<Long?>(null)
    private val _currentPage = MutableStateFlow(1)
    private val _renderedBitmap = MutableStateFlow<Bitmap?>(null)
    private val _isProcessing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _visionExplanation = MutableStateFlow<String?>(null)

    private val activeDocFlow = _activeDocId.flatMapLatest { id ->
        if (id == null) flowOf(null) else libraryDao.observeDocument(id)
    }
    private val activePagesFlow = _activeDocId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else libraryDao.observeChunksForDocument(id)
    }
    private val activeAnnotationsFlow = _activeDocId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else libraryDao.observeAnnotations(id)
    }

    private val filteredDocsFlow = combine(
        libraryDao.observeDocuments(),
        _selectedFolder,
        _searchQuery,
        _matchingDocIds
    ) { allDocs, folder, query, matchIds ->
        val filtered = allDocs.filter { doc ->
            val folderOk = folder == "All" || doc.subjectFolder.equals(folder, ignoreCase = true)
            val queryOk = query.isBlank() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.subjectFolder.contains(query, ignoreCase = true) ||
                (matchIds?.contains(doc.id) == true)
            folderOk && queryOk
        }
        Triple(filtered, folder, query)
    }

    private val activeReaderFlow = combine(
        activeDocFlow,
        activePagesFlow,
        activeAnnotationsFlow,
        _currentPage,
        _renderedBitmap
    ) { doc, pages, anns, pageIdx, bmp ->
        listOf(doc, pages, anns, pageIdx, bmp)
    }

    private val statusFlow = combine(
        _isProcessing,
        _statusMessage,
        _errorMessage,
        _visionExplanation
    ) { busy, status, err, vision ->
        listOf(busy, status, err, vision)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        filteredDocsFlow,
        activeReaderFlow,
        statusFlow
    ) { docsTriple, readerList, statusList ->
        @Suppress("UNCHECKED_CAST")
        LibraryUiState(
            documents = docsTriple.first,
            selectedFolder = docsTriple.second,
            searchQuery = docsTriple.third,
            activeDocument = readerList[0] as DocumentEntity?,
            activePages = readerList[1] as List<DocumentPageChunkEntity>,
            activeAnnotations = readerList[2] as List<DocumentAnnotationEntity>,
            currentPageIndex = readerList[3] as Int,
            renderedPageBitmap = readerList[4] as Bitmap?,
            isProcessing = statusList[0] as Boolean,
            statusMessage = statusList[1] as String?,
            errorMessage = statusList[2] as String?,
            visionExplanation = statusList[3] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun selectFolder(folder: String) {
        _selectedFolder.value = folder
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                _matchingDocIds.value = null
            } else {
                _matchingDocIds.value = libraryDao.searchDocumentIdsByContent(query).toSet()
            }
        }
    }

    fun importDocumentUri(
        uri: Uri,
        subjectFolder: String,
        copyIntoPrivateStorage: Boolean,
        runOcrIfScanned: Boolean
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Extracting pages & running bilingual OCR where needed..."
            _errorMessage.value = null

            val result = pdfAndOcrProcessor.importDocument(
                uri = uri,
                copyIntoPrivateStorage = copyIntoPrivateStorage,
                runOcrIfScanned = runOcrIfScanned
            )
            result.fold(
                onSuccess = { imported ->
                    val docId = libraryDao.insertDocument(
                        DocumentEntity(
                            title = imported.title,
                            subjectFolder = subjectFolder.ifBlank { "General" },
                            fileType = imported.fileType,
                            localFilePath = imported.localFilePath,
                            sourceUri = uri.toString(),
                            pageCount = imported.pageCount,
                            lastReadPage = 1,
                            ocrStatus = imported.overallStatus,
                            ocrConfidence = imported.averageConfidence
                        )
                    )
                    val chunks = imported.pages.map { page ->
                        DocumentPageChunkEntity(
                            documentId = docId,
                            documentTitle = imported.title,
                            pageNumber = page.pageNumber,
                            content = page.text,
                            isOcrDerived = page.isOcrDerived,
                            confidence = page.confidence
                        )
                    }
                    libraryDao.insertChunks(chunks)
                    val confNote = if (imported.overallStatus == "OCR_LOW_CONFIDENCE") {
                        "Imported with low OCR confidence (${(imported.averageConfidence * 100).toInt()}%) — verify scanned formulas/handwriting."
                    } else {
                        "Imported '${imported.title}' (${imported.pageCount} pages) locally."
                    }
                    _statusMessage.value = confNote
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to import document."
                    _statusMessage.value = null
                }
            )
            _isProcessing.value = false
        }
    }

    fun createPersonalNote(title: String, subjectFolder: String, content: String) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty() || cleanContent.isEmpty()) return
        viewModelScope.launch {
            val docId = libraryDao.insertDocument(
                DocumentEntity(
                    title = cleanTitle,
                    subjectFolder = subjectFolder.ifBlank { "Personal Notes" },
                    fileType = "NOTE",
                    pageCount = 1,
                    ocrStatus = "EXTRACTED",
                    ocrConfidence = 1.0f
                )
            )
            libraryDao.insertChunks(
                listOf(
                    DocumentPageChunkEntity(
                        documentId = docId,
                        documentTitle = cleanTitle,
                        pageNumber = 1,
                        content = cleanContent
                    )
                )
            )
            _statusMessage.value = "Note '$cleanTitle' saved locally."
        }
    }

    fun openDocument(doc: DocumentEntity) {
        _activeDocId.value = doc.id
        val startPage = doc.lastReadPage.coerceIn(1, doc.pageCount.coerceAtLeast(1))
        _currentPage.value = startPage
        _visionExplanation.value = null
        loadRenderedPageIfPdf(doc, startPage)
    }

    fun openDocumentById(docId: Long, targetPage: Int) {
        viewModelScope.launch {
            val doc = libraryDao.getDocumentOnce(docId) ?: return@launch
            val page = targetPage.coerceIn(1, doc.pageCount.coerceAtLeast(1))
            _activeDocId.value = doc.id
            _currentPage.value = page
            _visionExplanation.value = null
            loadRenderedPageIfPdf(doc, page)
        }
    }

    fun closeDocument() {
        _activeDocId.value = null
        _renderedBitmap.value = null
        _visionExplanation.value = null
    }

    fun goToPage(pageNumber: Int) {
        val doc = uiState.value.activeDocument ?: return
        val clamped = pageNumber.coerceIn(1, doc.pageCount.coerceAtLeast(1))
        _currentPage.value = clamped
        _visionExplanation.value = null
        loadRenderedPageIfPdf(doc, clamped)

        viewModelScope.launch {
            libraryDao.updateDocument(
                doc.copy(lastReadPage = clamped, updatedAt = System.currentTimeMillis())
            )
            val epochDay = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
            plannerDao.insertActivityLog(
                StudyActivityLogEntity(
                    activityType = "DOC_READ",
                    subject = doc.subjectFolder,
                    detail = "Read ${doc.title} p.$clamped",
                    minutesSpent = 5,
                    epochDay = epochDay
                )
            )
        }
    }

    fun toggleDocumentBookmark(doc: DocumentEntity) {
        viewModelScope.launch {
            libraryDao.updateDocument(doc.copy(isBookmarked = !doc.isBookmarked))
        }
    }

    fun renameDocument(doc: DocumentEntity, newTitle: String, newFolder: String) {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            libraryDao.updateDocument(
                doc.copy(
                    title = cleanTitle,
                    subjectFolder = newFolder.trim().ifBlank { doc.subjectFolder },
                    updatedAt = System.currentTimeMillis()
                )
            )
            val chunks = libraryDao.getChunksForDocumentOnce(doc.id)
            if (chunks.isNotEmpty()) {
                libraryDao.insertChunks(chunks.map { it.copy(documentTitle = cleanTitle) })
            }
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            if (doc.localFilePath.isNotBlank()) {
                runCatching { File(doc.localFilePath).delete() }
            }
            libraryDao.deleteDocument(doc.id)
            if (_activeDocId.value == doc.id) {
                closeDocument()
            }
        }
    }

    fun addAnnotation(kind: String, excerpt: String, note: String) {
        val doc = uiState.value.activeDocument ?: return
        val page = _currentPage.value
        if (excerpt.isBlank() && note.isBlank()) return
        viewModelScope.launch {
            libraryDao.insertAnnotation(
                DocumentAnnotationEntity(
                    documentId = doc.id,
                    pageNumber = page,
                    kind = kind,
                    excerpt = excerpt.trim(),
                    note = note.trim()
                )
            )
            _statusMessage.value = "$kind saved on Page $page."
        }
    }

    fun deleteAnnotation(annotation: DocumentAnnotationEntity) {
        viewModelScope.launch {
            libraryDao.deleteAnnotation(annotation)
        }
    }

    fun rerunOcrOnCurrentPage() {
        val doc = uiState.value.activeDocument ?: return
        if (doc.localFilePath.isBlank() || doc.fileType != "PDF") {
            _errorMessage.value = "On-device page re-OCR requires the PDF to be imported into app-private storage."
            return
        }
        val page = _currentPage.value
        viewModelScope.launch {
            _isProcessing.value = true
            val res = pdfAndOcrProcessor.runOcrOnPdfPage(doc.localFilePath, page)
            res.fold(
                onSuccess = { extracted ->
                    val existing = uiState.value.activePages.firstOrNull { it.pageNumber == page }
                    val updatedChunk = (existing ?: DocumentPageChunkEntity(
                        documentId = doc.id,
                        documentTitle = doc.title,
                        pageNumber = page,
                        content = ""
                    )).copy(
                        content = extracted.text,
                        isOcrDerived = true,
                        confidence = extracted.confidence
                    )
                    libraryDao.insertChunks(listOf(updatedChunk))
                    _statusMessage.value =
                        "Bilingual OCR updated Page $page (confidence ${(extracted.confidence * 100).toInt()}%)."
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Page OCR failed."
                }
            )
            _isProcessing.value = false
        }
    }

    fun analyzeCurrentPdfPageWithVisionAi() {
        val bmp = _renderedBitmap.value
        val doc = uiState.value.activeDocument ?: return
        val page = _currentPage.value
        if (bmp == null) {
            _errorMessage.value = "No rendered PDF image available for this page."
            return
        }
        connectivityMonitor.refresh()
        if (!connectivityMonitor.isOnline.value) {
            _errorMessage.value = "Offline Mode: Sending a page image to Vision AI requires internet."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            val b64 = pdfAndOcrProcessor.encodeBitmapToJpegBase64(bmp)
            val prefs = prefsRepo.preferencesFlow.first()
            val prompt = "Analyze this textbook/note page (${doc.title}, Page $page). Transcribe any math formulas, diagrams, tables, or handwritten notes clearly and explain key takeaways for Owner."
            val result = aiRouter.generateSinglePrompt(
                prefs = prefs,
                systemHint = "Vision Study Analysis for diagrams, tables, and formulas.",
                prompt = prompt,
                imageBase64Jpeg = b64
            )
            result.fold(
                onSuccess = { text -> _visionExplanation.value = text },
                onFailure = { err -> _errorMessage.value = err.message ?: "Vision AI failed." }
            )
            _isProcessing.value = false
        }
    }

    private fun loadRenderedPageIfPdf(doc: DocumentEntity, page: Int) {
        if (doc.fileType != "PDF" || doc.localFilePath.isBlank()) {
            _renderedBitmap.value = null
            return
        }
        viewModelScope.launch {
            _renderedBitmap.value = pdfAndOcrProcessor.renderPdfPageBitmap(doc.localFilePath, page)
        }
    }

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(
                        libraryDao = container.database.libraryDao(),
                        plannerDao = container.database.plannerDao(),
                        pdfAndOcrProcessor = container.pdfAndOcrProcessor,
                        aiRouter = container.aiRouter,
                        prefsRepo = container.preferencesRepository,
                        connectivityMonitor = container.connectivityMonitor
                    ) as T
                }
            }
    }
}
