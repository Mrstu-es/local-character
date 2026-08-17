package com.localcharacter.app.data.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufMetadataParserTest {
    @Test
    fun `detects future architecture metadata and tensor parameters without filename rules`() {
        val bytes = GgufFixture()
            .metadata("general.name", "Modelo futuro")
            .metadata("general.architecture", "future_arch")
            .metadata("future_arch.context_length", 65_536L)
            .metadata("general.file_type", 15L)
            .metadata("tokenizer.ggml.model", "future-tokenizer")
            .metadata("tokenizer.chat_template", "template {{prompt}}")
            .tensor("weight", 2, 3)
            .build()

        val result = GgufMetadataParser().parse(ByteArrayInputStream(bytes))

        assertEquals("future_arch", result.architecture)
        assertEquals(65_536, result.contextLength)
        assertEquals("Q4_K_M", result.quantization)
        assertEquals("future-tokenizer", result.tokenizer)
        assertEquals("template {{prompt}}", result.chatTemplate)
        assertEquals(1L, result.tensorCount)
        assertEquals(6L, result.parameterCount)
    }

    @Test
    fun `rejects a non GGUF document regardless of its extension`() {
        val error = runCatching { GgufMetadataParser().parse(ByteArrayInputStream("fake".encodeToByteArray())) }.exceptionOrNull()
        assertTrue(error is GgufFormatException)
    }
}

private class GgufFixture {
    private val metadata = mutableListOf<Pair<String, Any>>()
    private val tensors = mutableListOf<Pair<String, List<Long>>>()
    fun metadata(key: String, value: Any) = apply { metadata += key to value }
    fun tensor(name: String, vararg dimensions: Long) = apply { tensors += name to dimensions.toList() }

    fun build(): ByteArray = ByteArrayOutputStream().apply {
        write("GGUF".encodeToByteArray())
        u32(3)
        u64(tensors.size.toLong())
        u64(metadata.size.toLong())
        metadata.forEach { (key, value) ->
            string(key)
            when (value) {
                is String -> { u32(8); string(value) }
                is Long -> { u32(4); u32(value) }
                else -> error("fixture type")
            }
        }
        tensors.forEach { (name, dimensions) ->
            string(name)
            u32(dimensions.size.toLong())
            dimensions.forEach { u64(it) }
            u32(0)
            u64(0)
        }
    }.toByteArray()

    private fun ByteArrayOutputStream.u32(value: Long) = write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array())
    private fun ByteArrayOutputStream.u64(value: Long) = write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())
    private fun ByteArrayOutputStream.string(value: String) { val bytes = value.encodeToByteArray(); u64(bytes.size.toLong()); write(bytes) }
}
