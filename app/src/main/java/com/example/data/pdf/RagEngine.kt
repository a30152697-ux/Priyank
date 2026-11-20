package com.example.data.pdf

import com.example.core.security.InputValidator
import com.example.data.db.DocumentPageChunkEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

data class RagCitation(
    val documentId: Long,
    val documentTitle: String,
    val pageNumber: Int,
    val snippet: String,
    val score: Double,
    val isOcrDerived: Boolean,
    val confidence: Float
)

data class RagSearchResult(
    val citations: List<RagCitation>,
    val formattedContextBlock: String
) {
    val hasRelevantBookEvidence: Boolean get() = citations.isNotEmpty()
}

/**
 * Local Book-First RAG retrieval engine:
 * - Indexes & scores every imported document page chunk locally using BM25 + bigram phrase matching
 * - Preserves exact document titles and page numbers
 * - Never invents citations; wraps retrieved passages in untrusted-content delimiters
 */
class RagEngine {

    private val stopWords = setOf(
        "the", "is", "are", "was", "were", "a", "an", "and", "or", "of", "to", "in", "on", "for",
        "with", "that", "this", "it", "as", "by", "from", "at", "be", "what", "how", "why", "when",
        "explain", "define", "tell", "me", "about", "please", "owner", "sukoon", "kya", "hai", "aur",
        "ka", "ki", "ke", "mein", "se", "ko", "par"
    )

    fun searchRelevantPassages(
        query: String,
        allChunks: List<DocumentPageChunkEntity>,
        topK: Int = 5
    ): RagSearchResult {
        if (allChunks.isEmpty() || query.isBlank()) {
            return RagSearchResult(emptyList(), "")
        }

        val queryTokens = tokenize(query).filterNot { it in stopWords }.ifEmpty { tokenize(query) }
        if (queryTokens.isEmpty()) return RagSearchResult(emptyList(), "")

        val queryBigrams = queryTokens.zipWithNext { a, b -> "$a $b" }.toSet()
        val docFreq = mutableMapOf<String, Int>()
        val chunkTokenLists = allChunks.map { chunk ->
            val tokens = tokenize(chunk.content)
            tokens.toSet().forEach { tok ->
                docFreq[tok] = (docFreq[tok] ?: 0) + 1
            }
            tokens
        }

        val n = allChunks.size.toDouble()
        val avgLen = chunkTokenLists.map { it.size }.average().coerceAtLeast(1.0)
        val k1 = 1.5
        val b = 0.75

        val scored = mutableListOf<RagCitation>()
        allChunks.forEachIndexed { idx, chunk ->
            val tokens = chunkTokenLists[idx]
            if (tokens.isEmpty()) return@forEachIndexed
            val tfMap = tokens.groupingBy { it }.eachCount()
            val titleLower = chunk.documentTitle.lowercase()

            var score = 0.0
            var matchedTerms = 0
            for (qTok in queryTokens.distinct()) {
                val tf = tfMap[qTok]?.toDouble() ?: 0.0
                // Prefix/stem partial match boost for inflected words
                val partialTf = if (tf == 0.0 && qTok.length >= 4) {
                    tokens.count { it.startsWith(qTok) || (it.length >= 4 && qTok.startsWith(it)) } * 0.5
                } else 0.0
                val effectiveTf = tf + partialTf
                if (effectiveTf > 0.0) {
                    matchedTerms++
                    val df = (docFreq[qTok] ?: 0).toDouble()
                    val idf = ln((n - df + 0.5) / (df + 0.5) + 1.0)
                    val norm = effectiveTf * (k1 + 1.0) /
                        (effectiveTf + k1 * (1.0 - b + b * (tokens.size / avgLen)))
                    score += idf * norm
                }
                if (titleLower.contains(qTok)) {
                    score += 0.65
                }
            }

            // Bigram phrase proximity bonus
            if (queryBigrams.isNotEmpty()) {
                val contentBigrams = tokens.zipWithNext { x, y -> "$x $y" }.toSet()
                val bigramMatches = queryBigrams.count { it in contentBigrams }
                score += bigramMatches * 1.4
            }

            if (matchedTerms > 0 && score >= 0.45) {
                scored += RagCitation(
                    documentId = chunk.documentId,
                    documentTitle = chunk.documentTitle,
                    pageNumber = chunk.pageNumber,
                    snippet = buildRelevantSnippet(chunk.content, queryTokens),
                    score = score,
                    isOcrDerived = chunk.isOcrDerived,
                    confidence = chunk.confidence
                )
            }
        }

        val topCitations = scored
            .sortedByDescending { it.score }
            .take(topK)

        if (topCitations.isEmpty()) {
            return RagSearchResult(emptyList(), "")
        }

        val contextBlock = topCitations.joinToString("\n\n") { c ->
            val chunkText = allChunks.firstOrNull {
                it.documentId == c.documentId && it.pageNumber == c.pageNumber
            }?.content ?: c.snippet
            val trimmedPassage = chunkText.take(1600)
            val ocrNotice = if (c.isOcrDerived && c.confidence < 0.65f) {
                "[OCR Notice: Low-confidence scan extraction (${(c.confidence * 100).toInt()}%)]\n"
            } else ""
            InputValidator.wrapUntrustedDocumentText(
                docName = c.documentTitle,
                pageNumber = c.pageNumber,
                rawText = ocrNotice + trimmedPassage
            )
        }

        return RagSearchResult(topCitations, contextBlock)
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 2 }

    private fun buildRelevantSnippet(content: String, queryTokens: List<String>): String {
        val clean = content.replace(Regex("""\s+"""), " ").trim()
        if (clean.length <= 240) return clean
        val lower = clean.lowercase()
        val firstMatchIdx = queryTokens
            .mapNotNull { tok -> lower.indexOf(tok).takeIf { it >= 0 } }
            .minOrNull() ?: 0
        val start = (firstMatchIdx - 80).coerceAtLeast(0)
        val end = (start + 240).coerceAtMost(clean.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < clean.length) "…" else ""
        return prefix + clean.substring(start, end).trim() + suffix
    }

    companion object {
        fun serializeCitations(citations: List<RagCitation>): String {
            val arr = JSONArray()
            citations.forEach { c ->
                arr.put(JSONObject().apply {
                    put("documentId", c.documentId)
                    put("documentTitle", c.documentTitle)
                    put("pageNumber", c.pageNumber)
                    put("snippet", c.snippet)
                    put("isOcrDerived", c.isOcrDerived)
                    put("confidence", c.confidence.toDouble())
                })
            }
            return arr.toString()
        }

        fun deserializeCitations(json: String): List<RagCitation> {
            if (json.isBlank() || json == "[]") return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    RagCitation(
                        documentId = o.optLong("documentId"),
                        documentTitle = o.optString("documentTitle"),
                        pageNumber = o.optInt("pageNumber", 1),
                        snippet = o.optString("snippet"),
                        score = 1.0,
                        isOcrDerived = o.optBoolean("isOcrDerived", false),
                        confidence = o.optDouble("confidence", 1.0).toFloat()
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
