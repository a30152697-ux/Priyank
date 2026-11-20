package com.example.data.ai

import com.example.core.identity.SukoonIdentity
import com.example.core.preferences.UserPreferences
import com.example.core.security.SecureCredentialStore
import com.example.data.db.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Central AI orchestrator:
 * - Resolves active provider (OpenRouter, OpenAI, Google Gemini) & encrypted credentials
 * - Injects immutable SUKOON identity + prompt-injection guardrails
 * - Manages context windows via rolling conversation summarization (no false "unlimited context" claims)
 */
class AiRouter(
    private val credentialStore: SecureCredentialStore,
    private val openAiCompatibleClient: OpenAiCompatibleClient,
    private val geminiRestClient: GeminiRestClient
) {

    fun buildConfig(prefs: UserPreferences, providerOverride: AiProviderType? = null): AiRequestConfig {
        val provider = providerOverride ?: prefs.activeProvider
        val modelId = if (providerOverride != null && providerOverride != prefs.activeProvider) {
            providerOverride.defaultModel
        } else {
            prefs.effectiveModelId()
        }
        return AiRequestConfig(
            provider = provider,
            modelId = modelId,
            apiKey = credentialStore.getApiKey(provider),
            proxyBaseUrl = prefs.proxyBaseUrl,
            proxyAuthToken = credentialStore.getProxyAuthToken(),
            useProxy = prefs.useProxyBackend
        )
    }

    fun streamConversation(
        prefs: UserPreferences,
        historyMessages: List<ChatMessageEntity>,
        rollingSummary: String,
        summarizedCount: Int,
        memoriesBlock: String,
        bookContextBlock: String,
        teachingModeHint: String,
        userMessageText: String,
        attachedImageBase64: String? = null
    ): Flow<AiStreamEvent> {
        val config = buildConfig(prefs)
        val systemPrompt = SukoonIdentity.buildSystemPrompt(
            memoriesSummary = memoriesBlock,
            conversationSummary = rollingSummary,
            teachingModeHint = teachingModeHint,
            bookContextBlock = bookContextBlock,
            conciseByDefault = prefs.conciseAnswersDefault
        )

        // Keep only the active recent window beyond summarizedCount to fit provider context limits
        val recentSlice = historyMessages
            .drop(summarizedCount.coerceAtLeast(0))
            .takeLast(MAX_RECENT_TURNS)
            .map { AiTurnMessage(role = it.role, text = it.content) }

        val turns = recentSlice + AiTurnMessage(
            role = "user",
            text = userMessageText,
            imageBase64Jpeg = attachedImageBase64
        )

        return clientFor(config.provider).streamChat(config, systemPrompt, turns)
    }

    suspend fun generateSinglePrompt(
        prefs: UserPreferences,
        systemHint: String = "",
        prompt: String,
        memoriesBlock: String = "",
        bookContextBlock: String = "",
        imageBase64Jpeg: String? = null
    ): Result<String> {
        val config = buildConfig(prefs)
        val sys = SukoonIdentity.buildSystemPrompt(
            memoriesSummary = memoriesBlock,
            teachingModeHint = systemHint,
            bookContextBlock = bookContextBlock,
            conciseByDefault = prefs.conciseAnswersDefault
        )
        return clientFor(config.provider).generateOnce(
            config = config,
            systemPrompt = sys,
            messages = listOf(AiTurnMessage("user", prompt, imageBase64Jpeg))
        )
    }

    suspend fun summarizeOlderMessages(
        prefs: UserPreferences,
        existingSummary: String,
        messagesToCompress: List<ChatMessageEntity>
    ): String {
        if (messagesToCompress.isEmpty()) return existingSummary
        val config = buildConfig(prefs)
        val transcript = messagesToCompress.joinToString("\n") {
            "${if (it.role == "user") "Owner" else "SUKOON"}: ${it.content.take(500)}"
        }
        val prompt = buildString {
            appendLine("Condense this study conversation into a concise factual summary (max 180 words) preserving key topics, formulas, Owner's goals, and unresolved questions.")
            if (existingSummary.isNotBlank()) {
                appendLine("Previous summary: $existingSummary")
            }
            appendLine("New messages:")
            appendLine(transcript)
        }

        val aiResult = clientFor(config.provider).generateOnce(
            config = config,
            systemPrompt = "You are SUKOON summarizing study context for Owner Priyanksinh Raj. Keep only accurate facts.",
            messages = listOf(AiTurnMessage("user", prompt))
        )

        return aiResult.getOrElse {
            // Deterministic extractive fallback if offline or API fails
            val fallbackBullets = messagesToCompress.takeLast(6).joinToString(" | ") {
                "${it.role}: ${it.content.replace("\n", " ").take(90)}"
            }
            listOf(existingSummary, fallbackBullets).filter { s -> s.isNotBlank() }.joinToString("\n")
                .take(1200)
        }
    }

    suspend fun testConnection(config: AiRequestConfig): ConnectionTestResult {
        return clientFor(config.provider).testConnection(config)
    }

    private fun clientFor(provider: AiProviderType): AiProviderClient = when (provider) {
        AiProviderType.GEMINI -> geminiRestClient
        AiProviderType.OPENAI, AiProviderType.OPENROUTER -> openAiCompatibleClient
    }

    companion object {
        const val MAX_RECENT_TURNS = 12
        const val SUMMARIZE_THRESHOLD = 16
    }
}
