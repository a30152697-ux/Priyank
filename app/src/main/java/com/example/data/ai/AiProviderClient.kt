package com.example.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Modular AI provider contract allowing OpenAI, OpenRouter, Gemini,
 * or future local/cloud providers to be plugged in cleanly.
 */
interface AiProviderClient {
    fun streamChat(
        config: AiRequestConfig,
        systemPrompt: String,
        messages: List<AiTurnMessage>
    ): Flow<AiStreamEvent>

    suspend fun generateOnce(
        config: AiRequestConfig,
        systemPrompt: String,
        messages: List<AiTurnMessage>
    ): Result<String>

    suspend fun testConnection(config: AiRequestConfig): ConnectionTestResult
}
