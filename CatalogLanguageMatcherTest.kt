package com.localcharacter.app.domain.character

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLanguageMatcherTest {
    @Test fun `spanish aliases and regional codes match`() {
        assertTrue(CatalogLanguageMatcher.matches("es", "es-ES"))
        assertTrue(CatalogLanguageMatcher.matches("es", "Español"))
        assertFalse(CatalogLanguageMatcher.matches("es", "en"))
    }

    @Test fun `all languages accepts missing metadata but a selected language does not`() {
        assertTrue(CatalogLanguageMatcher.matches(null, null))
        assertFalse(CatalogLanguageMatcher.matches("es", null))
    }
}
