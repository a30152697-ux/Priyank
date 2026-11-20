package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class MathBlock(val formula: String) : MdBlock
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data class BulletItem(val bullet: String, val text: String) : MdBlock
}

/**
 * Native Jetpack Compose renderer for Markdown headings, lists, bold/italic/inline code,
 * fenced code blocks (with copy), tables, and mathematical formulas ($...$ / $$...$$).
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val clipboard = LocalClipboardManager.current

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        Text(
                            text = formatInlineMarkdown(block.text),
                            style = style,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is MdBlock.Paragraph -> {
                        Text(
                            text = formatInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    is MdBlock.BulletItem -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = block.bullet,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = formatInlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MdBlock.CodeBlock -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.language.ifBlank { "code" }.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString(block.code)) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "Copy code",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = block.code,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                    is MdBlock.MathBlock -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = block.formula,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                    is MdBlock.TableBlock -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    block.headers.forEach { header ->
                                        Text(
                                            text = header,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.widthIn(min = 84.dp, max = 180.dp)
                                        )
                                    }
                                }
                                block.rows.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .padding(vertical = 7.dp, horizontal = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        row.forEach { cell ->
                                            Text(
                                                text = formatInlineMarkdown(cell),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.widthIn(min = 84.dp, max = 180.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatInlineMarkdown(text: String): AnnotatedString {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant
    val formulaColor = MaterialTheme.colorScheme.tertiary

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i++])
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = inlineCodeBg,
                                fontSize = 13.sp
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i++])
                    }
                }
                text[i] == '$' && (i == 0 || text[i - 1] != '\\') -> {
                    val end = text.indexOf('$', i + 1)
                    if (end > i + 1 && !text.substring(i + 1, end).contains('\n')) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = formulaColor
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i++])
                    }
                }
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i++])
                    }
                }
                else -> append(text[i++])
            }
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val lines = input.lines()
    val blocks = mutableListOf<MdBlock>()
    var idx = 0

    while (idx < lines.size) {
        val line = lines[idx]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            idx++
            continue
        }

        // Fenced code block
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            idx++
            val sb = StringBuilder()
            while (idx < lines.size && !lines[idx].trim().startsWith("```")) {
                sb.appendLine(lines[idx])
                idx++
            }
            if (idx < lines.size) idx++
            blocks += MdBlock.CodeBlock(lang, sb.toString().trimEnd())
            continue
        }

        // Display math block $$ ... $$
        if (trimmed.startsWith("$$")) {
            val sb = StringBuilder()
            val singleLine = trimmed.removePrefix("$$")
            if (singleLine.endsWith("$$") && singleLine.length > 2) {
                blocks += MdBlock.MathBlock(singleLine.removeSuffix("$$").trim())
                idx++
            } else {
                sb.appendLine(singleLine)
                idx++
                while (idx < lines.size && !lines[idx].trim().endsWith("$$")) {
                    sb.appendLine(lines[idx])
                    idx++
                }
                if (idx < lines.size) {
                    sb.append(lines[idx].trim().removeSuffix("$$"))
                    idx++
                }
                blocks += MdBlock.MathBlock(sb.toString().trim())
            }
            continue
        }

        // Markdown table
        if (trimmed.startsWith("|") && idx + 1 < lines.size && lines[idx + 1].trim().matches(Regex("""^\|?[\s:\-|]+\|?$"""))) {
            val headers = splitTableRow(trimmed)
            idx += 2
            val rows = mutableListOf<List<String>>()
            while (idx < lines.size && lines[idx].trim().startsWith("|")) {
                rows += splitTableRow(lines[idx].trim())
                idx++
            }
            blocks += MdBlock.TableBlock(headers, rows)
            continue
        }

        // Headings
        when {
            trimmed.startsWith("### ") -> {
                blocks += MdBlock.Heading(3, trimmed.removePrefix("### ").trim())
                idx++
            }
            trimmed.startsWith("## ") -> {
                blocks += MdBlock.Heading(2, trimmed.removePrefix("## ").trim())
                idx++
            }
            trimmed.startsWith("# ") -> {
                blocks += MdBlock.Heading(1, trimmed.removePrefix("# ").trim())
                idx++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks += MdBlock.BulletItem("•", trimmed.drop(2).trim())
                idx++
            }
            trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                val dotIdx = trimmed.indexOf('.')
                val num = trimmed.substring(0, dotIdx + 1)
                val body = trimmed.substring(dotIdx + 1).trim()
                blocks += MdBlock.BulletItem(num, body)
                idx++
            }
            else -> {
                val para = StringBuilder(trimmed)
                idx++
                while (idx < lines.size) {
                    val next = lines[idx].trim()
                    if (next.isEmpty() || next.startsWith("```") || next.startsWith("#") ||
                        next.startsWith("- ") || next.startsWith("* ") || next.startsWith("|") ||
                        next.startsWith("$$") || next.matches(Regex("""^\d+\.\s+.*"""))
                    ) {
                        break
                    }
                    para.append(" ").append(next)
                    idx++
                }
                blocks += MdBlock.Paragraph(para.toString())
            }
        }
    }
    return blocks
}

private fun splitTableRow(row: String): List<String> =
    row.trim().trim('|').split('|').map { it.trim() }
