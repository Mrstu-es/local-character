package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.MemoryOrigin
import com.localcharacter.app.domain.model.MemoryType
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class MemoryCandidate(
    val type: MemoryType,
    val content: String,
    val importance: Float,
    val confidence: Float,
    val origin: MemoryOrigin,
    val eventAt: Long? = null,
    val expiresAt: Long? = null,
    val isPinned: Boolean = false,
)

object MemoryTextNormalizer {
    private val punctuation = Regex("[^a-z0-9áéíóúüñ\\s]")
    private val spaces = Regex("\\s+")
    private val stopWords = setOf(
        "a", "al", "de", "del", "el", "ella", "en", "es", "la", "las", "le", "los", "me",
        "mi", "mis", "que", "se", "su", "sus", "un", "una", "usuario", "y", "ya",
    )

    fun normalize(text: String): String = text.lowercase().trim()
        .replace(punctuation, " ")
        .replace(spaces, " ")
        .trim()

    fun tokens(text: String): Set<String> = normalize(text).split(' ')
        .asSequence()
        .map(::canonicalToken)
        .filter { it.length > 1 && it !in stopWords }
        .toSet()

    fun ascii(text: String): String = Normalizer.normalize(normalize(text), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    private fun canonicalToken(token: String): String = when {
        token.startsWith("llam") -> "nombre"
        token in setOf("perrito", "perra", "perro") -> "perro"
        token in setOf("gatito", "gata", "gato") -> "gato"
        token.startsWith("viv") -> "residencia"
        token.startsWith("mud") -> "residencia"
        token.startsWith("gust") -> "preferencia"
        else -> token
    }
}

class MemoryParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = false },
) {
    private val trivial = setOf("hola", "ok", "okay", "jajaja", "gracias", "que haces", "qué haces")

    fun parse(raw: String, defaultOrigin: MemoryOrigin = MemoryOrigin.USER_STATED_FACT): List<MemoryCandidate> {
        val root = extractJsonObjects(raw).firstNotNullOfOrNull { candidate ->
            runCatching { json.parseToJsonElement(candidate).jsonObject }
                .getOrNull()?.takeIf { it["memories"] is JsonArray }
        }
            ?: return emptyList()
        val items = root["memories"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { runCatching { parseCandidate(it, defaultOrigin) }.getOrNull() }
            .distinctBy { it.type to MemoryTextNormalizer.normalize(it.content) }
    }

    private fun parseCandidate(element: JsonElement, defaultOrigin: MemoryOrigin): MemoryCandidate? {
        val item = element as? JsonObject ?: return null
        val type = item.string("type")?.uppercase()?.let { runCatching { MemoryType.valueOf(it) }.getOrNull() } ?: return null
        val content = item.string("content")?.trim()?.takeIf { it.length in 4..400 } ?: return null
        val normalized = MemoryTextNormalizer.normalize(content)
        if (normalized in trivial || normalized.split(' ').size < 3) return null
        val importance = item.float("importance")?.takeIf { it in 0f..1f } ?: return null
        val confidence = item.float("confidence")?.takeIf { it in 0f..1f } ?: return null
        val origin = item.string("origin")?.uppercase()?.let { runCatching { MemoryOrigin.valueOf(it) }.getOrNull() }
            ?: defaultOrigin
        return MemoryCandidate(
            type = type,
            content = content,
            importance = importance,
            confidence = confidence,
            origin = origin,
            eventAt = item.timestamp("eventAt") ?: item.timestamp("eventDate"),
            expiresAt = item.timestamp("expiresAt"),
            isPinned = item["isPinned"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private fun extractJsonObjects(raw: String): List<String> {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val objects = mutableListOf<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        cleaned.forEachIndexed { index, char ->
            if (start < 0) {
                if (char == '{') { start = index; depth = 1 }
                return@forEachIndexed
            }
            if (escaped) { escaped = false; return@forEachIndexed }
            if (char == '\\' && inString) { escaped = true; return@forEachIndexed }
            if (char == '"') { inString = !inString; return@forEachIndexed }
            if (!inString) {
                if (char == '{') depth++
                if (char == '}') depth--
                if (depth == 0) {
                    objects += cleaned.substring(start, index + 1)
                    start = -1
                }
            }
        }
        return objects
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.float(key: String): Float? = this[key]?.jsonPrimitive?.floatOrNull
    private fun JsonObject.timestamp(key: String): Long? {
        val value = this[key]?.jsonPrimitive ?: return null
        value.longOrNull?.let { return if (it in 1..9_999_999_999L) it * 1_000 else it }
        val text = value.contentOrNull ?: return null
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    }
}

class ExplicitMemoryIntentDetector {
    private val pattern = Regex("(?i)\\b(?:recuerda|recordá|recuerda bien|no olvides)\\s+que\\s+(.{3,400})")
    fun detect(message: String): String? = pattern.find(message)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', '!', '?')
}

class MemoryForgetIntentDetector {
    private val patterns = listOf(
        Regex("(?i)\\b(?:olvida|olvidá|borra de tu memoria)\\s+(?:que\\s+)?(.{3,400})"),
        Regex("(?i)\\bno recuerdes\\s+(?:que\\s+)?(.{3,400})"),
    )

    fun detect(message: String): String? = patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(message)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', '!', '?')
    }
}
