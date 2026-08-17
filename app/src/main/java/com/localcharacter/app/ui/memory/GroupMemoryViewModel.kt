package com.localcharacter.app.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localcharacter.app.AppContainer
import com.localcharacter.app.domain.model.GroupMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GroupMemoryUiState(
    val groupName: String = "Grupo",
    val memories: List<GroupMemory> = emptyList(),
    val loading: Boolean = true,
)

class GroupMemoryViewModel(
    private val groupId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GroupMemoryUiState())
    val state: StateFlow<GroupMemoryUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val group = container.groups.get(groupId) ?: return@launch
        mutableState.value = GroupMemoryUiState(
            groupName = group.name,
            memories = container.groups.activeMemories(groupId),
            loading = false,
        )
    }

    fun delete(memory: GroupMemory) = viewModelScope.launch {
        container.groups.deleteMemory(memory.id)
        refresh()
    }

    class Factory(private val groupId: String, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GroupMemoryViewModel(groupId, container) as T
    }
}
