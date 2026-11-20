package com.example.data.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import com.example.core.security.InputValidator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class ExtractedPageResult(
    val pageNumber: Int,
    val text: String,
    val isOcrDerived: Boolean,
    val confidence: Float
)

data class DocumentImportResult(
    val title: String,
    val fileType: String,
    val localFilePath: String,
    val pageCount: Int,
    val pages: List<ExtractedPageResult>,
    val overallStatus: String,
    val averageConfidence: Float
)

data class ImageOcrResult(
    val extractedText: String,
    val isLowConfidence: Boolean,
    val confidenceNote: String,
    val compressedJpegBase64: String?
)

/**
 * Handles normal PDF text extraction (PDFBox), scanned PDF rendering (PdfRenderer),
 * bilingual English (Latin) + Hindi (Devanagari) on-device OCR (ML Kit),
 * image text recognition, and safe app-private file import.
 */
class PdfAndOcrProcessor(private val context: Context) {

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val devanagariRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    suspend fun importDocument(
        uri: Uri,
        copyIntoPrivateStorage: Boolean,
        runOcrIfScanned: Boolean = true
    ): Result<DocumentImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val rawName = queryDisplayName(resolver, uri) ?: "Study_Document"
            val mimeType = resolver.getType(uri)
            if (!InputValidator.isAllowedDocumentMimeOrExtension(rawName, mimeType)) {
                error("Unsupported file format. Please select a PDF, TXT, or Markdown file.")
            }

            val sanitizedName = InputValidator.sanitizeFileName(rawName)
            val isPdf = sanitizedName.lowercase().endsWith(".pdf") || mimeType == "application/pdf"

            val docsDir = File(context.filesDir, "library_docs").apply { mkdirs() }
            val tempOrStoredFile = if (copyIntoPrivateStorage) {
                File(docsDir, "${System.currentTimeMillis()}_$sanitizedName")
            } else {
                File(context.cacheDir, "temp_index_${System.currentTimeMillis()}_$sanitizedName")
            }

            try {
                copyUriWithLimit(resolver, uri, tempOrStoredFile)
                val storedPath = if (copyIntoPrivateStorage) tempOrStoredFile.absolutePath else ""

                if (!isPdf) {
                    val rawText = tempOrStoredFile.readText(Charsets.UTF_8)
                    val pages = chunkPlainTextAsPages(rawText)
                    return@runCatching DocumentImportResult(
                        title = sanitizedName.substringBeforeLast('.').ifBlank { sanitizedName },
                        fileType = "TXT",
                        localFilePath = storedPath,
                        pageCount = pages.size,
                        pages = pages,
                        overallStatus = "EXTRACTED",
                        averageConfidence = 1.0f
                    )
                }

                // PDF processing: page-by-page normal text extraction + OCR fallback for scanned pages
                val extractedPages = mutableListOf<ExtractedPageResult>()
                PDDocument.load(tempOrStoredFile).use { pdDoc ->
                    val totalPages = pdDoc.numberOfPages.coerceAtLeast(1)
                    val stripper = PDFTextStripper()

                    var pfd: ParcelFileDescriptor? = null
                    var renderer: PdfRenderer? = null
                    try {
                        for (pageIdx in 1..totalPages) {
                            stripper.startPage = pageIdx
                            stripper.endPage = pageIdx
                            val embeddedText = stripper.getText(pdDoc).trim()

                            if (embeddedText.length >= 25 || !runOcrIfScanned) {
                                extractedPages += ExtractedPageResult(
                                    pageNumber = pageIdx,
                                    text = embeddedText,
                                    isOcrDerived = false,
                                    confidence = if (embeddedText.isNotBlank()) 1.0f else 0.35f
                                )
                            } else {
                                // Render scanned PDF page via Android PdfRenderer & run bilingual OCR
                                if (renderer == null) {
                                    pfd = ParcelFileDescriptor.open(
                                        tempOrStoredFile,
                                        ParcelFileDescriptor.MODE_READ_ONLY
                                    )
                                    renderer = PdfRenderer(pfd)
                                }
                                val ocrPage = renderAndOcrPdfPage(renderer, pageIdx - 1, pageIdx)
                                extractedPages += ocrPage
                            }
                        }
                    } finally {
                        renderer?.close()
                        pfd?.close()
                    }
                }

                val avgConf = if (extractedPages.isEmpty()) 0f
                else extractedPages.map { it.confidence }.average().toFloat()

                val anyOcr = extractedPages.any { it.isOcrDerived }
                val status = when {
                    !anyOcr -> "EXTRACTED"
                    avgConf < 0.62f -> "OCR_LOW_CONFIDENCE"
                    else -> "OCR_OK"
                }

                DocumentImportResult(
                    title = sanitizedName.substringBeforeLast('.').ifBlank { sanitizedName },
                    fileType = "PDF",
                    localFilePath = storedPath,
                    pageCount = extractedPages.size.coerceAtLeast(1),
                    pages = extractedPages,
                    overallStatus = status,
                    averageConfidence = avgConf
                )
            } finally {
                if (!copyIntoPrivateStorage && tempOrStoredFile.exists()) {
                    tempOrStoredFile.delete()
                }
            }
        }
    }

    suspend fun runOcrOnPdfPage(
        localFilePath: String,
        pageNumber: Int
    ): Result<ExtractedPageResult> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(localFilePath)
            if (!file.exists()) error("Local PDF file not found on device.")
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val idx = (pageNumber - 1).coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                    renderAndOcrPdfPage(renderer, idx, pageNumber)
                }
            }
        }
    }

    suspend fun renderPdfPageBitmap(
        localFilePath: String,
        pageNumber: Int,
        maxWidth: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(localFilePath)
            if (!file.exists()) return@runCatching null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching null
                    val idx = (pageNumber - 1).coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(idx).use { page ->
                        val scale = (maxWidth.toFloat() / page.width.coerceAtLeast(1)).coerceIn(1f, 2.5f)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun processImageForOcrAndVision(
        uri: Uri,
        includeCompressedJpegForVision: Boolean = true
    ): Result<ImageOcrResult> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaledBitmap(uri, maxDimension = 1600)
                ?: error("Could not decode the selected image.")
            val (text, confidence) = recognizeBilingualText(bitmap)
            val lowConf = confidence < 0.62f || text.length < 10
            val note = if (lowConf) {
                "OCR confidence is low or handwriting/diagrams/formulas were detected. Review extracted text or ask Vision AI (requires Owner permission)."
            } else {
                "On-device English + Hindi OCR completed."
            }
            val b64 = if (includeCompressedJpegForVision) encodeBitmapToJpegBase64(bitmap) else null
            bitmap.recycle()
            ImageOcrResult(
                extractedText = text,
                isLowConfidence = lowConf,
                confidenceNote = note,
                compressedJpegBase64 = b64
            )
        }
    }

    private suspend fun renderAndOcrPdfPage(
        renderer: PdfRenderer,
        pageIndex: Int,
        pageNumber: Int
    ): ExtractedPageResult {
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            return ExtractedPageResult(pageNumber, "", true, 0f)
        }
        val bitmap = renderer.openPage(pageIndex).use { page ->
            val scale = 2.0f
            val w = (page.width * scale).toInt().coerceIn(400, 2200)
            val h = (page.height * scale).toInt().coerceIn(400, 3000)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
        return try {
            val (text, confidence) = recognizeBilingualText(bitmap)
            ExtractedPageResult(
                pageNumber = pageNumber,
                text = text,
                isOcrDerived = true,
                confidence = confidence
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognizeBilingualText(bitmap: Bitmap): Pair<String, Float> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val latinResult = runCatching { latinRecognizer.process(inputImage).await() }.getOrNull()
        val devanagariResult = runCatching { devanagariRecognizer.process(inputImage).await() }.getOrNull()

        val latinText = latinResult?.text?.trim().orEmpty()
        val hindiText = devanagariResult?.text?.trim().orEmpty()

        val hasDevanagariChars = hindiText.any { it in '\u0900'..'\u097F' }
        val combined = when {
            hasDevanagariChars && latinText.isNotBlank() && hindiText != latinText ->
                "$hindiText\n\n[Latin Script Pass]\n$latinText"
            hasDevanagariChars -> hindiText
            else -> latinText
        }

        val confidence = estimateOcrConfidence(combined)
        return combined to confidence
    }

    private fun estimateOcrConfidence(text: String): Float {
        if (text.isBlank()) return 0.15f
        val validChars = text.count { it.isLetterOrDigit() || it.isWhitespace() || it in '\u0900'..'\u097F' }
        val ratio = validChars.toFloat() / text.length.coerceAtLeast(1)
        val lengthBonus = (text.length / 200f).coerceIn(0f, 0.25f)
        return (ratio * 0.72f + lengthBonus).coerceIn(0.15f, 0.96f)
    }

    private fun decodeScaledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    fun encodeBitmapToJpegBase64(bitmap: Bitmap, quality: Int = 82): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun chunkPlainTextAsPages(text: String): List<ExtractedPageResult> {
        val clean = text.trim()
        if (clean.isEmpty()) return listOf(ExtractedPageResult(1, "", false, 0.5f))
        val chunkSize = 2200
        val chunks = mutableListOf<ExtractedPageResult>()
        var idx = 0
        var page = 1
        while (idx < clean.length) {
            val end = (idx + chunkSize).coerceAtMost(clean.length)
            chunks += ExtractedPageResult(
                pageNumber = page++,
                text = clean.substring(idx, end).trim(),
                isOcrDerived = false,
                confidence = 1.0f
            )
            idx = end
        }
        return chunks
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    private fun copyUriWithLimit(resolver: ContentResolver, uri: Uri, dest: File) {
        var total = 0L
        val buffer = ByteArray(16 * 1024)
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > InputValidator.MAX_IMPORT_BYTES) {
                        dest.delete()
                        error("File exceeds 80 MB safety limit.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("Unable to open selected document.")
    }
}
