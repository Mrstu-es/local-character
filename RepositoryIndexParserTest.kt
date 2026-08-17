package com.localcharacter.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositoryIndexParserTest {
    @Test fun `valid repository reports its characters`() {
        val index = RepositoryIndexParser.parse(
            """{"schemaVersion":1,"characters":[{"id":"a","name":"A","cardUrl":"https://example.org/a.png","sourceUrl":"https://example.org/a"}]}""",
        )
        assertEquals(1, index.characters.size)
    }

    @Test fun `unsupported or malformed repository is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryIndexParser.parse("""{"schemaVersion":2,"characters":[]}""")
        }
        assertThrows(Exception::class.java) { RepositoryIndexParser.parse("not-json") }
    }
}
