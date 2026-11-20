package com.example

import android.app.Application
import com.example.core.network.ConnectivityMonitor
import com.example.core.plugins.FormulaScratchpadPlugin
import com.example.core.plugins.SukoonToolRegistry
import com.example.core.preferences.UserPreferencesRepository
import com.example.core.security.SecureCredentialStore
import com.example.data.ai.AiRouter
import com.example.data.ai.GeminiRestClient
import com.example.data.ai.OpenAiCompatibleClient
import com.example.data.backup.BackupManager
import com.example.data.db.SukoonDatabase
import com.example.data.pdf.PdfAndOcrProcessor
import com.example.data.pdf.RagEngine
import com.example.data.work.StudyReminderScheduler
import com.example.data.work.StudyReminderWorker
import com.example.data.youtube.YouTubeMetadataFetcher
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SukoonApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        StudyReminderWorker.ensureNotificationChannel(applicationContext)
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database: SukoonDatabase by lazy { SukoonDatabase.getInstance(application) }
    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(application)
    }
    val credentialStore: SecureCredentialStore by lazy { SecureCredentialStore(application) }
    val connectivityMonitor: ConnectivityMonitor by lazy { ConnectivityMonitor(application) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val openAiCompatibleClient: OpenAiCompatibleClient by lazy {
        OpenAiCompatibleClient(httpClient)
    }
    val geminiRestClient: GeminiRestClient by lazy { GeminiRestClient(httpClient) }
    val aiRouter: AiRouter by lazy {
        AiRouter(credentialStore, openAiCompatibleClient, geminiRestClient)
    }

    val pdfAndOcrProcessor: PdfAndOcrProcessor by lazy { PdfAndOcrProcessor(application) }
    val ragEngine: RagEngine by lazy { RagEngine() }
    val youTubeMetadataFetcher: YouTubeMetadataFetcher by lazy { YouTubeMetadataFetcher(httpClient) }
    val backupManager: BackupManager by lazy { BackupManager(application, database) }
    val reminderScheduler: StudyReminderScheduler by lazy { StudyReminderScheduler(application) }

    val toolRegistry: SukoonToolRegistry by lazy {
        SukoonToolRegistry().apply {
            register(FormulaScratchpadPlugin())
        }
    }
}
