package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.identity.SukoonIdentity
import com.example.core.network.ConnectivityMonitor
import com.example.core.preferences.UserPreferences
import com.example.core.preferences.UserPreferencesRepository
import com.example.core.security.InputValidator
import com.example.data.ai.AiRouter
import com.example.data.ai.AiStreamEvent
import com.example.data.db.ChatDao
import com.example.data.db.ChatMessageEntity
import com.example.data.db.ChatSessionEntity
import com.example.data.db.LibraryDao
import com.example.data.db.MemoryDao
import com.example.data.db.MemoryEntity
import com.example.data.db.PlannerDao
import com.example.data.db.PyqDao
import com.example.data.db.PyqEntity
import com.example.data.db.StudyActivityLogEntity
import com.example.data.pdf.PdfAndOcrProcessor
import com.example.data.pdf.RagCitation
import com.example.data.pdf.RagEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingTransferConsent(
    val userPrompt: String,
    val teachingHint: String,
    val citations: List<RagCitation>,
    val bookContextBlock: String,
    val imageBase64: String?,
    val imageOcrText: String?
)

data class PendingMemoryConsent(
    val category: String,
    val proposedContent: String,
    val reason: String
)

data class ChatUiState(
    val activeSession: ChatSessionEntity? = null,
    val sessions: List<ChatSessionEntity> = emptyList(),
    val messages: List<ChatMessageEntity> = emptyList(),
    val searchQuery: String = "",
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val streamingCitations: List<RagCitation> = emptyList(),
    val errorMessage: String? = null,
    val statusBanner: String? = null,
    val pendingImageBase64: String? = null,
    val pendingImageOcrText: String? = null,
    val pendingImageLowConfidence: Boolean = false,
    val pendingTransferConsent: PendingTransferConsent? = null,
    val pendingMemoryConsent: PendingMemoryConsent? = null,
    val activeTeachingPreset: String = "Default (Concise)"
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatDao: ChatDao,
    private val libraryDao: LibraryDao,
    private val memoryDao: MemoryDao,
    private val pyqDao: PyqDao,
    private val plannerDao: PlannerDao,
    private val prefsRepo: UserPreferencesRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val aiRouter: AiRouter,
    private val ragEngine: RagEngine,
    private val pdfAndOcrProcessor: PdfAndOcrProcessor
) : ViewModel() {

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _matchingSearchSessionIds = MutableStateFlow<Set<Long>?>(null)
    private val _isStreaming = MutableStateFlow(false)
    private val _streamingText = MutableStateFlow("")
    private val _streamingCitations = MutableStateFlow<List<RagCitation>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _pendingImageBase64 = MutableStateFlow<String?>(null)
    private val _pendingImageOcrText = MutableStateFlow<String?>(null)
    private val _pendingImageLowConfidence = MutableStateFlow(false)
    private val _pendingTransferConsent = MutableStateFlow<PendingTransferConsent?>(null)
    private val _pendingMemoryConsent = MutableStateFlow<PendingMemoryConsent?>(null)
    private val _activeTeachingPreset = MutableStateFlow("Default (Concise)")

    private var streamJob: Job? = null

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnline

    private val allSessionsFlow = chatDao.observeSessions()

    private val activeMessagesFlow = _activeSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else chatDao.observeMessages(id)
    }

    private val sessionSearchFlow = combine(
        allSessionsFlow,
        _activeSessionId,
        activeMessagesFlow,
        _searchQuery,
        _matchingSearchSessionIds
    ) { sessions, activeId, messages, query, matchIds ->
        val filteredSessions = if (query.isBlank()) {
            sessions
        } else {
            sessions.filter { s ->
                s.title.contains(query, ignoreCase = true) || (matchIds?.contains(s.id) == true)
            }
        }
        val activeSession = sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()
        Triple(activeSession, filteredSessions, messages to query)
    }

    private val streamStatusFlow = combine(
        _isStreaming,
        _streamingText,
        _streamingCitations,
        _errorMessage,
        _statusBanner
    ) { streaming, text, citations, err, banner ->
        listOf(streaming, text, citations, err, banner)
    }

    private val attachmentConsentFlow = combine(
        _pendingImageBase64,
        _pendingImageOcrText,
        _pendingImageLowConfidence,
        _pendingTransferConsent,
        _pendingMemoryConsent
    ) { b64, ocr, lowConf, transfer, memory ->
        listOf(b64, ocr, lowConf, transfer, memory)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        sessionSearchFlow,
        streamStatusFlow,
        attachmentConsentFlow,
        _activeTeachingPreset
    ) { sessTriple, streamList, consentList, preset ->
        @Suppress("UNCHECKED_CAST")
        ChatUiState(
            activeSession = sessTriple.first,
            sessions = sessTriple.second,
            messages = sessTriple.third.first,
            searchQuery = sessTriple.third.second,
            isStreaming = streamList[0] as Boolean,
            streamingText = streamList[1] as String,
            streamingCitations = streamList[2] as List<RagCitation>,
            errorMessage = streamList[3] as String?,
            statusBanner = streamList[4] as String?,
            pendingImageBase64 = consentList[0] as String?,
            pendingImageOcrText = consentList[1] as String?,
            pendingImageLowConfidence = consentList[2] as Boolean,
            pendingTransferConsent = consentList[3] as PendingTransferConsent?,
            pendingMemoryConsent = consentList[4] as PendingMemoryConsent?,
            activeTeachingPreset = preset
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    init {
        viewModelScope.launch {
            chatDao.observeSessions().collect { sessions ->
                if (_activeSessionId.value == null && sessions.isNotEmpty()) {
                    _activeSessionId.value = sessions.first().id
                }
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _activeSessionId.value = sessionId
        _errorMessage.value = null
    }

    fun startNewChat() {
        viewModelScope.launch {
            val prefs = preferences.value
            val id = chatDao.insertSession(
                ChatSessionEntity(
                    title = "Study Chat",
                    lastProviderName = prefs.activeProvider.displayName,
                    lastModelId = prefs.effectiveModelId()
                )
            )
            _activeSessionId.value = id
            _errorMessage.value = null
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = chatDao.getSessionById(sessionId) ?: return@launch
            chatDao.updateSession(existing.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatDao.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                _matchingSearchSessionIds.value = null
            } else {
                _matchingSearchSessionIds.value = chatDao.findSessionIdsMatchingMessage(query).toSet()
            }
        }
    }

    fun setTeachingPreset(preset: String) {
        _activeTeachingPreset.value = preset
    }

    fun attachImageFromPicker(uri: Uri) {
        viewModelScope.launch {
            _statusBanner.value = "Running on-device English + Hindi OCR on image..."
            val res = pdfAndOcrProcessor.processImageForOcrAndVision(uri, includeCompressedJpegForVision = true)
            res.fold(
                onSuccess = { ocr ->
                    _pendingImageBase64.value = ocr.compressedJpegBase64
                    _pendingImageOcrText.value = ocr.extractedText
                    _pendingImageLowConfidence.value = ocr.isLowConfidence
                    _statusBanner.value = ocr.confidenceNote
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Image processing failed."
                    _statusBanner.value = null
                }
            )
        }
    }

    fun clearAttachedImage() {
        _pendingImageBase64.value = null
        _pendingImageOcrText.value = null
        _pendingImageLowConfidence.value = false
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun dismissStatus() {
        _statusBanner.value = null
    }

    fun sendMessage(rawText: String, overrideTeachingHint: String = "") {
        val text = rawText.trim()
        val imgB64 = _pendingImageBase64.value
        val imgOcr = _pendingImageOcrText.value
        if (text.isEmpty() && imgB64 == null) return
        if (_isStreaming.value) return

        val prefs = preferences.value
        connectivityMonitor.refresh()
        if (!isOnline.value) {
            _errorMessage.value =
                "Offline Mode: Owner, online AI providers require internet. Your saved Library, Notes, PYQs, Videos, Memories, and Study Plans remain available offline."
            return
        }

        // Check for correction or memory instruction to ask Owner permission before saving
        detectCorrectionForMemoryPrompt(text)

        viewModelScope.launch {
            val ragResult = if (prefs.bookFirstRagEnabled && text.isNotBlank()) {
                val chunks = libraryDao.getAllChunksOnce()
                ragEngine.searchRelevantPassages(text, chunks, topK = 4)
            } else {
                com.example.data.pdf.RagSearchResult(emptyList(), "")
            }

            val needsBookConsent = prefs.askBeforeSendingBookContext && ragResult.hasRelevantBookEvidence
            val needsImageConsent = prefs.askBeforeSendingImagesOnline && imgB64 != null

            val effectivePrompt = text.ifBlank { "Please analyze the attached study image and explain clearly." }
            val effectiveHint = overrideTeachingHint.ifBlank { teachingHintForPreset(_activeTeachingPreset.value) }

            if (needsBookConsent || needsImageConsent) {
                _pendingTransferConsent.value = PendingTransferConsent(
                    userPrompt = effectivePrompt,
                    teachingHint = effectiveHint,
                    citations = ragResult.citations,
                    bookContextBlock = ragResult.formattedContextBlock,
                    imageBase64 = imgB64,
                    imageOcrText = imgOcr
                )
            } else {
                executeAiSend(
                    userPrompt = effectivePrompt,
                    teachingHint = effectiveHint,
                    citations = ragResult.citations,
                    bookContextBlock = ragResult.formattedContextBlock,
                    imageBase64 = imgB64,
                    imageOcrText = imgOcr
                )
            }
        }
    }

    fun resolveTransferConsent(allowBookContext: Boolean, allowImageTransfer: Boolean) {
        val pending = _pendingTransferConsent.value ?: return
        _pendingTransferConsent.value = null
        viewModelScope.launch {
            executeAiSend(
                userPrompt = pending.userPrompt,
                teachingHint = pending.teachingHint,
                citations = if (allowBookContext) pending.citations else emptyList(),
                bookContextBlock = if (allowBookContext) pending.bookContextBlock else "",
                imageBase64 = if (allowImageTransfer) pending.imageBase64 else null,
                imageOcrText = pending.imageOcrText
            )
        }
    }

    fun cancelTransferConsent() {
        _pendingTransferConsent.value = null
    }

    fun approvePendingMemory(editedContent: String, category: String) {
        val clean = editedContent.trim()
        _pendingMemoryConsent.value = null
        if (clean.isEmpty()) return
        if (InputValidator.containsSensitiveSecret(clean)) {
            _errorMessage.value = "Security rule: Passwords and API secrets are never saved in Memory."
            return
        }
        viewModelScope.launch {
            memoryDao.insertMemory(
                MemoryEntity(
                    category = category,
                    content = clean,
                    source = "APPROVED_CHAT"
                )
            )
            _statusBanner.value = "Saved to Owner Memory ($category)."
        }
    }

    fun dismissPendingMemory() {
        _pendingMemoryConsent.value = null
    }

    fun requestSaveMessageToMemory(content: String) {
        if (InputValidator.containsSensitiveSecret(content)) {
            _errorMessage.value = "Security rule: Passwords and API secrets cannot be saved to Memory."
            return
        }
        _pendingMemoryConsent.value = PendingMemoryConsent(
            category = "INSTRUCTION",
            proposedContent = content.take(400),
            reason = "Save this study note/preference to long-term Memory? (Does not retrain the AI model.)"
        )
    }

    fun saveQuestionToPyq(questionText: String, answerText: String) {
        viewModelScope.launch {
            pyqDao.insertPyq(
                PyqEntity(
                    question = questionText.trim(),
                    subject = "From Chat",
                    answer = answerText.trim(),
                    sourceType = "Chat"
                )
            )
            _statusBanner.value = "Saved to PYQ Vault!"
        }
    }

    fun regenerateLastResponse() {
        if (_isStreaming.value) return
        val session = uiState.value.activeSession ?: return
        val msgs = uiState.value.messages
        val lastUser = msgs.lastOrNull { it.role == "user" } ?: return
        val lastAssistant = msgs.lastOrNull { it.role == "assistant" }

        viewModelScope.launch {
            if (lastAssistant != null && lastAssistant.id > lastUser.id) {
                chatDao.deleteMessage(lastAssistant.id)
            }
            val updatedHistory = chatDao.getMessagesOnce(session.id)
            val priorHistory = updatedHistory.dropLast(1)
            val prefs = preferences.value
            val ragResult = if (prefs.bookFirstRagEnabled) {
                ragEngine.searchRelevantPassages(lastUser.content, libraryDao.getAllChunksOnce(), 4)
            } else {
                com.example.data.pdf.RagSearchResult(emptyList(), "")
            }
            streamIntoSession(
                session = session,
                prefs = prefs,
                historyBeforeUserTurn = priorHistory,
                userText = lastUser.content,
                citations = ragResult.citations,
                bookContextBlock = ragResult.formattedContextBlock,
                teachingHint = teachingHintForPreset(_activeTeachingPreset.value),
                imageBase64 = null
            )
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        _isStreaming.value = false
    }

    private suspend fun executeAiSend(
        userPrompt: String,
        teachingHint: String,
        citations: List<RagCitation>,
        bookContextBlock: String,
        imageBase64: String?,
        imageOcrText: String?
    ) {
        val prefs = preferences.value
        val activeId = _activeSessionId.value
        val session = if (activeId != null) {
            chatDao.getSessionById(activeId)
        } else {
            null
        } ?: run {
            val autoTitle = userPrompt.replace("\n", " ").take(36).ifBlank { "Study Chat" }
            val newId = chatDao.insertSession(
                ChatSessionEntity(
                    title = autoTitle,
                    lastProviderName = prefs.activeProvider.displayName,
                    lastModelId = prefs.effectiveModelId()
                )
            )
            _activeSessionId.value = newId
            chatDao.getSessionById(newId)!!
        }

        val combinedPrompt = buildString {
            append(userPrompt)
            if (!imageOcrText.isNullOrBlank()) {
                appendLine()
                appendLine()
                appendLine("[On-device OCR text extracted from attached image:]")
                append(imageOcrText.take(1500))
            }
        }

        val historyBefore = chatDao.getMessagesOnce(session.id)
        chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = session.id,
                role = "user",
                content = combinedPrompt,
                providerUsed = prefs.activeProvider.displayName,
                modelUsed = prefs.effectiveModelId()
            )
        )

        // Update title on first real user turn
        val updatedSession = if (historyBefore.isEmpty() && session.title == "Study Chat") {
            val newTitle = userPrompt.replace("\n", " ").take(38).ifBlank { "Study Chat" }
            session.copy(
                title = newTitle,
                updatedAt = System.currentTimeMillis(),
                lastProviderName = prefs.activeProvider.displayName,
                lastModelId = prefs.effectiveModelId()
            ).also { chatDao.updateSession(it) }
        } else {
            session.copy(
                updatedAt = System.currentTimeMillis(),
                lastProviderName = prefs.activeProvider.displayName,
                lastModelId = prefs.effectiveModelId()
            ).also { chatDao.updateSession(it) }
        }

        clearAttachedImage()

        // Summarize older messages if context window threshold exceeded
        val refreshedSession = maybeSummarizeSession(updatedSession, historyBefore, prefs)

        streamIntoSession(
            session = refreshedSession,
            prefs = prefs,
            historyBeforeUserTurn = historyBefore,
            userText = combinedPrompt,
            citations = citations,
            bookContextBlock = bookContextBlock,
            teachingHint = teachingHint,
            imageBase64 = imageBase64
        )
    }

    private suspend fun maybeSummarizeSession(
        session: ChatSessionEntity,
        history: List<ChatMessageEntity>,
        prefs: UserPreferences
    ): ChatSessionEntity {
        val unsummarizedCount = history.size - session.summarizedCount
        if (unsummarizedCount < AiRouter.SUMMARIZE_THRESHOLD) return session
        val splitIndex = (history.size - AiRouter.MAX_RECENT_TURNS).coerceAtLeast(session.summarizedCount)
        if (splitIndex <= session.summarizedCount) return session

        val slice = history.subList(session.summarizedCount, splitIndex)
        val newSummary = aiRouter.summarizeOlderMessages(prefs, session.rollingSummary, slice)
        val updated = session.copy(
            rollingSummary = newSummary,
            summarizedCount = splitIndex,
            updatedAt = System.currentTimeMillis()
        )
        chatDao.updateSession(updated)
        return updated
    }

    private fun streamIntoSession(
        session: ChatSessionEntity,
        prefs: UserPreferences,
        historyBeforeUserTurn: List<ChatMessageEntity>,
        userText: String,
        citations: List<RagCitation>,
        bookContextBlock: String,
        teachingHint: String,
        imageBase64: String?
    ) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _isStreaming.value = true
            _streamingText.value = ""
            _streamingCitations.value = citations
            _errorMessage.value = null

            val memories = memoryDao.getAllMemoriesOnce()
            val memoriesBlock = memories.take(15).joinToString("\n") {
                "- [${it.category}] ${it.content}"
            }

            try {
                aiRouter.streamConversation(
                    prefs = prefs,
                    historyMessages = historyBeforeUserTurn,
                    rollingSummary = session.rollingSummary,
                    summarizedCount = session.summarizedCount,
                    memoriesBlock = memoriesBlock,
                    bookContextBlock = bookContextBlock,
                    teachingModeHint = teachingHint,
                    userMessageText = userText,
                    attachedImageBase64 = imageBase64
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Delta -> {
                            _streamingText.value += event.textChunk
                        }
                        is AiStreamEvent.Completed -> {
                            chatDao.insertMessage(
                                ChatMessageEntity(
                                    sessionId = session.id,
                                    role = "assistant",
                                    content = event.fullText,
                                    providerUsed = prefs.activeProvider.displayName,
                                    modelUsed = prefs.effectiveModelId(),
                                    citationsJson = RagEngine.serializeCitations(citations),
                                    usedBookContext = citations.isNotEmpty()
                                )
                            )
                            val epochDay = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
                            plannerDao.insertActivityLog(
                                StudyActivityLogEntity(
                                    activityType = "CHAT_STUDY",
                                    subject = citations.firstOrNull()?.documentTitle?.take(24) ?: "AI Study",
                                    detail = userText.take(60),
                                    minutesSpent = 5,
                                    epochDay = epochDay
                                )
                            )
                            _isStreaming.value = false
                            _streamingText.value = ""
                            _streamingCitations.value = emptyList()
                        }
                        is AiStreamEvent.Error -> {
                            _errorMessage.value = event.message
                            _isStreaming.value = false
                            _streamingText.value = ""
                            _streamingCitations.value = emptyList()
                        }
                    }
                }
            } catch (e: CancellationException) {
                val partial = _streamingText.value.trim()
                if (partial.isNotEmpty()) {
                    chatDao.insertMessage(
                        ChatMessageEntity(
                            sessionId = session.id,
                            role = "assistant",
                            content = "$partial\n\n_[Stopped by Owner]_",
                            providerUsed = prefs.activeProvider.displayName,
                            modelUsed = prefs.effectiveModelId(),
                            citationsJson = RagEngine.serializeCitations(citations),
                            usedBookContext = citations.isNotEmpty()
                        )
                    )
                }
                _isStreaming.value = false
                _streamingText.value = ""
                throw e
            } finally {
                _isStreaming.value = false
            }
        }
    }

    private fun detectCorrectionForMemoryPrompt(userText: String) {
        if (!preferences.value.askBeforeSavingMemory) return
        val lower = userText.lowercase()
        val triggers = listOf(
            "remember that", "remember this", "yaad rakhna", "from now on",
            "actually the correct", "correction:", "my exam is", "my goal is"
        )
        if (triggers.any { lower.contains(it) } && !InputValidator.containsSensitiveSecret(userText)) {
            val cat = when {
                lower.contains("correction") || lower.contains("actually") -> "CORRECTION"
                lower.contains("exam") || lower.contains("goal") -> "GOAL"
                else -> "PREFERENCE"
            }
            _pendingMemoryConsent.value = PendingMemoryConsent(
                category = cat,
                proposedContent = userText.take(300),
                reason = "Owner, would you like SUKOON to save this to your local Long-Term Memory? (Note: Saving memory personalizes future prompts; it does not retrain the AI model.)"
            )
        }
    }

    private fun teachingHintForPreset(preset: String): String = when (preset) {
        "Step-by-Step Teacher" -> "Explain step-by-step like a calm personal teacher, checking conceptual clarity."
        "Detailed Deep Dive" -> "Provide a thorough, detailed explanation with examples, formulas, and common exam traps."
        "Quick Revision Notes" -> "Format as crisp bulleted revision notes with key formulas and bold keywords."
        "MCQ Practice" -> "Generate clear practice MCQs with options A-D and reveal answers + short explanations."
        else -> ""
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(
                        chatDao = container.database.chatDao(),
                        libraryDao = container.database.libraryDao(),
                        memoryDao = container.database.memoryDao(),
                        pyqDao = container.database.pyqDao(),
                        plannerDao = container.database.plannerDao(),
                        prefsRepo = container.preferencesRepository,
                        connectivityMonitor = container.connectivityMonitor,
                        aiRouter = container.aiRouter,
                        ragEngine = container.ragEngine,
                        pdfAndOcrProcessor = container.pdfAndOcrProcessor
                    ) as T
                }
            }
    }
}
