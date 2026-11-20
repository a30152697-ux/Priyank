package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchSessions(query: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): ChatSessionEntity?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesOnce(sessionId: Long): List<ChatMessageEntity>

    @Query("SELECT DISTINCT sessionId FROM chat_messages WHERE content LIKE '%' || :query || '%'")
    suspend fun findSessionIdsMatchingMessage(query: String): List<Long>

    @Query("SELECT * FROM chat_sessions")
    suspend fun getAllSessionsOnce(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_messages ORDER BY sessionId ASC, timestamp ASC, id ASC")
    suspend fun getAllMessagesOnce(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND id >= :fromMessageId")
    suspend fun deleteMessagesFrom(sessionId: Long, fromMessageId: Long)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :docId")
    fun observeDocument(docId: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :docId")
    suspend fun getDocumentOnce(docId: Long): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun getAllDocumentsOnce(): List<DocumentEntity>

    @Query("SELECT * FROM document_chunks WHERE documentId = :docId ORDER BY pageNumber ASC")
    fun observeChunksForDocument(docId: Long): Flow<List<DocumentPageChunkEntity>>

    @Query("SELECT * FROM document_chunks WHERE documentId = :docId ORDER BY pageNumber ASC")
    suspend fun getChunksForDocumentOnce(docId: Long): List<DocumentPageChunkEntity>

    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunksOnce(): List<DocumentPageChunkEntity>

    @Query("SELECT * FROM document_annotations WHERE documentId = :docId ORDER BY pageNumber ASC, createdAt DESC")
    fun observeAnnotations(docId: Long): Flow<List<DocumentAnnotationEntity>>

    @Query("SELECT * FROM document_annotations")
    suspend fun getAllAnnotationsOnce(): List<DocumentAnnotationEntity>

    @Query("SELECT DISTINCT documentId FROM document_chunks WHERE content LIKE '%' || :query || '%'")
    suspend fun searchDocumentIdsByContent(query: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :docId")
    suspend fun deleteDocument(docId: Long)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentPageChunkEntity>)

    @Query("DELETE FROM document_chunks WHERE documentId = :docId")
    suspend fun deleteChunksForDocument(docId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: DocumentAnnotationEntity): Long

    @Delete
    suspend fun deleteAnnotation(annotation: DocumentAnnotationEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAllMemoriesOnce(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()
}

@Dao
interface PyqDao {
    @Query("SELECT * FROM pyq_items ORDER BY createdAt DESC")
    fun observePyqs(): Flow<List<PyqEntity>>

    @Query("SELECT * FROM pyq_items ORDER BY createdAt DESC")
    suspend fun getAllPyqsOnce(): List<PyqEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPyq(item: PyqEntity): Long

    @Update
    suspend fun updatePyq(item: PyqEntity)

    @Query("DELETE FROM pyq_items WHERE id = :id")
    suspend fun deletePyq(id: Long)

    @Query("DELETE FROM pyq_items")
    suspend fun deleteAllPyqs()
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM saved_videos ORDER BY createdAt DESC")
    fun observeVideos(): Flow<List<SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos ORDER BY createdAt DESC")
    suspend fun getAllVideosOnce(): List<SavedVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: SavedVideoEntity): Long

    @Update
    suspend fun updateVideo(video: SavedVideoEntity)

    @Query("DELETE FROM saved_videos WHERE id = :id")
    suspend fun deleteVideo(id: Long)

    @Query("DELETE FROM saved_videos")
    suspend fun deleteAllVideos()
}

@Dao
interface PlannerDao {
    @Query("SELECT * FROM study_tasks ORDER BY isCompleted ASC, dayOfWeek ASC, createdAt DESC")
    fun observeTasks(): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks")
    suspend fun getAllTasksOnce(): List<StudyTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: StudyTaskEntity): Long

    @Update
    suspend fun updateTask(task: StudyTaskEntity)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM study_tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT * FROM study_goals ORDER BY isCompleted ASC, targetDateMillis ASC")
    fun observeGoals(): Flow<List<StudyGoalEntity>>

    @Query("SELECT * FROM study_goals")
    suspend fun getAllGoalsOnce(): List<StudyGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: StudyGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: StudyGoalEntity)

    @Query("DELETE FROM study_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM study_goals")
    suspend fun deleteAllGoals()

    @Query("SELECT * FROM study_activity_logs ORDER BY timestamp DESC")
    fun observeActivityLogs(): Flow<List<StudyActivityLogEntity>>

    @Query("SELECT * FROM study_activity_logs ORDER BY timestamp DESC")
    suspend fun getAllActivityLogsOnce(): List<StudyActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: StudyActivityLogEntity): Long

    @Query("DELETE FROM study_activity_logs")
    suspend fun deleteAllActivityLogs()
}
