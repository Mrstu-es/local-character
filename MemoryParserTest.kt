package com.localcharacter.app.domain.memory

import com.localcharacter.app.domain.model.MemoryType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryParserTest {
    @Test
    fun `cleans fences and ignores invalid candidates`() {
        val raw = """texto previo
            ```json
            {"memories":[
              {"type":"RELATIONSHIP","content":"María es la hermana del usuario.","importance":0.8,"confidence":0.95},
              {"type":"FACT","content":"hola","importance":0.9,"confidence":0.9},
              {"type":"UNKNOWN","content":"Esto no es válido para guardar.","importance":0.8,"confidence":0.8}
            ]}
            ``` texto posterior
        """.trimIndent()
        val result = MemoryParser().parse(raw)
        assertEquals(1, result.size)
        assertEquals(MemoryType.RELATIONSHIP, result.single().type)
    }

    @Test
    fun `parses absolute event date`() {
        val result = MemoryParser().parse(
            """{"memories":[{"type":"EVENT","content":"El usuario tiene un examen de programación.","importance":0.8,"confidence":0.9,"eventDate":"2026-08-15"}]}""",
        ).single()
        val expected = LocalDate.parse("2026-08-15").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, result.eventAt)
    }

    @Test fun `invalid json never crashes`() {
        assertTrue(MemoryParser().parse("```json {broken} ```").isEmpty())
        assertTrue(MemoryParser().parse("""{"memories":[{"type":{},"content":"Dato con forma inválida.","importance":{},"confidence":0.8}]}""").isEmpty())
    }
}
