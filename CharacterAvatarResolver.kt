package com.localcharacter.app.ui.components

data class ResolvedCharacterAvatar(val imageData: String?, val fallbackInitial: String)

object CharacterAvatarResolver {
    fun resolve(name: String, avatarUri: String?): ResolvedCharacterAvatar = ResolvedCharacterAvatar(
        imageData = avatarUri?.trim()?.takeIf(String::isNotBlank),
        fallbackInitial = name.trim().take(1).uppercase().ifBlank { "?" },
    )
}
