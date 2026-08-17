package com.localcharacter.app.domain.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatalogIdentityTest {
    @Test fun `same provider and remote id always produces same local id`() {
        assertEquals(
            CatalogIdentity.localCharacterId("aicc", "42"),
            CatalogIdentity.localCharacterId("aicc", "42"),
        )
    }

    @Test fun `provider is part of remote identity`() {
        assertNotEquals(
            CatalogIdentity.localCharacterId("aicc", "42"),
            CatalogIdentity.localCharacterId("other", "42"),
        )
    }
}
