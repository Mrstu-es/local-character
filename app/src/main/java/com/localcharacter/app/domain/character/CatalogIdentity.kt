package com.localcharacter.app.domain.character

import java.nio.charset.StandardCharsets
import java.util.UUID

object CatalogIdentity {
    fun localCharacterId(providerId: String, remoteId: String): String {
        require(providerId.isNotBlank() && remoteId.isNotBlank())
        return UUID.nameUUIDFromBytes("catalog:${providerId.trim()}:${remoteId.trim()}".toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
