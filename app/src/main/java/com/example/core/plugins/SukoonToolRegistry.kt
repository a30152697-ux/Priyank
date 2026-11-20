package com.example.core.plugins

/**
 * Modular plugin & custom tool architecture for future extensibility.
 * New tools/modules can be registered cleanly without rewriting Chat, RAG, or UI layers.
 */
interface SukoonPlugin {
    val id: String
    val name: String
    val description: String
    val category: String
    val isBuiltIn: Boolean
    suspend fun execute(input: String, context: Map<String, String> = emptyMap()): PluginResult
}

data class PluginResult(
    val success: Boolean,
    val output: String,
    val metadata: Map<String, String> = emptyMap()
)

class SukoonToolRegistry {
    private val plugins = linkedMapOf<String, SukoonPlugin>()

    fun register(plugin: SukoonPlugin) {
        plugins[plugin.id] = plugin
    }

    fun getAll(): List<SukoonPlugin> = plugins.values.toList()

    fun getById(id: String): SukoonPlugin? = plugins[id]
}

class FormulaScratchpadPlugin : SukoonPlugin {
    override val id = "tool_formula_eval"
    override val name = "Study Calculator & Unit Helper"
    override val description = "Offline quick arithmetic & unit conversion helper for numerical problem solving."
    override val category = "Math & STEM"
    override val isBuiltIn = true

    override suspend fun execute(input: String, context: Map<String, String>): PluginResult {
        val expr = input.trim()
        val result = runCatching { evaluateSimpleExpression(expr) }
        return result.fold(
            onSuccess = { PluginResult(true, "$expr = $it") },
            onFailure = { PluginResult(false, "Unsupported expression: ${it.message}") }
        )
    }

    private fun evaluateSimpleExpression(expr: String): Double {
        val clean = expr.replace(" ", "")
        if (clean.isEmpty()) error("Empty expression")
        var pos = 0

        fun peek(): Char? = clean.getOrNull(pos)
        fun consume(): Char = clean[pos++]

        lateinit var parseExpr: () -> Double

        fun parseFactor(): Double {
            if (peek() == '+') { consume(); return parseFactor() }
            if (peek() == '-') { consume(); return -parseFactor() }
            if (peek() == '(') {
                consume()
                val v = parseExpr()
                if (peek() != ')') error("Missing ')'")
                consume()
                return v
            }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            if (start == pos) error("Unexpected char '${peek()}'")
            return clean.substring(start, pos).toDouble()
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (peek() == '*' || peek() == '/') {
                val op = consume()
                val rhs = parseFactor()
                x = if (op == '*') x * rhs else x / rhs
            }
            return x
        }

        parseExpr = {
            var x = parseTerm()
            while (peek() == '+' || peek() == '-') {
                val op = consume()
                val rhs = parseTerm()
                x = if (op == '+') x + rhs else x - rhs
            }
            x
        }

        val x = parseExpr()
        if (pos != clean.length) error("Unexpected trailing '${clean.substring(pos)}'")
        return x
    }
}
