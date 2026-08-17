package com.localcharacter.app.data.charactercard

import android.util.Base64
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.LoreEntry
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.InflaterInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ImportedCharacter(val character: Character, val lore: List<LoreEntry>)

class CharacterCardException(message: String) : IllegalArgumentException(message)

class CharacterCardParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val maxMetadataBytes = 2 * 1024 * 1024

    fun parse(bytes: ByteArray, fileName: String = "card.json"): ImportedCharacter {
        if (bytes.size > 20 * 1024 * 1024) throw CharacterCardException("La tarjeta supera el límite permitido de 20 MB.")
        val jsonBytes = if (fileName.endsWith(".png", true) || bytes.startsWithPngSignature()) extractPngPayload(bytes) else bytes
        if (jsonBytes.size > maxMetadataBytes) throw CharacterCardException("La metadata de la tarjeta es demasiado grande.")
        return parseJson(jsonBytes.decodeToString())
    }

    fun parseJson(raw: String): ImportedCharacter {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw CharacterCardException("El JSON de la tarjeta está dañado o no es válido.") }
        val data = root["data"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: root
        val name = data.string("name").trim()
        if (name.isBlank()) throw CharacterCardException("La tarjeta no contiene un nombre de personaje.")
        val id = UUID.randomUUID().toString()
        val localCharacterExtension = data["extensions"]
            ?.let { runCatching { it.jsonObject["localcharacter"]?.jsonObject }.getOrNull() }
        val character = Character(
            id = id,
            name = name.take(120),
            description = data.string("description").take(20_000),
            personality = data.string("personality").take(20_000),
            scenario = data.string("scenario").take(20_000),
            firstMessage = data.string("first_mes", "firstMessage").take(20_000),
            exampleMessages = data.string("mes_example", "exampleMessages").take(40_000),
            systemPrompt = data.string("system_prompt", "systemPrompt").take(20_000),
            creatorNotes = data.string("creator_notes", "creatorNotes").take(20_000),
            tags = data.stringList("tags").take(64).map { it.take(80) },
            alternateGreetings = data.stringList("alternate_greetings", "alternateGreetings").take(32).map { it.take(20_000) },
            recommendedVoiceId = localCharacterExtension?.string("recommendedVoiceId")
                ?.trim()?.take(120)?.takeIf(String::isNotBlank),
        )
        return ImportedCharacter(character, parseLore(data, id))
    }

    fun exportJson(character: Character, lore: List<LoreEntry>): String = buildJsonObject {
        put("spec", "chara_card_v2")
        put("spec_version", "2.0")
        put("data", buildJsonObject {
            put("name", character.name)
            put("description", character.description)
            put("personality", character.personality)
            put("scenario", character.scenario)
            put("first_mes", character.firstMessage)
            put("mes_example", character.exampleMessages)
            put("creator_notes", character.creatorNotes)
            put("system_prompt", character.systemPrompt)
            put("tags", buildJsonArray { character.tags.forEach { add(JsonPrimitive(it)) } })
            put("alternate_greetings", buildJsonArray { character.alternateGreetings.forEach { add(JsonPrimitive(it)) } })
            character.recommendedVoiceId?.takeIf(String::isNotBlank)?.let { recommended ->
                put("extensions", buildJsonObject {
                    put("localcharacter", buildJsonObject { put("recommendedVoiceId", recommended) })
                })
            }
            put("character_book", buildJsonObject {
                put("entries", buildJsonArray {
                    lore.forEach { entry ->
                        add(buildJsonObject {
                            put("keys", buildJsonArray { entry.keywords.forEach { add(JsonPrimitive(it)) } })
                            put("content", entry.content)
                            put("enabled", entry.enabled)
                            put("case_sensitive", entry.caseSensitive)
                            put("priority", entry.priority)
                        })
                    }
                })
            })
        })
    }.toString()

    private fun parseLore(data: JsonObject, characterId: String): List<LoreEntry> {
        val book = data["character_book"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return emptyList()
        val entries = book["entries"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
        return entries.take(500).mapNotNull { element ->
            val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val content = item.string("content").take(20_000)
            if (content.isBlank()) return@mapNotNull null
            LoreEntry(
                id = UUID.randomUUID().toString(),
                characterId = characterId,
                keywords = item.stringList("keys", "keywords").take(64),
                content = content,
                priority = item["priority"]?.jsonPrimitive?.intOrNull ?: item["insertion_order"]?.jsonPrimitive?.intOrNull ?: 0,
                enabled = item["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                caseSensitive = item["case_sensitive"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }

    private fun extractPngPayload(bytes: ByteArray): ByteArray {
        if (!bytes.startsWithPngSignature()) throw CharacterCardException("El archivo PNG no tiene una firma válida.")
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            if (length < 0 || length > maxMetadataBytes || offset + 12L + length > bytes.size) {
                throw CharacterCardException("La estructura PNG de la tarjeta está dañada.")
            }
            val type = bytes.copyOfRange(offset + 4, offset + 8).decodeToString()
            val payload = bytes.copyOfRange(offset + 8, offset + 8 + length)
            val encoded = when (type) {
                "tEXt" -> parseTextChunk(payload)
                "zTXt" -> parseCompressedTextChunk(payload)
                "iTXt" -> parseInternationalTextChunk(payload)
                else -> null
            }
            if (encoded != null) {
                return runCatching { Base64.decode(encoded.trim(), Base64.DEFAULT) }
                    .getOrElse { throw CharacterCardException("La metadata 'chara' no contiene Base64 válido.") }
            }
            offset += 12 + length
            if (type == "IEND") break
        }
        throw CharacterCardException("Este PNG no contiene metadata compatible con Character Card.")
    }

    private fun parseTextChunk(payload: ByteArray): String? {
        val separator = payload.indexOf(0)
        if (separator <= 0) return null
        val key = payload.copyOfRange(0, separator).decodeToString()
        return if (key == "chara") payload.copyOfRange(separator + 1, payload.size).decodeToString() else null
    }

    private fun parseCompressedTextChunk(payload: ByteArray): String? {
        val separator = payload.indexOf(0)
        if (separator <= 0 || separator + 2 > payload.size) return null
        val key = payload.copyOfRange(0, separator).decodeToString()
        if (key != "chara" || payload[separator + 1].toInt() != 0) return null
        return InflaterInputStream(ByteArrayInputStream(payload, separator + 2, payload.size - separator - 2))
            .readBytes().also { if (it.size > maxMetadataBytes) throw CharacterCardException("Metadata PNG demasiado grande.") }.decodeToString()
    }

    private fun parseInternationalTextChunk(payload: ByteArray): String? {
        val keywordEnd = payload.indexOf(0)
        if (keywordEnd <= 0 || keywordEnd + 3 >= payload.size) return null
        if (payload.copyOfRange(0, keywordEnd).decodeToString() != "chara") return null
        val compressed = payload[keywordEnd + 1].toInt() == 1
        var cursor = keywordEnd + 3
        cursor = payload.indexOf(0, cursor).takeIf { it >= 0 }?.plus(1) ?: return null
        cursor = payload.indexOf(0, cursor).takeIf { it >= 0 }?.plus(1) ?: return null
        val textBytes = payload.copyOfRange(cursor, payload.size)
        return if (compressed) InflaterInputStream(ByteArrayInputStream(textBytes)).readBytes().decodeToString() else textBytes.decodeToString()
    }

    private fun ByteArray.startsWithPngSignature(): Boolean = size >= 8 && copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))
    private fun JsonObject.string(vararg names: String): String = names.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.contentOrNull }.orEmpty()
    private fun JsonObject.stringList(vararg names: String): List<String> = names.firstNotNullOfOrNull { name ->
        this[name]?.let { element ->
            when (element) {
                is JsonArray -> element.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                is JsonPrimitive -> element.contentOrNull?.split(',')?.map(String::trim)
                else -> null
            }
        }
    }.orEmpty().filter { it.isNotBlank() }
}

private fun ByteArray.indexOf(value: Byte, startIndex: Int = 0): Int {
    for (index in startIndex until size) if (this[index] == value) return index
    return -1
}
