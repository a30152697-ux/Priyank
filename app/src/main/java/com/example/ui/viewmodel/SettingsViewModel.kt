package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.network.ConnectivityMonitor
import com.example.core.plugins.SukoonPlugin
import com.example.core.plugins.SukoonToolRegistry
import com.example.core.preferences.ThemePreference
import com.example.core.preferences.UserPreferences
import com.example.core.preferences.UserPreferencesRepository
import com.example.core.security.SecureCredentialStore
import com.example.data.ai.AiProviderType
import com.example.data.ai.AiRouter
import com.example.data.ai.ConnectionTestResult
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class SettingsUiState(
    val keyPreviews: Map<AiProviderType, String> = emptyMap(),
    val hasProxyToken: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val isTesting: Boolean = false,
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
    val pendingRestoreJson: JSONObject? = null,
    val pendingRestoreSummary: BackupSummary? = null,
    val plugins: List<SukoonPlugin> = emptyList(),
    val pluginOutput: String? = null
)

class SettingsViewModel(
    private val prefsRepo: UserPreferencesRepository,
    private val credentialStore: SecureCredentialStore,
    private val aiRouter: AiRouter,
    private val backupManager: BackupManager,
    private val connectivityMonitor: ConnectivityMonitor,
    private val toolRegistry: SukoonToolRegistry
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            keyPreviews = refreshKeyPreviews(),
            hasProxyToken = credentialStore.getProxyAuthToken().isNotBlank(),
            plugins = toolRegistry.getAll()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { prefsRepo.setTheme(theme) }
    }

    fun selectProvider(provider: AiProviderType) {
        viewModelScope.launch {
            prefsRepo.setActiveProvider(provider, provider.defaultModel)
            _uiState.value = _uiState.value.copy(testResult = null)
        }
    }

    fun selectModel(model: String) {
        viewModelScope.launch { prefsRepo.setSelectedModel(model) }
    }

    fun setCustomModel(modelId: String, enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setCustomModel(modelId, enabled) }
    }

    fun saveApiKey(provider: AiProviderType, key: String) {
        credentialStore.saveApiKey(provider, key)
        _uiState.value = _uiState.value.copy(
            keyPreviews = refreshKeyPreviews(),
            testResult = null
        )
    }

    fun clearApiKey(provider: AiProviderType) {
        credentialStore.clearApiKey(provider)
        _uiState.value = _uiState.value.copy(
            keyPreviews = refreshKeyPreviews(),
            testResult = null
        )
    }

    fun saveProxyConfig(url: String, enabled: Boolean, bearerToken: String?) {
        viewModelScope.launch {
            prefsRepo.setProxyConfig(url, enabled)
            if (bearerToken != null) {
                credentialStore.saveProxyAuthToken(bearerToken)
            }
            _uiState.value = _uiState.value.copy(
                hasProxyToken = credentialStore.getProxyAuthToken().isNotBlank(),
                testResult = null
            )
        }
    }

    fun testActiveConnection() {
        connectivityMonitor.refresh()
        if (!connectivityMonitor.isOnline.value) {
            _uiState.value = _uiState.value.copy(
                testResult = ConnectionTestResult(false, "Device is currently offline.")
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResult = null)
            val cfg = aiRouter.buildConfig(preferences.value)
            val result = aiRouter.testConnection(cfg)
            _uiState.value = _uiState.value.copy(isTesting = false, testResult = result)
        }
    }

    fun setBookFirstRag(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setBookFirstRagEnabled(enabled) }
    }

    fun setConciseDefault(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setConciseAnswersDefault(enabled) }
    }

    fun setAskBookTransfer(ask: Boolean) {
        viewModelScope.launch { prefsRepo.setAskBeforeSendingBookContext(ask) }
    }

    fun setAskImageTransfer(ask: Boolean) {
        viewModelScope.launch { prefsRepo.setAskBeforeSendingImagesOnline(ask) }
    }

    fun setAskMemorySave(ask: Boolean) {
        viewModelScope.launch { prefsRepo.setAskBeforeSavingMemory(ask) }
    }

    // --- Backup & Restore ---
    fun exportBackup(uri: Uri, includeImportedFiles: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(backupBusy = true, backupMessage = null)
            val res = backupManager.exportToUri(uri, includeImportedFiles)
            res.fold(
                onSuccess = { sum ->
                    _uiState.value = _uiState.value.copy(
                        backupBusy = false,
                        backupMessage = "Backup exported: ${sum.chatsCount} chats, ${sum.documentsCount} docs, ${sum.pyqsCount} PYQs, ${sum.memoriesCount} memories, ${sum.embeddedFilesCount} embedded files."
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        backupBusy = false,
                        backupMessage = "Export failed: ${err.message}"
                    )
                }
            )
        }
    }

    fun inspectBackupBeforeRestore(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(backupBusy = true, backupMessage = null)
            val res = backupManager.inspectBackupUri(uri)
            res.fold(
                onSuccess = { (json, summary) ->
                    _uiState.value = _uiState.value.copy(
                        backupBusy = false,
                        pendingRestoreJson = json,
                        pendingRestoreSummary = summary
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        backupBusy = false,
                        backupMessage = "Backup validation failed: ${err.message}"
                    )
                }
            )
        }
    }

    fun confirmRestore(overwriteExisting: Boolean) {
        val json = _uiState.value.pendingRestoreJson ?: return
        _uiState.value = _uiState.value.copy(pendingRestoreJson = null, pendingRestoreSummary = null)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(backupBusy = true)
            val res = backupManager.restoreFromValidatedJson(json, overwriteExisting)
            res.fold(
                onSuccess = { sum ->
                    _uiState.value = _uiState.value.copy(
                        backupBusy = false,
                        backupMessage = "Restored ${sum.chatsCount} chats, ${sum.documentsCount} docs, ${sum.pyqsCount} PYQs, ${sum.tasksCount} tasks."
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        backupBusy = false,
                        backupMessage = "Restore failed: ${err.message}"
                    )
                }
            )
        }
    }

    fun cancelPendingRestore() {
        _uiState.value = _uiState.value.copy(pendingRestoreJson = null, pendingRestoreSummary = null)
    }

    fun runPlugin(pluginId: String, input: String) {
        val plugin = toolRegistry.getById(pluginId) ?: return
        viewModelScope.launch {
            val result = plugin.execute(input)
            _uiState.value = _uiState.value.copy(pluginOutput = result.output)
        }
    }

    fun clearBackupMessage() {
        _uiState.value = _uiState.value.copy(backupMessage = null)
    }

    private fun refreshKeyPreviews(): Map<AiProviderType, String> =
        AiProviderType.entries.associateWith { credentialStore.maskedKeyPreview(it) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        prefsRepo = container.preferencesRepository,
                        credentialStore = container.credentialStore,
                        aiRouter = container.aiRouter,
                        backupManager = container.backupManager,
                        connectivityMonitor = container.connectivityMonitor,
                        toolRegistry = container.toolRegistry
                    ) as T
                }
            }
    }
}
