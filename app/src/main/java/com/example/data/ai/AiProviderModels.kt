package com.example.data.ai

enum class AiProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val keyHint: String
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-2.5-flash",
        suggestedModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash-lite"
        ),
        keyHint = "AIza..."
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com",
        defaultModel = "gpt-4o-mini",
        suggestedModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "o3-mini"
        ),
        keyHint = "sk-..."
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai",
        defaultModel = "google/gemini-2.5-flash",
        suggestedModels = listOf(
            "google/gemini-2.5-flash",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.7-sonnet",
            "deepseek/deepseek-r1"
        ),
        keyHint = "sk-or-v1-..."
    )
}

data class AiRequestConfig(
    val provider: AiProviderType,
    val modelId: String,
    val apiKey: String,
    val proxyBaseUrl: String = "",
    val proxyAuthToken: String = "",
    val useProxy: Boolean = false
)

data class AiTurnMessage(
    val role: String, // "user" | "assistant"
    val text: String,
    val imageBase64Jpeg: String? = null
)

sealed interface AiStreamEvent {
    data class Delta(val textChunk: String) : AiStreamEvent
    data class Completed(val fullText: String) : AiStreamEvent
    data class Error(val message: String, val httpCode: Int? = null) : AiStreamEvent
}

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0L
)
