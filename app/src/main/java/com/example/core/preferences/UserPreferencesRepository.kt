package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.ai.AiProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sukoonDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sukoon_preferences"
)

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class UserPreferences(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val activeProvider: AiProviderType = AiProviderType.GEMINI,
    val selectedModel: String = "gemini-2.5-flash",
    val customModelId: String = "",
    val useCustomModel: Boolean = false,
    val proxyBaseUrl: String = "",
    val useProxyBackend: Boolean = false,
    val bookFirstRagEnabled: Boolean = true,
    val conciseAnswersDefault: Boolean = true,
    val askBeforeSendingBookContext: Boolean = true,
    val askBeforeSendingImagesOnline: Boolean = true,
    val askBeforeSavingMemory: Boolean = true
) {
    fun effectiveModelId(): String =
        if (useCustomModel && customModelId.isNotBlank()) customModelId.trim() else selectedModel
}

class UserPreferencesRepository(private val context: Context) {

    val preferencesFlow: Flow<UserPreferences> = context.sukoonDataStore.data.map { prefs ->
        val theme = runCatching {
            ThemePreference.valueOf(prefs[KEY_THEME] ?: ThemePreference.SYSTEM.name)
        }.getOrDefault(ThemePreference.SYSTEM)

        val provider = runCatching {
            AiProviderType.valueOf(prefs[KEY_PROVIDER] ?: AiProviderType.GEMINI.name)
        }.getOrDefault(AiProviderType.GEMINI)

        UserPreferences(
            themePreference = theme,
            activeProvider = provider,
            selectedModel = prefs[KEY_MODEL] ?: provider.defaultModel,
            customModelId = prefs[KEY_CUSTOM_MODEL] ?: "",
            useCustomModel = prefs[KEY_USE_CUSTOM_MODEL] ?: false,
            proxyBaseUrl = prefs[KEY_PROXY_URL] ?: "",
            useProxyBackend = prefs[KEY_USE_PROXY] ?: false,
            bookFirstRagEnabled = prefs[KEY_BOOK_FIRST_RAG] ?: true,
            conciseAnswersDefault = prefs[KEY_CONCISE_DEFAULT] ?: true,
            askBeforeSendingBookContext = prefs[KEY_ASK_BOOK_TRANSFER] ?: true,
            askBeforeSendingImagesOnline = prefs[KEY_ASK_IMAGE_TRANSFER] ?: true,
            askBeforeSavingMemory = prefs[KEY_ASK_MEMORY] ?: true
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.sukoonDataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setActiveProvider(provider: AiProviderType, defaultModel: String) {
        context.sukoonDataStore.edit {
            it[KEY_PROVIDER] = provider.name
            it[KEY_MODEL] = defaultModel
            it[KEY_USE_CUSTOM_MODEL] = false
        }
    }

    suspend fun setSelectedModel(model: String) {
        context.sukoonDataStore.edit {
            it[KEY_MODEL] = model
            it[KEY_USE_CUSTOM_MODEL] = false
        }
    }

    suspend fun setCustomModel(customModel: String, enabled: Boolean) {
        context.sukoonDataStore.edit {
            it[KEY_CUSTOM_MODEL] = customModel.trim()
            it[KEY_USE_CUSTOM_MODEL] = enabled
        }
    }

    suspend fun setProxyConfig(url: String, enabled: Boolean) {
        context.sukoonDataStore.edit {
            it[KEY_PROXY_URL] = url.trim().trimEnd('/')
            it[KEY_USE_PROXY] = enabled
        }
    }

    suspend fun setBookFirstRagEnabled(enabled: Boolean) {
        context.sukoonDataStore.edit { it[KEY_BOOK_FIRST_RAG] = enabled }
    }

    suspend fun setConciseAnswersDefault(enabled: Boolean) {
        context.sukoonDataStore.edit { it[KEY_CONCISE_DEFAULT] = enabled }
    }

    suspend fun setAskBeforeSendingBookContext(ask: Boolean) {
        context.sukoonDataStore.edit { it[KEY_ASK_BOOK_TRANSFER] = ask }
    }

    suspend fun setAskBeforeSendingImagesOnline(ask: Boolean) {
        context.sukoonDataStore.edit { it[KEY_ASK_IMAGE_TRANSFER] = ask }
    }

    suspend fun setAskBeforeSavingMemory(ask: Boolean) {
        context.sukoonDataStore.edit { it[KEY_ASK_MEMORY] = ask }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_PROVIDER = stringPreferencesKey("active_provider")
        val KEY_MODEL = stringPreferencesKey("selected_model")
        val KEY_CUSTOM_MODEL = stringPreferencesKey("custom_model")
        val KEY_USE_CUSTOM_MODEL = booleanPreferencesKey("use_custom_model")
        val KEY_PROXY_URL = stringPreferencesKey("proxy_base_url")
        val KEY_USE_PROXY = booleanPreferencesKey("use_proxy_backend")
        val KEY_BOOK_FIRST_RAG = booleanPreferencesKey("book_first_rag")
        val KEY_CONCISE_DEFAULT = booleanPreferencesKey("concise_answers_default")
        val KEY_ASK_BOOK_TRANSFER = booleanPreferencesKey("ask_book_transfer")
        val KEY_ASK_IMAGE_TRANSFER = booleanPreferencesKey("ask_image_transfer")
        val KEY_ASK_MEMORY = booleanPreferencesKey("ask_memory_save")
    }
}
