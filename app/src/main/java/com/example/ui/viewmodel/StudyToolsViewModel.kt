package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.network.ConnectivityMonitor
import com.example.core.preferences.UserPreferencesRepository
import com.example.core.security.InputValidator
import com.example.data.ai.AiRouter
import com.example.data.db.LibraryDao
import com.example.data.db.MemoryDao
import com.example.data.db.MemoryEntity
import com.example.data.db.PlannerDao
import com.example.data.db.PyqDao
import com.example.data.db.PyqEntity
import com.example.data.db.SavedVideoEntity
import com.example.data.db.StudyActivityLogEntity
import com.example.data.db.StudyGoalEntity
import com.example.data.db.StudyTaskEntity
import com.example.data.db.VideoDao
import com.example.data.pdf.PdfAndOcrProcessor
import com.example.data.pdf.RagEngine
import com.example.data.work.StudyReminderScheduler
import com.example.data.youtube.YouTubeMetadataFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudyActivityReport(
    val periodLabel: String,
    val totalMinutes: Int,
    val completedTasks: Int,
    val solvedPyqs: Int,
    val docsRead: Int,
    val quizzesDone: Int,
    val subjectBreakdown: List<Pair<String, Int>>
)

data class TeacherStudioState(
    val isLoading: Boolean = false,
    val outputText: String = "",
    val citationsText: String = "",
    val error: String? = null
)

class StudyToolsViewModel(
    private val pyqDao: PyqDao,
    private val videoDao: VideoDao,
    private val plannerDao: PlannerDao,
    private val memoryDao: MemoryDao,
    private val libraryDao: LibraryDao,
    private val aiRouter: AiRouter,
    private val ragEngine: RagEngine,
    private val prefsRepo: UserPreferencesRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val pdfAndOcrProcessor: PdfAndOcrProcessor,
    private val youTubeFetcher: YouTubeMetadataFetcher,
    private val reminderScheduler: StudyReminderScheduler
) : ViewModel() {

    val pyqs: StateFlow<List<PyqEntity>> = pyqDao.observePyqs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videos: StateFlow<List<SavedVideoEntity>> = videoDao.observeVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<StudyTaskEntity>> = plannerDao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<StudyGoalEntity>> = plannerDao.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityLogs: StateFlow<List<StudyActivityLogEntity>> = plannerDao.observeActivityLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = memoryDao.observeMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _teacherState = MutableStateFlow(TeacherStudioState())
    val teacherState: StateFlow<TeacherStudioState> = _teacherState.asStateFlow()

    private val _toolStatus = MutableStateFlow<String?>(null)
    val toolStatus: StateFlow<String?> = _toolStatus.asStateFlow()

    private val _ocrExtractedPyqDraft = MutableStateFlow<String?>(null)
    val ocrExtractedPyqDraft: StateFlow<String?> = _ocrExtractedPyqDraft.asStateFlow()

    // Computed Real Reports (Zero fabricated data)
    val reports: StateFlow<List<StudyActivityReport>> = combine(
        activityLogs,
        tasks,
        pyqs
    ) { logs, _, _ ->
        val today = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
        listOf(
            buildReport("Daily (Today)", logs.filter { it.epochDay == today }),
            buildReport("Weekly (Last 7 Days)", logs.filter { it.epochDay >= today - 6 }),
            buildReport("Monthly (Last 30 Days)", logs.filter { it.epochDay >= today - 29 })
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Weak topics derived strictly from real unsolved/hard PYQs
    val weakTopics: StateFlow<List<Pair<String, Int>>> = pyqs.combine(activityLogs) { pyqList, _ ->
        pyqList
            .filter { !it.isSolved || it.difficulty == "Hard" }
            .groupBy { it.subject.ifBlank { "General" } + if (it.chapter.isNotBlank()) " • ${it.chapter}" else "" }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- PYQ Vault ---
    fun addOrUpdatePyq(item: PyqEntity) {
        viewModelScope.launch {
            pyqDao.insertPyq(item)
            _toolStatus.value = "PYQ saved locally."
        }
    }

    fun togglePyqSolved(item: PyqEntity) {
        viewModelScope.launch {
            val nowSolved = !item.isSolved
            pyqDao.updatePyq(item.copy(isSolved = nowSolved))
            if (nowSolved) {
                val today = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
                plannerDao.insertActivityLog(
                    StudyActivityLogEntity(
                        activityType = "PYQ_SOLVED",
                        subject = item.subject.ifBlank { "General" },
                        detail = item.question.take(60),
                        minutesSpent = (item.marks * 3).coerceIn(5, 45),
                        epochDay = today
                    )
                )
            }
        }
    }

    fun togglePyqBookmark(item: PyqEntity) {
        viewModelScope.launch {
            pyqDao.updatePyq(item.copy(isBookmarked = !item.isBookmarked))
        }
    }

    fun deletePyq(id: Long) {
        viewModelScope.launch { pyqDao.deletePyq(id) }
    }

    fun extractPyqFromPhoto(uri: Uri) {
        viewModelScope.launch {
            _toolStatus.value = "Extracting question text via English + Hindi OCR..."
            val res = pdfAndOcrProcessor.processImageForOcrAndVision(uri, includeCompressedJpegForVision = false)
            res.fold(
                onSuccess = { ocr ->
                    _ocrExtractedPyqDraft.value = ocr.extractedText
                    _toolStatus.value = ocr.confidenceNote
                },
                onFailure = { err ->
                    _toolStatus.value = err.message ?: "OCR failed."
                }
            )
        }
    }

    fun clearPyqDraft() {
        _ocrExtractedPyqDraft.value = null
    }

    // --- YouTube Library ---
    fun fetchYouTubeInfoAndSave(
        url: String,
        manualTitle: String,
        manualChannel: String,
        subject: String,
        chapter: String,
        notes: String,
        timestamps: String,
        tags: String
    ) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val meta = if (connectivityMonitor.isOnline.value) {
                youTubeFetcher.fetchMetadataIfPossible(url)
            } else {
                com.example.data.youtube.YouTubeMetadata(
                    videoId = youTubeFetcher.extractVideoId(url),
                    title = "",
                    channelName = "",
                    fetchedFromNetwork = false
                )
            }
            val finalTitle = manualTitle.trim().ifBlank { meta.title }.ifBlank { "Saved Study Video" }
            val finalChannel = manualChannel.trim().ifBlank { meta.channelName }

            videoDao.insertVideo(
                SavedVideoEntity(
                    url = url.trim(),
                    videoId = meta.videoId,
                    title = finalTitle,
                    channelName = finalChannel,
                    subject = subject.trim().ifBlank { "General" },
                    chapter = chapter.trim(),
                    notes = notes.trim(),
                    timestampsText = timestamps.trim(),
                    tags = tags.trim(),
                    transcriptAvailable = false
                )
            )
            _toolStatus.value = if (meta.fetchedFromNetwork) {
                "Video & channel metadata saved locally."
            } else {
                "Video URL & notes saved locally."
            }
        }
    }

    fun deleteVideo(id: Long) {
        viewModelScope.launch { videoDao.deleteVideo(id) }
    }

    // --- Study Planner & Reminders ---
    fun addStudyTask(
        title: String,
        subject: String,
        taskType: String,
        dayOfWeek: Int,
        timeLabel: String,
        durationMinutes: Int,
        reminderDelayMinutes: Long?
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val today = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
            val id = plannerDao.insertTask(
                StudyTaskEntity(
                    title = title.trim(),
                    subject = subject.trim().ifBlank { "General" },
                    taskType = taskType,
                    dayOfWeek = dayOfWeek,
                    scheduledTimeLabel = timeLabel.trim(),
                    scheduledEpochDay = today,
                    durationMinutes = durationMinutes.coerceIn(5, 360),
                    reminderEnabled = reminderDelayMinutes != null
                )
            )
            if (reminderDelayMinutes != null) {
                reminderScheduler.scheduleReminder(
                    taskId = id,
                    title = title.trim(),
                    subject = subject.trim().ifBlank { "General" },
                    delayMinutes = reminderDelayMinutes
                )
                _toolStatus.value = "Task added with WorkManager reminder in $reminderDelayMinutes min."
            } else {
                _toolStatus.value = "Study task saved."
            }
        }
    }

    fun toggleTaskCompletion(task: StudyTaskEntity) {
        viewModelScope.launch {
            val nowDone = !task.isCompleted
            plannerDao.updateTask(
                task.copy(
                    isCompleted = nowDone,
                    completedAt = if (nowDone) System.currentTimeMillis() else null
                )
            )
            if (nowDone) {
                val today = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
                plannerDao.insertActivityLog(
                    StudyActivityLogEntity(
                        activityType = "TASK_COMPLETED",
                        subject = task.subject.ifBlank { "General" },
                        detail = task.title,
                        minutesSpent = task.durationMinutes,
                        epochDay = today
                    )
                )
                reminderScheduler.cancelReminder(task.id)
            }
        }
    }

    fun deleteTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            reminderScheduler.cancelReminder(task.id)
            plannerDao.deleteTask(task.id)
        }
    }

    fun addGoal(title: String, goalType: String, subject: String, daysFromNow: Int, notes: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            plannerDao.insertGoal(
                StudyGoalEntity(
                    title = title.trim(),
                    goalType = goalType,
                    subject = subject.trim().ifBlank { "General" },
                    targetDateMillis = System.currentTimeMillis() + daysFromNow.coerceAtLeast(1) * 24L * 60 * 60 * 1000,
                    notes = notes.trim()
                )
            )
            _toolStatus.value = "Goal saved."
        }
    }

    fun updateGoalProgress(goal: StudyGoalEntity, progress: Int) {
        viewModelScope.launch {
            val clamped = progress.coerceIn(0, 100)
            plannerDao.updateGoal(goal.copy(progressPercent = clamped, isCompleted = clamped >= 100))
        }
    }

    fun deleteGoal(id: Long) {
        viewModelScope.launch { plannerDao.deleteGoal(id) }
    }

    // --- Memory Manager ---
    fun saveMemory(category: String, content: String, existingId: Long = 0L) {
        val clean = content.trim()
        if (clean.isEmpty()) return
        if (InputValidator.containsSensitiveSecret(clean)) {
            _toolStatus.value = "Blocked: Never store passwords or API keys in Memory."
            return
        }
        viewModelScope.launch {
            memoryDao.insertMemory(
                MemoryEntity(
                    id = existingId,
                    category = category,
                    content = clean,
                    source = "MANUAL",
                    updatedAt = System.currentTimeMillis()
                )
            )
            _toolStatus.value = "Memory saved locally."
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { memoryDao.deleteMemory(id) }
    }

    // --- Personal Teacher Studio ---
    fun runTeacherTask(mode: String, topicOrQuestion: String, useBooksFirst: Boolean) {
        val input = topicOrQuestion.trim()
        if (input.isEmpty()) return
        connectivityMonitor.refresh()
        if (!connectivityMonitor.isOnline.value) {
            _teacherState.value = TeacherStudioState(
                error = "Offline Mode: Teacher AI generation requires internet."
            )
            return
        }

        viewModelScope.launch {
            _teacherState.value = TeacherStudioState(isLoading = true)
            val prefs = prefsRepo.preferencesFlow.first()
            val rag = if (useBooksFirst) {
                ragEngine.searchRelevantPassages(input, libraryDao.getAllChunksOnce(), 4)
            } else {
                com.example.data.pdf.RagSearchResult(emptyList(), "")
            }

            val memoriesText = memoryDao.getAllMemoriesOnce().take(12).joinToString("\n") {
                "- [${it.category}] ${it.content}"
            }

            val instruction = when (mode) {
                "Explain Chapter" -> "Teach this chapter clearly with structure, core intuition, key formulas, and 2 quick check questions."
                "Step-by-Step Solve" -> "Solve step-by-step showing every formula, substitution, unit, and final verification."
                "MCQ Test" -> "Create 5 exam-style MCQs (Easy to Hard) with options A/B/C/D, followed by an Answer Key & brief explanations."
                "Short Notes" -> "Create high-yield 1-page revision short notes with bullet points, definitions, and formulas."
                "Topic Summary" -> "Provide a crisp executive summary of the topic with key takeaways."
                "Weak Topic Coach" -> "Diagnose common mistakes on this weak topic and give Owner a 3-step targeted improvement drill."
                else -> "Teach clearly."
            }

            val res = aiRouter.generateSinglePrompt(
                prefs = prefs,
                systemHint = instruction,
                prompt = input,
                memoriesBlock = memoriesText,
                bookContextBlock = rag.formattedContextBlock
            )
            res.fold(
                onSuccess = { out ->
                    val citLabel = if (rag.citations.isNotEmpty()) {
                        "Book Citations: " + rag.citations.joinToString(" • ") {
                            "${it.documentTitle} (p.${it.pageNumber})"
                        }
                    } else ""
                    val today = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
                    plannerDao.insertActivityLog(
                        StudyActivityLogEntity(
                            activityType = if (mode == "MCQ Test") "QUIZ_COMPLETED" else "CHAT_STUDY",
                            subject = mode,
                            detail = input.take(60),
                            minutesSpent = 10,
                            epochDay = today
                        )
                    )
                    _teacherState.value = TeacherStudioState(
                        isLoading = false,
                        outputText = out,
                        citationsText = citLabel
                    )
                },
                onFailure = { err ->
                    _teacherState.value = TeacherStudioState(
                        isLoading = false,
                        error = err.message ?: "Teacher AI failed."
                    )
                }
            )
        }
    }

    fun clearToolStatus() {
        _toolStatus.value = null
    }

    private fun buildReport(period: String, logs: List<StudyActivityLogEntity>): StudyActivityReport {
        val breakdown = logs
            .groupBy { it.subject.ifBlank { "General" } }
            .mapValues { entry -> entry.value.sumOf { it.minutesSpent } }
            .toList()
            .sortedByDescending { it.second }
        return StudyActivityReport(
            periodLabel = period,
            totalMinutes = logs.sumOf { it.minutesSpent },
            completedTasks = logs.count { it.activityType == "TASK_COMPLETED" },
            solvedPyqs = logs.count { it.activityType == "PYQ_SOLVED" },
            docsRead = logs.count { it.activityType == "DOC_READ" },
            quizzesDone = logs.count { it.activityType == "QUIZ_COMPLETED" },
            subjectBreakdown = breakdown
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StudyToolsViewModel(
                        pyqDao = container.database.pyqDao(),
                        videoDao = container.database.videoDao(),
                        plannerDao = container.database.plannerDao(),
                        memoryDao = container.database.memoryDao(),
                        libraryDao = container.database.libraryDao(),
                        aiRouter = container.aiRouter,
                        ragEngine = container.ragEngine,
                        prefsRepo = container.preferencesRepository,
                        connectivityMonitor = container.connectivityMonitor,
                        pdfAndOcrProcessor = container.pdfAndOcrProcessor,
                        youTubeFetcher = container.youTubeMetadataFetcher,
                        reminderScheduler = container.reminderScheduler
                    ) as T
                }
            }
    }
}
