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
 * Handles Google Gemini REST & SSE streaming API (`streamGenerateContent?alt=sse`)
 * using Owner-configured Gemini API key or a configured backend proxy URL.
 */
class GeminiRestClient(
    private val httpClient: OkHttpClient
) : AiProviderClient {

    override fun streamChat(
        config: AiRequestConfig,
        systemPrompt: String,
        messages: List<AiTurnMessage>
    ): Flow<AiStreamEvent> = flow {
        val err = validateCredentials(config)
        if (err != null) {
            emit(AiStreamEvent.Error(err))
            return@flow
        }

        val url = buildUrl(config, stream = true)
        val payload = buildPayload(systemPrompt, messages)
        val request = buildRequest(url, config, payload)
        val call = httpClient.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    emit(AiStreamEvent.Error(parseGeminiError(response.code, body), response.code))
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(AiStreamEvent.Error("Empty response stream from Google Gemini."))
                    return@flow
                }

                val accumulated = StringBuilder()
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue

                    val chunk = extractCandidateText(data)
                    if (chunk.isNotEmpty()) {
                        accumulated.append(chunk)
                        emit(AiStreamEvent.Delta(chunk))
                    }
                }
                val finalText = accumulated.toString().trim()
                if (finalText.isEmpty()) {
                    emit(AiStreamEvent.Error("Google Gemini returned an empty response."))
                } else {
                    emit(AiStreamEvent.Completed(finalText))
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: IOException) {
            emit(AiStreamEvent.Error("Network error (Google Gemini): ${e.localizedMessage ?: "Connection failed"}"))
        } catch (e: Exception) {
            emit(AiStreamEvent.Error("Error (Google Gemini): ${e.localizedMessage ?: "Unknown failure"}"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateOnce(
        config: AiRequestConfig,
        systemPrompt: String,
        messages: List<AiTurnMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val err = validateCredentials(config)
        if (err != null) {
            return@withContext Result.failure(IllegalStateException(err))
        }
        val url = buildUrl(config, stream = false)
        val payload = buildPayload(systemPrompt, messages)
        val request = buildRequest(url, config, payload)

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(parseGeminiError(response.code, raw))
                    )
                }
                val text = extractCandidateText(raw).trim()
                if (text.isEmpty()) {
                    Result.failure(IllegalStateException("Empty response from Google Gemini."))
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
            val res = generateOnce(
                config = config,
                systemPrompt = "Reply with only: SUKOON_OK",
                messages = listOf(AiTurnMessage(role = "user", text = "Connection check"))
            )
            val elapsed = System.currentTimeMillis() - start
            res.fold(
                onSuccess = {
                    ConnectionTestResult(
                        success = true,
                        message = "Connected to Google Gemini (${config.modelId})",
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
            return "Owner, please choose or enter a Gemini model ID in Settings."
        }
        if (config.useProxy) {
            if (config.proxyBaseUrl.isBlank()) {
                return "Owner, Proxy mode is enabled but no Backend Proxy URL is configured."
            }
            return null
        }
        if (config.apiKey.isBlank()) {
            return "Owner, Google Gemini API key is not configured. Open Settings -> AI Providers to enter your key or configure a backend proxy."
        }
        return null
    }

    private fun buildUrl(config: AiRequestConfig, stream: Boolean): String {
        val model = config.modelId.removePrefix("models/")
        val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        return if (config.useProxy && config.proxyBaseUrl.isNotBlank()) {
            val base = config.proxyBaseUrl.trimEnd('/')
            "$base/gemini/v1beta/models/$model:$action"
        } else {
            "https://generativelanguage.googleapis.com/v1beta/models/$model:$action"
        }
    }

    private fun buildRequest(url: String, config: AiRequestConfig, payload: JSONObject): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(mediaType))
            .header("Content-Type", "application/json")

        if (config.useProxy) {
            if (config.proxyAuthToken.isNotBlank()) {
                builder.header("Authorization", "Bearer ${config.proxyAuthToken}")
            }
        } else {
            // Pass API key via header rather than URL query param so it stays out of URL logs
            builder.header("x-goog-api-key", config.apiKey)
        }
        return builder.build()
    }

    private fun buildPayload(
        systemPrompt: String,
        messages: List<AiTurnMessage>
    ): JSONObject {
        val contents = JSONArray()
        for (msg in messages) {
            val role = if (msg.role == "assistant") "model" else "user"
            val parts = JSONArray().apply {
                if (msg.text.isNotBlank()) {
                    put(JSONObject().apply { put("text", msg.text) })
                }
                if (msg.imageBase64Jpeg != null && role == "user") {
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", msg.imageBase64Jpeg)
                        })
                    })
                }
            }
            contents.put(JSONObject().apply {
                put("role", role)
                put("parts", parts)
            })
        }
        return JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", systemPrompt)
                }))
            })
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
            })
        }
    }

    private fun extractCandidateText(jsonStr: String): String {
        return runCatching {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates") ?: return ""
            val first = candidates.optJSONObject(0) ?: return ""
            val content = first.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            buildString {
                for (i in 0 until parts.length()) {
                    append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
                }
            }
        }.getOrDefault("")
    }

    private fun parseGeminiError(code: Int, body: String): String {
        val msg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            400 -> "Gemini request rejected (400): ${msg.ifBlank { "Check model ID or payload" }}"
            401, 403 -> "Gemini API key unauthorized ($code). Please verify your key in Settings. $msg".trim()
            404 -> "Gemini model not found (404). Verify model ID in Settings. $msg".trim()
            429 -> "Gemini quota/rate limit reached (429). Wait briefly or switch provider."
            else -> "Gemini HTTP $code: ${msg.ifBlank { body.take(180) }}"
        }
    }
}
