package com.example.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Handles OpenAI & OpenRouter (and compatible custom backend proxy endpoints)
 * using real Server-Sent Events (SSE) streaming via OkHttp.
 */
class OpenAiCompatibleClient(
    private val httpClient: OkHttpClient
) : AiProviderClient {

    override fun streamChat(
        config: AiRequestConfig,
        systemPrompt: String,
        messages: List<AiTurnMessage>
    ): Flow<AiStreamEvent> = flow {
        val credentialError = validateCredentials(config)
        if (credentialError != null) {
            emit(AiStreamEvent.Error(credentialError))
            return@flow
        }

        val url = resolveChatUrl(config)
        val bodyJson = buildPayload(
            modelId = config.modelId,
            systemPrompt = systemPrompt,
            messages = messages,
            stream = true
        )

        val request = buildRequest(url, config, bodyJson)
        val call = httpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    emit(
                        AiStreamEvent.Error(
                            message = parseApiError(config.provider, response.code, errBody),
                            httpCode = response.code
                        )
                    )
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(AiStreamEvent.Error("Empty response stream from ${config.provider.displayName}."))
                    return@flow
                }

                val accumulated = StringBuilder()
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isBlank()) continue

                    val chunk = extractSseDelta(data)
                    if (chunk.isNotEmpty()) {
                        accumulated.append(chunk)
                        emit(AiStreamEvent.Delta(chunk))
                    }
                }
                val finalText = accumulated.toString().trim()
                if (finalText.isEmpty()) {
                    emit(AiStreamEvent.Error("${config.provider.displayName} returned an empty response."))
                } else {
                    emit(AiStreamEvent.Completed(finalText))
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: IOException) {
            emit(AiStreamEvent.Error("Network error (${config.provider.displayName}): ${e.localizedMessage ?: "Connection failed"}"))
        } catch (e: Exception) {
            emit(AiStreamEvent.Error("Error (${config.provider.displayName}): ${e.localizedMessage ?: "Unknown failure"}"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateOnce(
        config: AiRequestConfig,
        systemPrompt: String,
        messages: List<AiTurnMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val credentialError = validateCredentials(config)
        if (credentialError != null) {
            return@withContext Result.failure(IllegalStateException(credentialError))
        }
        val url = resolveChatUrl(config)
        val bodyJson = buildPayload(
            modelId = config.modelId,
            systemPrompt = systemPrompt,
            messages = messages,
            stream = false
        )
        val request = buildRequest(url, config, bodyJson)
        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(parseApiError(config.provider, response.code, raw))
                    )
                }
                val root = JSONObject(raw)
                val text = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) {
                    Result.failure(IllegalStateException("Empty response from ${config.provider.displayName}."))
                } else {
                    Result.success(text)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(config: AiRequestConfig): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val result = generateOnce(
                config = config,
                systemPrompt = "Reply with only: SUKOON_OK",
                messages = listOf(AiTurnMessage(role = "user", text = "Connection check"))
            )
            val elapsed = System.currentTimeMillis() - start
            result.fold(
                onSuccess = {
                    ConnectionTestResult(
                        success = true,
                        message = "Connected to ${config.provider.displayName} (${config.modelId})",
                        latencyMs = elapsed
                    )
                },
                onFailure = { err ->
                    ConnectionTestResult(
                        success = false,
                        message = err.message ?: "Connection failed",
                        latencyMs = elapsed
                    )
                }
            )
        }

    private fun validateCredentials(config: AiRequestConfig): String? {
        if (config.modelId.isBlank()) {
            return "Owner, please select or enter a valid model ID in Settings."
        }
        if (config.useProxy) {
            if (config.proxyBaseUrl.isBlank()) {
                return "Owner, Proxy mode is enabled but no Backend Proxy URL is configured in Settings."
            }
            return null
        }
        if (config.apiKey.isBlank()) {
            return "Owner, ${config.provider.displayName} API key is not configured. Open Settings -> AI Providers to add your key or configure a backend proxy."
        }
        return null
    }

    private fun resolveChatUrl(config: AiRequestConfig): String {
        val base = if (config.useProxy && config.proxyBaseUrl.isNotBlank()) {
            config.proxyBaseUrl.trimEnd('/')
        } else {
            config.provider.defaultBaseUrl
        }
        return when (config.provider) {
            AiProviderType.OPENROUTER ->
                if (config.useProxy) "$base/openrouter/v1/chat/completions"
                else "$base/api/v1/chat/completions"
            AiProviderType.OPENAI -> "$base/v1/chat/completions"
            AiProviderType.GEMINI -> "$base/v1/chat/completions"
        }
    }

    private fun buildRequest(url: String, config: AiRequestConfig, bodyJson: JSONObject): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val builder = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(mediaType))
            .header("Content-Type", "application/json")

        val bearer = if (config.useProxy && config.proxyAuthToken.isNotBlank()) {
            config.proxyAuthToken
        } else {
            config.apiKey
        }
        if (bearer.isNotBlank()) {
            builder.header("Authorization", "Bearer $bearer")
        }
        if (config.provider == AiProviderType.OPENROUTER) {
            builder.header("HTTP-Referer", "https://sukoon.ai.local")
            builder.header("X-Title", "SUKOON AI")
        }
        return builder.build()
    }

    private fun buildPayload(
        modelId: String,
        systemPrompt: String,
        messages: List<AiTurnMessage>,
        stream: Boolean
    ): JSONObject {
        val msgArray = JSONArray()
        msgArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        for (msg in messages) {
            val role = if (msg.role == "assistant") "assistant" else "user"
            if (msg.imageBase64Jpeg != null && role == "user") {
                val parts = JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", msg.text)
                    })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,${msg.imageBase64Jpeg}")
                        })
                    })
                }
                msgArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", parts)
                })
            } else {
                msgArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.text)
                })
            }
        }
        return JSONObject().apply {
            put("model", modelId)
            put("messages", msgArray)
            put("stream", stream)
            put("temperature", 0.4)
        }
    }

    private fun extractSseDelta(jsonStr: String): String {
        return runCatching {
            val obj = JSONObject(jsonStr)
            val choices = obj.optJSONArray("choices") ?: return ""
            val first = choices.optJSONObject(0) ?: return ""
            val delta = first.optJSONObject("delta") ?: return ""
            delta.optString("content", "")
        }.getOrDefault("")
    }

    private fun parseApiError(provider: AiProviderType, code: Int, body: String): String {
        val serverMsg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            401 -> "${provider.displayName} authentication failed (401). Please verify your API key in Settings."
            402 -> "${provider.displayName} credits required (402). ${serverMsg}".trim()
            403 -> "${provider.displayName} access forbidden (403). ${serverMsg}".trim()
            404 -> "Model not found on ${provider.displayName} (404). Check model ID. $serverMsg".trim()
            429 -> "${provider.displayName} rate limit or quota exceeded (429). Please wait or switch provider."
            else -> "${provider.displayName} HTTP $code: ${serverMsg.ifBlank { body.take(180) }}"
        }
    }
}
