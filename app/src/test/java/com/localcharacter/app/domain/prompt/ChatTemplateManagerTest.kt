package com.localcharacter.app.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTemplateManagerTest {
    private val manager = ChatTemplateManager()

    @Test fun `auto leaves prompt for embedded GGUF template`() =
        assertEquals("hola", manager.apply("hola", ChatTemplate.AUTO))

    @Test fun `qwen uses chatml framing`() =
        assertTrue(manager.apply("hola", ChatTemplate.QWEN).contains("<|im_start|>assistant"))

    @Test fun `custom replaces prompt marker`() =
        assertEquals("pre hola post", manager.apply("hola", ChatTemplate.CUSTOM, "pre {{prompt}} post"))
}
