package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rollingSummary: String = "",
    val summarizedCount: Int = 0,
    val lastProviderName: String = "",
    val lastModelId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "user" | "assistant"
    val content: String,
    val providerUsed: String = "",
    val modelUsed: String = "",
    val citationsJson: String = "[]",
    val usedBookContext: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectFolder: String = "General",
    val fileType: String = "PDF", // "PDF" | "TXT" | "NOTE"
    val localFilePath: String = "",
    val sourceUri: String = "",
    val pageCount: Int = 1,
    val lastReadPage: Int = 1,
    val isBookmarked: Boolean = false,
    val ocrStatus: String = "EXTRACTED", // EXTRACTED | OCR_OK | OCR_LOW_CONFIDENCE | PENDING
    val ocrConfidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class DocumentPageChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val documentTitle: String,
    val pageNumber: Int,
    val content: String,
    val isOcrDerived: Boolean = false,
    val confidence: Float = 1.0f
)

@Entity(
    tableName = "document_annotations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class DocumentAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val kind: String, // "HIGHLIGHT" | "BOOKMARK" | "NOTE"
    val excerpt: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // PREFERENCE | GOAL | TOPIC | INSTRUCTION | CORRECTION | PERSONAL
    val content: String,
    val source: String = "MANUAL",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pyq_items")
data class PyqEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val examYear: String = "",
    val subject: String = "General",
    val chapter: String = "",
    val marks: Int = 5,
    val difficulty: String = "Medium", // Easy | Medium | Hard
    val answer: String = "",
    val explanation: String = "",
    val isBookmarked: Boolean = false,
    val isSolved: Boolean = false,
    val sourceType: String = "Manual", // Manual | Chat | Photo | PDF
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_videos")
data class SavedVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val videoId: String = "",
    val title: String,
    val channelName: String = "",
    val subject: String = "General",
    val chapter: String = "",
    val notes: String = "",
    val timestampsText: String = "",
    val tags: String = "",
    val transcriptAvailable: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String = "General",
    val taskType: String = "DAILY", // DAILY | TIMETABLE | REVISION
    val dayOfWeek: Int = 0, // 0=any/daily, 1=Mon..7=Sun
    val scheduledTimeLabel: String = "",
    val scheduledEpochDay: Long = 0L,
    val durationMinutes: Int = 30,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val reminderEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_goals")
data class StudyGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val goalType: String = "MONTHLY_GOAL", // MONTHLY_GOAL | EXAM_COUNTDOWN
    val subject: String = "General",
    val targetDateMillis: Long = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
    val progressPercent: Int = 0,
    val notes: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_activity_logs")
data class StudyActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String, // TASK_COMPLETED | PYQ_SOLVED | CHAT_STUDY | DOC_READ | QUIZ_COMPLETED
    val subject: String = "General",
    val detail: String,
    val minutesSpent: Int = 15,
    val epochDay: Long,
    val timestamp: Long = System.currentTimeMillis()
)
