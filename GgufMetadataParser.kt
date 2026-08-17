package com.localcharacter.app.data.model

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
    val name: String?,
    val architecture: String?,
    val quantization: String?,
    val contextLength: Int?,
    val tensorCount: Long,
    val parameterCount: Long?,
    val tokenizer: String?,
    val chatTemplate: String?,
    val version: Int,
)

class GgufFormatException(message: String) : IllegalArgumentException(message)

class GgufMetadataParser {
    fun parse(source: InputStream): GgufMetadata {
        val input = LittleEndianInput(BufferedInputStream(source))
        val magic = input.bytes(4).decodeToString()
        if (magic != "GGUF") throw GgufFormatException("Este archivo no parece ser un modelo GGUF válido.")
        val version = input.u32().toInt()
        if (version !in 2..3) throw GgufFormatException("Versión GGUF no compatible: $version")
        val tensorCount = input.u64()
        if (tensorCount !in 0..1_000_000) throw GgufFormatException("Cantidad de tensores GGUF inválida.")
        val metadataCount = input.u64()
        if (metadataCount !in 0..100_000) throw GgufFormatException("Cantidad de metadata GGUF inválida.")
        val values = mutableMapOf<String, Any?>()
        repeat(metadataCount.toInt()) {
            val key = input.string(16_384)
            val type = input.u32().toInt()
            val value = input.value(type, 0)
            if (key in wantedKeys || key.endsWith(".context_length")) values[key] = value
        }
        val architecture = values["general.architecture"] as? String
        val contextKey = architecture?.let { "$it.context_length" }
        val fileType = (values["general.file_type"] as? Number)?.toInt()
        var parameterCount = 0L
        var parameterCountKnown = true
        repeat(tensorCount.toInt()) {
            input.string(16_384)
            val dimensions = input.u32().toInt()
            if (dimensions !in 1..8) throw GgufFormatException("Dimensiones de tensor GGUF inválidas.")
            var tensorParameters = 1L
            repeat(dimensions) {
                val dimension = input.u64()
                if (dimension <= 0 || tensorParameters > Long.MAX_VALUE / dimension) parameterCountKnown = false
                else tensorParameters *= dimension
            }
            input.u32() // tensor type
            input.u64() // aligned data offset
            if (parameterCountKnown && parameterCount <= Long.MAX_VALUE - tensorParameters) parameterCount += tensorParameters
            else parameterCountKnown = false
        }
        return GgufMetadata(
            name = values["general.name"] as? String,
            architecture = architecture,
            quantization = fileType?.let(::quantizationName),
            contextLength = ((values[contextKey] ?: values.entries.firstOrNull { it.key.endsWith(".context_length") }?.value) as? Number)?.toInt(),
            tensorCount = tensorCount,
            parameterCount = parameterCount.takeIf { parameterCountKnown },
            tokenizer = values["tokenizer.ggml.model"] as? String,
            chatTemplate = (values["tokenizer.chat_template"] as? String)?.take(32_000),
            version = version,
        )
    }

    private fun quantizationName(type: Int): String = mapOf(
        0 to "F32", 1 to "F16", 2 to "Q4_0", 3 to "Q4_1", 7 to "Q8_0",
        8 to "Q5_0", 9 to "Q5_1", 10 to "Q2_K", 11 to "Q3_K_S", 12 to "Q3_K_M",
        13 to "Q3_K_L", 14 to "Q4_K_S", 15 to "Q4_K_M", 16 to "Q5_K_S",
        17 to "Q5_K_M", 18 to "Q6_K", 19 to "IQ2_XXS", 20 to "IQ2_XS",
    )[type] ?: "Tipo $type"

    private class LittleEndianInput(private val input: InputStream) {
        fun bytes(count: Int): ByteArray {
            val result = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = input.read(result, offset, count - offset)
                if (read < 0) throw EOFException("GGUF truncado")
                offset += read
            }
            return result
        }
        fun u8(): Int = bytes(1)[0].toInt() and 0xff
        fun u16(): Int = ByteBuffer.wrap(bytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
        fun u32(): Long = ByteBuffer.wrap(bytes(4)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        fun u64(): Long = ByteBuffer.wrap(bytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
        fun f32(): Float = ByteBuffer.wrap(bytes(4)).order(ByteOrder.LITTLE_ENDIAN).float
        fun f64(): Double = ByteBuffer.wrap(bytes(8)).order(ByteOrder.LITTLE_ENDIAN).double
        fun string(max: Int): String {
            val length = u64()
            if (length !in 0..max.toLong()) throw GgufFormatException("Cadena GGUF fuera del límite seguro.")
            return bytes(length.toInt()).decodeToString()
        }
        fun value(type: Int, depth: Int): Any? {
            if (depth > 3) throw GgufFormatException("Metadata GGUF demasiado anidada.")
            return when (type) {
                0 -> u8(); 1 -> u8().toByte(); 2 -> u16(); 3 -> u16().toShort()
                4 -> u32(); 5 -> u32().toInt(); 6 -> f32(); 7 -> u8() != 0
                8 -> string(2 * 1024 * 1024)
                9 -> {
                    val childType = u32().toInt()
                    val count = u64()
                    if (count !in 0..1_000_000) throw GgufFormatException("Array GGUF fuera del límite seguro.")
                    var first: Any? = null
                    repeat(count.toInt()) { index -> value(childType, depth + 1).also { if (index == 0) first = it } }
                    first
                }
                10 -> u64(); 11 -> u64(); 12 -> f64()
                else -> throw GgufFormatException("Tipo de metadata GGUF desconocido: $type")
            }
        }
    }

    private companion object {
        val wantedKeys = setOf(
            "general.name", "general.architecture", "general.file_type",
            "tokenizer.ggml.model", "tokenizer.chat_template",
        )
    }
}
