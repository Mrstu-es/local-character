package com.localcharacter.app.llm

import com.localcharacter.app.domain.model.GenerationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface LlmState {
    data object NoModelLoaded : LlmState
    data class LoadingModel(val name: String) : LlmState
    data class ModelReady(val name: String, val details: String) : LlmState
    data class Generating(val name: String) : LlmState
    data class InternalWork(val name: String, val purpose: GenerationPurpose) : LlmState
    data class Stopping(val name: String) : LlmState
    data class Error(val friendlyMessage: String, val technicalDetails: String?) : LlmState
}

enum class GenerationPurpose { CHAT_GENERATION, MEMORY, SUMMARY }

interface LlmEngine {
    val state: StateFlow<LlmState>
    suspend fun loadModel(path: String, displayName: String, settings: GenerationSettings): Result<String>
    suspend fun unloadModel()
    fun configureChatTemplate(mode: String, customTemplate: String?) = Unit
    fun generate(
        prompt: String,
        settings: GenerationSettings,
        purpose: GenerationPurpose = GenerationPurpose.CHAT_GENERATION,
    ): Flow<String>
    suspend fun stopGeneration()
    fun nativeVersion(): String
}
