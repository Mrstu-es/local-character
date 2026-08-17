package com.localcharacter.app.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localcharacter.app.AppContainer
import com.localcharacter.app.domain.memory.MemoryTextNormalizer
import com.localcharacter.app.domain.model.CharacterRelationship
import com.localcharacter.app.domain.model.Memory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MemoryUiState(
    val characterName: String = "Memoria",
    val memories: List<Memory> = emptyList(),
    val relationship: CharacterRelationship? = null,
    val loading: Boolean = true,
)

class MemoryViewModel(
    private val conversationId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MemoryUiState())
    val state: StateFlow<MemoryUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val conversation = container.chats.getConversation(conversationId) ?: return@launch
        val character = container.characters.getCharacter(conversation.characterId) ?: return@launch
        val settings = container.settings.memorySettings.first()
        mutableState.value = MemoryUiState(
            characterName = character.name,
            memories = container.memories.candidates(
                character.id, conversation.id, conversation.userPersonaId, settings.shareAcrossChats,
            ),
            relationship = container.relationships.get(character.id, conversation.id, conversation.userPersonaId),
            loading = false,
        )
    }

    fun edit(memory: Memory, content: String) = viewModelScope.launch {
        val clean = content.trim()
        if (clean.isNotBlank()) container.memories.edit(memory.id, clean, MemoryTextNormalizer.normalize(clean))
        refresh()
    }

    fun delete(memory: Memory) = viewModelScope.launch {
        container.memories.delete(memory.id)
        refresh()
    }

    fun togglePinned(memory: Memory) = viewModelScope.launch {
        container.memories.setPinned(memory.id, !memory.isPinned)
        refresh()
    }

    class Factory(private val conversationId: String, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MemoryViewModel(conversationId, container) as T
    }
}
