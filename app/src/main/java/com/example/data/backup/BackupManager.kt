package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.core.security.InputValidator
import com.example.data.db.ChatMessageEntity
import com.example.data.db.ChatSessionEntity
import com.example.data.db.DocumentAnnotationEntity
import com.example.data.db.DocumentEntity
import com.example.data.db.DocumentPageChunkEntity
import com.example.data.db.MemoryEntity
import com.example.data.db.PyqEntity
import com.example.data.db.SavedVideoEntity
import com.example.data.db.StudyActivityLogEntity
import com.example.data.db.StudyGoalEntity
import com.example.data.db.StudyTaskEntity
import com.example.data.db.SukoonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupSummary(
    val schemaVersion: Int,
    val exportedAt: Long,
    val chatsCount: Int,
    val messagesCount: Int,
    val documentsCount: Int,
    val chunksCount: Int,
    val memoriesCount: Int,
    val pyqsCount: Int,
    val videosCount: Int,
    val tasksCount: Int,
    val goalsCount: Int,
    val activityLogsCount: Int,
    val embeddedFilesCount: Int
)

/**
 * Manual encrypted-key-free Backup & Restore manager.
 * Exports chats, memories, PYQs, videos, study plans, activity logs, doc metadata,
 * indexed chunks, annotations, and optionally imported private documents.
 * Never exports API keys or secrets.
 * Validates backup schema before restoring and requires explicit confirmation before overwrite.
 */
class BackupManager(
    private val context: Context,
    private val db: SukoonDatabase
) {

    suspend fun exportToUri(uri: Uri, includeImportedFiles: Boolean): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = buildBackupJson(includeImportedFiles)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                } ?: error("Cannot write to selected backup file.")
                parseSummary(root)
            }
        }

    suspend fun inspectBackupUri(uri: Uri): Result<Pair<JSONObject, BackupSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("Cannot read selected backup file.")
                val json = JSONObject(raw)
                validateBackupSchema(json)
                json to parseSummary(json)
            }
        }

    suspend fun restoreFromValidatedJson(
        root: JSONObject,
        overwriteExisting: Boolean
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            validateBackupSchema(root)
            val chatDao = db.chatDao()
            val libDao = db.libraryDao()
            val memDao = db.memoryDao()
            val pyqDao = db.pyqDao()
            val videoDao = db.videoDao()
            val plannerDao = db.plannerDao()

            if (overwriteExisting) {
                chatDao.deleteAllSessions()
                libDao.deleteAllDocuments()
                memDao.deleteAllMemories()
                pyqDao.deleteAllPyqs()
                videoDao.deleteAllVideos()
                plannerDao.deleteAllTasks()
                plannerDao.deleteAllGoals()
                plannerDao.deleteAllActivityLogs()
            }

            // 1. Restore Chat Sessions & Messages preserving relationships
            val sessionsArr = root.getJSONArray("chatSessions")
            val messagesArr = root.getJSONArray("chatMessages")
            val sessionIdMap = mutableMapOf<Long, Long>()
            for (i in 0 until sessionsArr.length()) {
                val o = sessionsArr.getJSONObject(i)
                val oldId = o.getLong("id")
                val newId = chatDao.insertSession(
                    ChatSessionEntity(
                        title = o.optString("title", "Restored Chat"),
                        rollingSummary = o.optString("rollingSummary", ""),
                        summarizedCount = o.optInt("summarizedCount", 0),
                        lastProviderName = o.optString("lastProviderName", ""),
                        lastModelId = o.optString("lastModelId", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
                sessionIdMap[oldId] = newId
            }
            for (i in 0 until messagesArr.length()) {
                val o = messagesArr.getJSONObject(i)
                val oldSessionId = o.getLong("sessionId")
                val newSessionId = sessionIdMap[oldSessionId] ?: continue
                chatDao.insertMessage(
                    ChatMessageEntity(
                        sessionId = newSessionId,
                        role = o.optString("role", "user"),
                        content = o.optString("content", ""),
                        providerUsed = o.optString("providerUsed", ""),
                        modelUsed = o.optString("modelUsed", ""),
                        citationsJson = o.optString("citationsJson", "[]"),
                        usedBookContext = o.optBoolean("usedBookContext", false),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }

            // 2. Restore Embedded Files (if any) & Documents + Chunks + Annotations
            val docsDir = File(context.filesDir, "library_docs").apply { mkdirs() }
            val embeddedMap = mutableMapOf<Long, String>()
            val filesArr = root.optJSONArray("embeddedFiles") ?: JSONArray()
            for (i in 0 until filesArr.length()) {
                val fo = filesArr.getJSONObject(i)
                val oldDocId = fo.getLong("documentId")
                val safeName = InputValidator.sanitizeFileName(fo.optString("fileName", "restored_doc.pdf"))
                val b64 = fo.optString("base64Data", "")
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val outFile = File(docsDir, "${System.currentTimeMillis()}_$safeName")
                    outFile.writeBytes(bytes)
                    embeddedMap[oldDocId] = outFile.absolutePath
                }
            }

            val docsArr = root.getJSONArray("documents")
            val docIdMap = mutableMapOf<Long, Long>()
            for (i in 0 until docsArr.length()) {
                val o = docsArr.getJSONObject(i)
                val oldDocId = o.getLong("id")
                val restoredPath = embeddedMap[oldDocId] ?: ""
                val newDocId = libDao.insertDocument(
                    DocumentEntity(
                        title = o.optString("title", "Document"),
                        subjectFolder = o.optString("subjectFolder", "General"),
                        fileType = o.optString("fileType", "PDF"),
                        localFilePath = restoredPath,
                        sourceUri = o.optString("sourceUri", ""),
                        pageCount = o.optInt("pageCount", 1),
                        lastReadPage = o.optInt("lastReadPage", 1),
                        isBookmarked = o.optBoolean("isBookmarked", false),
                        ocrStatus = o.optString("ocrStatus", "EXTRACTED"),
                        ocrConfidence = o.optDouble("ocrConfidence", 1.0).toFloat(),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
                docIdMap[oldDocId] = newDocId
            }

            val chunksArr = root.getJSONArray("documentChunks")
            val restoredChunks = mutableListOf<DocumentPageChunkEntity>()
            for (i in 0 until chunksArr.length()) {
                val o = chunksArr.getJSONObject(i)
                val newDocId = docIdMap[o.getLong("documentId")] ?: continue
                restoredChunks += DocumentPageChunkEntity(
                    documentId = newDocId,
                    documentTitle = o.optString("documentTitle", "Document"),
                    pageNumber = o.optInt("pageNumber", 1),
                    content = o.optString("content", ""),
                    isOcrDerived = o.optBoolean("isOcrDerived", false),
                    confidence = o.optDouble("confidence", 1.0).toFloat()
                )
            }
            if (restoredChunks.isNotEmpty()) {
                libDao.insertChunks(restoredChunks)
            }

            val annArr = root.optJSONArray("documentAnnotations") ?: JSONArray()
            for (i in 0 until annArr.length()) {
                val o = annArr.getJSONObject(i)
                val newDocId = docIdMap[o.getLong("documentId")] ?: continue
                libDao.insertAnnotation(
                    DocumentAnnotationEntity(
                        documentId = newDocId,
                        pageNumber = o.optInt("pageNumber", 1),
                        kind = o.optString("kind", "NOTE"),
                        excerpt = o.optString("excerpt", ""),
                        note = o.optString("note", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // 3. Memories (skip any accidentally tainted secrets)
            val memArr = root.getJSONArray("memories")
            for (i in 0 until memArr.length()) {
                val o = memArr.getJSONObject(i)
                val content = o.optString("content", "")
                if (content.isNotBlank() && !InputValidator.containsSensitiveSecret(content)) {
                    memDao.insertMemory(
                        MemoryEntity(
                            category = o.optString("category", "PREFERENCE"),
                            content = content,
                            source = o.optString("source", "RESTORED"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // 4. PYQs
            val pyqArr = root.getJSONArray("pyqs")
            for (i in 0 until pyqArr.length()) {
                val o = pyqArr.getJSONObject(i)
                pyqDao.insertPyq(
                    PyqEntity(
                        question = o.optString("question", ""),
                        examYear = o.optString("examYear", ""),
                        subject = o.optString("subject", "General"),
                        chapter = o.optString("chapter", ""),
                        marks = o.optInt("marks", 5),
                        difficulty = o.optString("difficulty", "Medium"),
                        answer = o.optString("answer", ""),
                        explanation = o.optString("explanation", ""),
                        isBookmarked = o.optBoolean("isBookmarked", false),
                        isSolved = o.optBoolean("isSolved", false),
                        sourceType = o.optString("sourceType", "Manual"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // 5. Saved Videos
            val vidArr = root.getJSONArray("videos")
            for (i in 0 until vidArr.length()) {
                val o = vidArr.getJSONObject(i)
                videoDao.insertVideo(
                    SavedVideoEntity(
                        url = o.optString("url", ""),
                        videoId = o.optString("videoId", ""),
                        title = o.optString("title", "Saved Video"),
                        channelName = o.optString("channelName", ""),
                        subject = o.optString("subject", "General"),
                        chapter = o.optString("chapter", ""),
                        notes = o.optString("notes", ""),
                        timestampsText = o.optString("timestampsText", ""),
                        tags = o.optString("tags", ""),
                        transcriptAvailable = o.optBoolean("transcriptAvailable", false),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // 6. Study Tasks, Goals & Activity Logs
            val tasksArr = root.getJSONArray("studyTasks")
            for (i in 0 until tasksArr.length()) {
                val o = tasksArr.getJSONObject(i)
                plannerDao.insertTask(
                    StudyTaskEntity(
                        title = o.optString("title", ""),
                        subject = o.optString("subject", "General"),
                        taskType = o.optString("taskType", "DAILY"),
                        dayOfWeek = o.optInt("dayOfWeek", 0),
                        scheduledTimeLabel = o.optString("scheduledTimeLabel", ""),
                        scheduledEpochDay = o.optLong("scheduledEpochDay", 0L),
                        durationMinutes = o.optInt("durationMinutes", 30),
                        isCompleted = o.optBoolean("isCompleted", false),
                        completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
                        reminderEnabled = o.optBoolean("reminderEnabled", false),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            val goalsArr = root.getJSONArray("studyGoals")
            for (i in 0 until goalsArr.length()) {
                val o = goalsArr.getJSONObject(i)
                plannerDao.insertGoal(
                    StudyGoalEntity(
                        title = o.optString("title", ""),
                        goalType = o.optString("goalType", "MONTHLY_GOAL"),
                        subject = o.optString("subject", "General"),
                        targetDateMillis = o.optLong("targetDateMillis", System.currentTimeMillis()),
                        progressPercent = o.optInt("progressPercent", 0),
                        notes = o.optString("notes", ""),
                        isCompleted = o.optBoolean("isCompleted", false),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            val logsArr = root.optJSONArray("activityLogs") ?: JSONArray()
            for (i in 0 until logsArr.length()) {
                val o = logsArr.getJSONObject(i)
                plannerDao.insertActivityLog(
                    StudyActivityLogEntity(
                        activityType = o.optString("activityType", "TASK_COMPLETED"),
                        subject = o.optString("subject", "General"),
                        detail = o.optString("detail", ""),
                        minutesSpent = o.optInt("minutesSpent", 15),
                        epochDay = o.optLong("epochDay", 0L),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }

            parseSummary(root)
        }
    }

    private suspend fun buildBackupJson(includeImportedFiles: Boolean): JSONObject {
        val chatDao = db.chatDao()
        val libDao = db.libraryDao()
        val memDao = db.memoryDao()
        val pyqDao = db.pyqDao()
        val videoDao = db.videoDao()
        val plannerDao = db.plannerDao()

        val sessions = chatDao.getAllSessionsOnce()
        val messages = chatDao.getAllMessagesOnce()
        val docs = libDao.getAllDocumentsOnce()
        val chunks = libDao.getAllChunksOnce()
        val annotations = libDao.getAllAnnotationsOnce()
        val memories = memDao.getAllMemoriesOnce()
        val pyqs = pyqDao.getAllPyqsOnce()
        val videos = videoDao.getAllVideosOnce()
        val tasks = plannerDao.getAllTasksOnce()
        val goals = plannerDao.getAllGoalsOnce()
        val logs = plannerDao.getAllActivityLogsOnce()

        val embeddedFiles = JSONArray()
        if (includeImportedFiles) {
            var embeddedTotalBytes = 0L
            for (doc in docs) {
                if (doc.localFilePath.isNotBlank()) {
                    val f = File(doc.localFilePath)
                    if (f.exists() && f.length() <= 10L * 1024L * 1024L && embeddedTotalBytes + f.length() <= 35L * 1024L * 1024L) {
                        embeddedTotalBytes += f.length()
                        val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                        embeddedFiles.put(JSONObject().apply {
                            put("documentId", doc.id)
                            put("fileName", f.name)
                            put("base64Data", b64)
                        })
                    }
                }
            }
        }

        return JSONObject().apply {
            put("app", "SUKOON_AI")
            put("owner", "Priyanksinh Raj")
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("chatSessions", JSONArray().apply {
                sessions.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("title", s.title)
                        put("rollingSummary", s.rollingSummary)
                        put("summarizedCount", s.summarizedCount)
                        put("lastProviderName", s.lastProviderName)
                        put("lastModelId", s.lastModelId)
                        put("createdAt", s.createdAt)
                        put("updatedAt", s.updatedAt)
                    })
                }
            })
            put("chatMessages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().apply {
                        put("sessionId", m.sessionId)
                        put("role", m.role)
                        put("content", m.content)
                        put("providerUsed", m.providerUsed)
                        put("modelUsed", m.modelUsed)
                        put("citationsJson", m.citationsJson)
                        put("usedBookContext", m.usedBookContext)
                        put("timestamp", m.timestamp)
                    })
                }
            })
            put("documents", JSONArray().apply {
                docs.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id)
                        put("title", d.title)
                        put("subjectFolder", d.subjectFolder)
                        put("fileType", d.fileType)
                        put("sourceUri", d.sourceUri)
                        put("pageCount", d.pageCount)
                        put("lastReadPage", d.lastReadPage)
                        put("isBookmarked", d.isBookmarked)
                        put("ocrStatus", d.ocrStatus)
                        put("ocrConfidence", d.ocrConfidence.toDouble())
                        put("createdAt", d.createdAt)
                        put("updatedAt", d.updatedAt)
                    })
                }
            })
            put("documentChunks", JSONArray().apply {
                chunks.forEach { c ->
                    put(JSONObject().apply {
                        put("documentId", c.documentId)
                        put("documentTitle", c.documentTitle)
                        put("pageNumber", c.pageNumber)
                        put("content", c.content)
                        put("isOcrDerived", c.isOcrDerived)
                        put("confidence", c.confidence.toDouble())
                    })
                }
            })
            put("documentAnnotations", JSONArray().apply {
                annotations.forEach { a ->
                    put(JSONObject().apply {
                        put("documentId", a.documentId)
                        put("pageNumber", a.pageNumber)
                        put("kind", a.kind)
                        put("excerpt", a.excerpt)
                        put("note", a.note)
                        put("createdAt", a.createdAt)
                    })
                }
            })
            put("embeddedFiles", embeddedFiles)
            put("memories", JSONArray().apply {
                memories.forEach { m ->
                    put(JSONObject().apply {
                        put("category", m.category)
                        put("content", m.content)
                        put("source", m.source)
                        put("createdAt", m.createdAt)
                        put("updatedAt", m.updatedAt)
                    })
                }
            })
            put("pyqs", JSONArray().apply {
                pyqs.forEach { p ->
                    put(JSONObject().apply {
                        put("question", p.question)
                        put("examYear", p.examYear)
                        put("subject", p.subject)
                        put("chapter", p.chapter)
                        put("marks", p.marks)
                        put("difficulty", p.difficulty)
                        put("answer", p.answer)
                        put("explanation", p.explanation)
                        put("isBookmarked", p.isBookmarked)
                        put("isSolved", p.isSolved)
                        put("sourceType", p.sourceType)
                        put("createdAt", p.createdAt)
                    })
                }
            })
            put("videos", JSONArray().apply {
                videos.forEach { v ->
                    put(JSONObject().apply {
                        put("url", v.url)
                        put("videoId", v.videoId)
                        put("title", v.title)
                        put("channelName", v.channelName)
                        put("subject", v.subject)
                        put("chapter", v.chapter)
                        put("notes", v.notes)
                        put("timestampsText", v.timestampsText)
                        put("tags", v.tags)
                        put("transcriptAvailable", v.transcriptAvailable)
                        put("createdAt", v.createdAt)
                    })
                }
            })
            put("studyTasks", JSONArray().apply {
                tasks.forEach { t ->
                    put(JSONObject().apply {
                        put("title", t.title)
                        put("subject", t.subject)
                        put("taskType", t.taskType)
                        put("dayOfWeek", t.dayOfWeek)
                        put("scheduledTimeLabel", t.scheduledTimeLabel)
                        put("scheduledEpochDay", t.scheduledEpochDay)
                        put("durationMinutes", t.durationMinutes)
                        put("isCompleted", t.isCompleted)
                        put("completedAt", t.completedAt ?: JSONObject.NULL)
                        put("reminderEnabled", t.reminderEnabled)
                        put("createdAt", t.createdAt)
                    })
                }
            })
            put("studyGoals", JSONArray().apply {
                goals.forEach { g ->
                    put(JSONObject().apply {
                        put("title", g.title)
                        put("goalType", g.goalType)
                        put("subject", g.subject)
                        put("targetDateMillis", g.targetDateMillis)
                        put("progressPercent", g.progressPercent)
                        put("notes", g.notes)
                        put("isCompleted", g.isCompleted)
                        put("createdAt", g.createdAt)
                    })
                }
            })
            put("activityLogs", JSONArray().apply {
                logs.forEach { l ->
                    put(JSONObject().apply {
                        put("activityType", l.activityType)
                        put("subject", l.subject)
                        put("detail", l.detail)
                        put("minutesSpent", l.minutesSpent)
                        put("epochDay", l.epochDay)
                        put("timestamp", l.timestamp)
                    })
                }
            })
        }
    }

    private fun validateBackupSchema(root: JSONObject) {
        if (root.optString("app") != "SUKOON_AI") {
            error("Invalid backup file: missing SUKOON_AI signature.")
        }
        val version = root.optInt("schemaVersion", -1)
        if (version != 1) {
            error("Unsupported backup schema version: $version")
        }
        val requiredArrays = listOf(
            "chatSessions", "chatMessages", "documents", "documentChunks",
            "memories", "pyqs", "videos", "studyTasks", "studyGoals"
        )
        for (key in requiredArrays) {
            if (root.optJSONArray(key) == null) {
                error("Corrupted backup: missing '$key' section.")
            }
        }
    }

    private fun parseSummary(root: JSONObject): BackupSummary = BackupSummary(
        schemaVersion = root.optInt("schemaVersion", 1),
        exportedAt = root.optLong("exportedAt", 0L),
        chatsCount = root.optJSONArray("chatSessions")?.length() ?: 0,
        messagesCount = root.optJSONArray("chatMessages")?.length() ?: 0,
        documentsCount = root.optJSONArray("documents")?.length() ?: 0,
        chunksCount = root.optJSONArray("documentChunks")?.length() ?: 0,
        memoriesCount = root.optJSONArray("memories")?.length() ?: 0,
        pyqsCount = root.optJSONArray("pyqs")?.length() ?: 0,
        videosCount = root.optJSONArray("videos")?.length() ?: 0,
        tasksCount = root.optJSONArray("studyTasks")?.length() ?: 0,
        goalsCount = root.optJSONArray("studyGoals")?.length() ?: 0,
        activityLogsCount = root.optJSONArray("activityLogs")?.length() ?: 0,
        embeddedFilesCount = root.optJSONArray("embeddedFiles")?.length() ?: 0
    )
}
