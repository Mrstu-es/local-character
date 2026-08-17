package com.localcharacter.app.ui.chat

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localcharacter.app.AppContainer
import com.localcharacter.app.AppBuildInfo
import com.localcharacter.app.data.model.ModelLoadPolicy
import com.localcharacter.app.data.settings.MemoryLevel
import com.localcharacter.app.data.settings.MemorySettings
import com.localcharacter.app.domain.memory.MemoryRetriever
import com.localcharacter.app.domain.conversation.ActionModeFormatter
import com.localcharacter.app.domain.conversation.ComposerMode
import com.localcharacter.app.domain.conversation.ContentPolicyResolver
import com.localcharacter.app.domain.conversation.GenerationMode
import com.localcharacter.app.domain.conversation.TtsTextSanitizer
import com.localcharacter.app.domain.model.VoiceAutoplayOverride
import com.localcharacter.app.tts.TtsManager
import com.localcharacter.app.tts.TtsSynthesisSettings
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.Conversation
import com.localcharacter.app.domain.model.MessageRole
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MemoryOrigin
import com.localcharacter.app.domain.model.MemoryType
import com.localcharacter.app.domain.prompt.PromptBuilder
import com.localcharacter.app.domain.prompt.PromptRequest
import com.localcharacter.app.llm.LlmState
import com.localcharacter.app.llm.LlmTaskPriority
import com.localcharacter.app.llm.provider.FallbackPolicy
import com.localcharacter.app.llm.provider.LlmModelInfo
import com.localcharacter.app.llm.provider.LlmRequest
import com.localcharacter.app.llm.provider.LlmStreamEvent
import com.localcharacter.app.llm.provider.ProviderError
import com.localcharacter.app.llm.provider.ProviderErrorKind
import com.localcharacter.app.llm.provider.ProviderModelSelection
import com.localcharacter.app.llm.provider.TokenUsage
import com.localcharacter.app.llm.provider.LOCAL_PROVIDER_ID
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ChatHeaderState(val character: Character? = null, val conversation: Conversation? = null)

data class ChatBrainState(
    val selection: ProviderModelSelection = ProviderModelSelection(),
    val label: String = "Local GGUF",
    val isLocal: Boolean = true,
    val options: List<LlmModelInfo> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val promptBuilder = PromptBuilder()
    private val memoryRetriever = MemoryRetriever()
    private val mutableHeader = MutableStateFlow(ChatHeaderState())
    val header: StateFlow<ChatHeaderState> = mutableHeader.asStateFlow()
    private val messageLimit = MutableStateFlow(ChatListPolicy.INITIAL_LIMIT)
    private val totalMessageCount = MutableStateFlow(0)
    val messages = messageLimit.flatMapLatest { limit -> container.chats.messages(conversationId, limit) }
        .onEach { rows ->
            if (AppBuildInfo.DEBUG) Log.d(PERFORMANCE_TAG, "Room emitió ${rows.size} mensajes para chat=$conversationId")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Messages pinned from this direct chat. */
    val pinnedMemories: StateFlow<List<Memory>> = container.memories.observe(conversationId)
        .map { memories -> memories.filter { it.isActive && it.isPinned && it.sourceMessageId != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hasOlderMessages = combine(messages, totalMessageCount) { visible, total -> visible.size < total }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val canRegenerate = messages
        .map { rows -> rows.any { it.role == MessageRole.CHARACTER } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val mutableStreamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage: StateFlow<ChatMessage?> = mutableStreamingMessage.asStateFlow()
    private val mutableGenerating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = mutableGenerating.asStateFlow()
    private val mutableBrain = MutableStateFlow(ChatBrainState())
    val brain: StateFlow<ChatBrainState> = mutableBrain.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice = mutableNotice.asStateFlow()
    val ttsState = container.ttsManager.state
    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            var conversation = container.chats.getConversation(conversationId) ?: return@launch
            if (conversation.userPersonaId == null) {
                val persona = container.userPersonas.ensureDefault()
                conversation = conversation.copy(userPersonaId = persona.id, updatedAt = System.currentTimeMillis())
                container.chats.saveConversation(conversation)
            }
            val character = container.characters.getCharacter(conversation.characterId) ?: return@launch
            mutableHeader.value = ChatHeaderState(character, conversation)
            refreshMessageCount()
            refreshBrain(character, conversation)
        }
    }

    fun send(text: String, composerMode: ComposerMode = ComposerMode.NORMAL) {
        val content = ActionModeFormatter.format(text, composerMode)
        if (content.isBlank() || generationJob?.isActive == true) return
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            val character = mutableHeader.value.character ?: return@launch
            val conversation = mutableHeader.value.conversation ?: return@launch
            val selection = container.aiProviders.resolveSelection(character.id, conversationId)
            if (selection.providerId == LOCAL_PROVIDER_ID) {
                val state = container.llm.state.value
                if (state !is LlmState.ModelReady && state !is LlmState.InternalWork) {
                    mutableError.value = "Selecciona y carga un modelo GGUF antes de conversar."
                    return@launch
                }
            }
            val userMessage = ChatMessage(
                UUID.randomUUID().toString(), conversationId, MessageRole.USER, content, true, System.currentTimeMillis(),
            )
            container.chats.saveMessage(userMessage)
            refreshMessageCount()
            val memorySettings = container.settings.memorySettings.first()
            container.memoryOrchestrator.handleManualIntents(userMessage, character, conversation, memorySettings)
            generateSafely(character, conversation, userMessage, memorySettings, GenerationMode.NORMAL_REPLY)
        }
    }

    fun continueAsCharacter() {
        if (generationJob?.isActive == true) return
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            val character = mutableHeader.value.character ?: return@launch
            val conversation = mutableHeader.value.conversation ?: return@launch
            val selection = container.aiProviders.resolveSelection(character.id, conversationId)
            if (selection.providerId == LOCAL_PROVIDER_ID) {
                val state = container.llm.state.value
                if (state !is LlmState.ModelReady && state !is LlmState.InternalWork) {
                    mutableError.value = "Selecciona y carga un modelo GGUF antes de continuar."
                    return@launch
                }
            }
            generateSafely(
                character, conversation, null, container.settings.memorySettings.first(),
                GenerationMode.CHARACTER_CONTINUE,
            )
        }
    }

    fun stop() {
        generationJob?.cancel()
        viewModelScope.launch { container.aiProviders.cancelGeneration() }
    }

    fun regenerate() {
        if (generationJob?.isActive == true) return
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            val lastCharacter = messages.value.lastOrNull { it.role == MessageRole.CHARACTER } ?: return@launch
            container.chats.deleteMessage(lastCharacter.id)
            val character = mutableHeader.value.character ?: return@launch
            val conversation = mutableHeader.value.conversation ?: return@launch
            val userMessage = messages.value.lastOrNull { it.role == MessageRole.USER } ?: return@launch
            generateSafely(
                character, conversation, userMessage, container.settings.memorySettings.first(),
                GenerationMode.NORMAL_REPLY,
            )
        }
    }

    fun deleteMessage(id: String) = viewModelScope.launch {
        container.chats.deleteMessage(id)
        refreshMessageCount()
    }

    fun rewindTo(id: String) = viewModelScope.launch {
        messages.value.firstOrNull { it.id == id }?.let { container.chats.rewindFrom(conversationId, it.createdAt); refreshMessageCount(); mutableNotice.value = "Conversación rebobinada." }
    }

    fun branchFrom(id: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        val current = mutableHeader.value
        val conversation = current.conversation ?: return@launch
        val target = messages.value.firstOrNull { it.id == id } ?: return@launch
        val now = System.currentTimeMillis()
        val branch = Conversation(UUID.randomUUID().toString(), conversation.characterId, "${conversation.title} · rama", conversation.userPersonaId, createdAt = now, updatedAt = now)
        container.chats.saveConversation(branch)
        container.chats.messagesUpTo(conversationId, target.createdAt).forEach { message ->
            container.chats.saveMessage(message.copy(id = UUID.randomUUID().toString(), conversationId = branch.id))
        }
        mutableNotice.value = "Rama creada."
        onCreated(branch.id)
    }

    fun pinMessageAsMemory(message: ChatMessage) = viewModelScope.launch {
        val character = mutableHeader.value.character ?: return@launch
        val conversation = mutableHeader.value.conversation ?: return@launch
        if (message.content.isBlank()) return@launch
        val now = System.currentTimeMillis()
        container.memories.save(
            Memory(
                id = UUID.randomUUID().toString(), characterId = character.id, conversationId = conversationId,
                userPersonaId = conversation.userPersonaId, type = MemoryType.FACT, content = message.content.trim(),
                normalizedContent = message.content.trim().lowercase(), importance = 0.9f, confidence = 1f,
                origin = if (message.role == MessageRole.USER) MemoryOrigin.USER_STATED_FACT else MemoryOrigin.CHARACTER_STATED,
                createdAt = now, updatedAt = now, lastAccessedAt = now,
                sourceMessageId = message.id, isPinned = true,
            ),
        )
        mutableNotice.value = "Memoria fijada."
    }

    fun reportMessage() { mutableNotice.value = "Reporte guardado localmente." }
    fun notify(message: String) { mutableNotice.value = message }
    fun dismissNotice() { mutableNotice.value = null }

    fun speakMessage(message: ChatMessage) = viewModelScope.launch {
        if (message.role != MessageRole.CHARACTER || message.content.isBlank()) return@launch
        val character = mutableHeader.value.character ?: return@launch
        speak(character, message.id, message.content)
    }

    fun stopAudio() = container.ttsManager.stop()

    fun loadOlderMessages() {
        messageLimit.value = ChatListPolicy.nextLimit(messageLimit.value)
    }

    fun dismissError() { mutableError.value = null }

    fun selectBrain(selection: ProviderModelSelection?) = viewModelScope.launch {
        container.aiProviders.setConversationSelection(conversationId, selection)
        val current = mutableHeader.value
        if (current.character != null && current.conversation != null) refreshBrain(current.character, current.conversation)
    }

    private suspend fun generate(
        character: Character,
        conversation: Conversation,
        userMessage: ChatMessage?,
        memorySettings: MemorySettings,
        generationMode: GenerationMode,
    ) {
        container.ttsManager.stop()
        val configured = container.settings.generationSettings.first()
        val activeLocalModel = container.models.active()
        val primarySelection = container.aiProviders.resolveSelection(character.id, conversationId)
        val primaryModel = container.aiProviders.modelInfo(primarySelection)
        val contextWindow = if (primarySelection.providerId == LOCAL_PROVIDER_ID) {
            ModelLoadPolicy.effectiveContextSize(configured.contextSize, activeLocalModel?.contextSize)
        } else {
            (primaryModel?.contextLength ?: UNKNOWN_REMOTE_CONTEXT).coerceIn(512, MAX_REMOTE_CONTEXT)
        }
        val settings = configured.copy(
            contextSize = contextWindow,
            maxTokens = minOf(configured.maxTokens, primaryModel?.maxOutputTokens ?: configured.maxTokens),
        )
        val localMemorySettings = configured.copy(
            contextSize = ModelLoadPolicy.effectiveContextSize(configured.contextSize, activeLocalModel?.contextSize),
        )
        val responseLanguage = container.settings.catalogLanguage.first().promptName
        val history = container.chats.recent(conversationId, 40)
        val persona = conversation.userPersonaId?.let { container.userPersonas.get(it) }
            ?: container.userPersonas.ensureDefault()
        val characterPreferences = container.characterPreferences.get(character.id)
        val contentMode = ContentPolicyResolver.resolve(
            container.settings.contentMode.first(), characterPreferences.contentOverride,
        )
        val lore = container.contextRepository.lore(character.id)
        val candidates = if (memorySettings.intelligentMemory) {
            container.memories.candidates(
                character.id, conversationId, conversation.userPersonaId, memorySettings.shareAcrossChats,
            )
        } else emptyList()
        val memoryLimit = when (memorySettings.level) {
            MemoryLevel.MINIMAL -> 4
            MemoryLevel.NORMAL -> 8
            MemoryLevel.DETAILED -> 12
        }
        val retrievalQuery = userMessage?.content
            ?: history.takeLast(6).joinToString(" ") { it.content }
        val retrievalHistory = if (userMessage != null) history.dropLast(1) else history
        val scoredMemories = memoryRetriever.retrieve(retrievalQuery, retrievalHistory, candidates, memoryLimit)
        val relationshipState = if (memorySettings.intelligentMemory) {
            container.relationships.get(character.id, conversationId, conversation.userPersonaId)?.relationshipSummary.orEmpty()
        } else ""
        val followUps = if (memorySettings.intelligentMemory && memorySettings.automaticFollowUps) {
            container.pendingEvents.due(
                character.id, conversationId, conversation.userPersonaId, memorySettings.shareAcrossChats,
            )
        } else emptyList()
        val summary = if (memorySettings.conversationSummaries) {
            container.summaries.latest(conversationId)?.summary.orEmpty()
        } else ""
        fun buildPromptFor(attemptContextWindow: Int, attemptMaxTokens: Int) = promptBuilder.build(
            PromptRequest(
                character = character,
                userName = persona.name,
                userPersona = persona.description,
                messages = history,
                loreEntries = lore,
                memories = scoredMemories.map { it.memory },
                conversationSummary = summary.ifBlank { conversation.summary },
                relationshipState = relationshipState,
                pendingFollowUps = followUps.map { it.description },
                contextWindow = attemptContextWindow,
                responseReserve = attemptMaxTokens,
                responseLanguage = responseLanguage,
                generationMode = generationMode,
                contentMode = contentMode,
            ),
        )
        val promptResult = buildPromptFor(settings.contextSize, settings.maxTokens)
        if (AppBuildInfo.DEBUG) {
            Log.d("LocalMemory", "Retrieved memories: ${promptResult.includedMemoryIds.size}")
            Log.d("LocalMemory", "Prompt tokens: ${promptResult.estimatedTokens}/${settings.contextSize}")
        }

        val response = ChatMessage(
            UUID.randomUUID().toString(), conversationId, MessageRole.CHARACTER, "", false, System.currentTimeMillis() + 1,
        )
        mutableStreamingMessage.value = response
        mutableGenerating.value = true
        val streamingBuffer = StreamingTextBuffer(STREAMING_UI_INTERVAL_MS)
        var receivedFirstToken = false
        var finalUsage: TokenUsage? = null
        var usedSelection = primarySelection
        var usedModel = primaryModel
        var timeToFirstTokenMillis: Long? = null
        val generationStarted = SystemClock.elapsedRealtime()
        try {
            try {
                val selections = listOf(primarySelection) + container.aiProviders.fallbackCandidates(primarySelection)
                var failure: ProviderStreamFailure? = null
                for (selection in selections) {
                    val model = container.aiProviders.modelInfo(selection)
                    container.apiUsageTracker.budgetBlock(model)?.let { throw ProviderStreamFailure(it, false) }
                    val attemptContextWindow = if (selection.providerId == LOCAL_PROVIDER_ID) {
                        localMemorySettings.contextSize
                    } else {
                        (model?.contextLength ?: UNKNOWN_REMOTE_CONTEXT).coerceIn(512, MAX_REMOTE_CONTEXT)
                    }
                    val attemptMaxTokens = minOf(settings.maxTokens, model?.maxOutputTokens ?: settings.maxTokens)
                    val attemptPrompt = if (selection == primarySelection) {
                        promptResult
                    } else {
                        buildPromptFor(attemptContextWindow, attemptMaxTokens)
                    }
                    container.memories.touch(attemptPrompt.includedMemoryIds)
                    val request = LlmRequest(
                        modelId = if (selection.providerId == LOCAL_PROVIDER_ID) {
                            activeLocalModel?.id ?: selection.modelId
                        } else selection.modelId,
                        systemPrompt = attemptPrompt.systemPrompt,
                        messages = attemptPrompt.messages,
                        temperature = settings.temperature,
                        topP = settings.topP,
                        topK = settings.topK,
                        maxTokens = attemptMaxTokens,
                        localFormattedPrompt = attemptPrompt.text,
                        localGenerationSettings = localMemorySettings.copy(maxTokens = attemptMaxTokens),
                        metadata = mapOf("conversationId" to conversationId, "characterId" to character.id),
                    )
                    try {
                        val attempt = executeProviderAttempt(selection, request) { token ->
                            receivedFirstToken = true
                            streamingBuffer.append(token, SystemClock.elapsedRealtime())?.let { content ->
                                mutableStreamingMessage.value = response.copy(content = content)
                            }
                        }
                        usedSelection = selection
                        usedModel = model
                        finalUsage = attempt.usage
                        timeToFirstTokenMillis = attempt.timeToFirstTokenMillis
                        failure = null
                        break
                    } catch (error: ProviderStreamFailure) {
                        failure = error
                        if (!FallbackPolicy.canFallback(error.error, error.emittedText)) break
                    }
                }
                failure?.let { throw it }
                if (!receivedFirstToken) throw IllegalStateException("El modelo terminó sin generar texto.")
            } catch (_: TimeoutCancellationException) {
                container.aiProviders.cancelGeneration()
                mutableError.value = if (receivedFirstToken) {
                    "La respuesta tardó demasiado y se detuvo. Reduce los tokens máximos."
                } else {
                    "El proveedor no produjo respuesta a tiempo. Puedes reintentar, cambiar proveedor o usar Local GGUF."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProviderStreamFailure) {
                mutableError.value = error.error.friendlyMessage + if (error.error.kind != ProviderErrorKind.AUTHENTICATION) {
                    " Puedes reintentar, cambiar proveedor o usar Local GGUF."
                } else ""
            } catch (error: Throwable) {
                mutableError.value = error.message ?: "La inferencia se interrumpió."
            }

            val finalContent = streamingBuffer.completed()
            if (finalContent.isNotBlank()) {
                val completedResponse = response.copy(content = finalContent, isComplete = true)
                mutableStreamingMessage.value = completedResponse
                container.chats.saveMessage(completedResponse)
                refreshMessageCount()
                val autoplay = when (characterPreferences.autoplayOverride) {
                    VoiceAutoplayOverride.USE_GLOBAL -> container.settings.ttsSettings.first().autoPlayResponses
                    VoiceAutoplayOverride.ALWAYS -> true
                    VoiceAutoplayOverride.NEVER -> false
                }
                if (autoplay) speak(character, completedResponse.id, finalContent)
                runCatching {
                    container.apiUsageTracker.record(
                        usedSelection, usedModel, finalUsage, conversationId, character.id,
                        timeToFirstTokenMillis, SystemClock.elapsedRealtime() - generationStarted,
                    )
                }
                viewModelScope.launch(Dispatchers.Default) {
                    delay(BACKGROUND_MEMORY_DELAY_MILLIS)
                    runCatching {
                        container.memoryOrchestrator.markFollowUpsUsed(followUps, userMessage?.content.orEmpty(), finalContent)
                        container.memoryOrchestrator.processAfterTurn(
                            userMessage, completedResponse, character, conversation, memorySettings, localMemorySettings,
                        )
                    }.onFailure {
                        if (AppBuildInfo.DEBUG) Log.d("LocalMemory", "Background memory skipped: ${it.message}")
                    }
                }
            }
        } finally {
            mutableStreamingMessage.value = null
            mutableGenerating.value = false
        }
    }

    private suspend fun generateSafely(
        character: Character,
        conversation: Conversation,
        userMessage: ChatMessage?,
        memorySettings: MemorySettings,
        generationMode: GenerationMode,
    ) {
        try {
            generate(character, conversation, userMessage, memorySettings, generationMode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (AppBuildInfo.DEBUG) Log.e("ChatGeneration", "Generation setup failed", error)
            mutableError.value = error.message?.takeIf { it.isNotBlank() }
                ?: "No se pudo preparar la respuesta. Intenta nuevamente."
            mutableStreamingMessage.value = null
            mutableGenerating.value = false
        }
    }

    private suspend fun speak(character: Character, messageId: String, text: String) {
        val preferences = container.characterPreferences.get(character.id)
        val global = container.settings.ttsSettings.first()
        val voice = preferences.voiceId?.let { container.voices.get(it) }
            ?: if (global.systemFallback) {
                TtsManager.systemVoice(container.settings.catalogLanguage.first().code ?: java.util.Locale.getDefault().toLanguageTag())
            } else return
        val sanitized = TtsTextSanitizer.sanitize(text, preferences.readMode)
        if (sanitized.isBlank()) return
        container.ttsManager.speak(
            messageId = messageId,
            text = sanitized,
            voice = voice,
            settings = TtsSynthesisSettings(
                speed = preferences.speed,
                pitch = preferences.pitch,
                volume = preferences.volume,
            ),
            unloadAfter = global.unloadWhenIdle,
        )
    }

    override fun onCleared() {
        container.ttsManager.stop()
        super.onCleared()
    }

    private data class AttemptMetrics(val usage: TokenUsage?, val timeToFirstTokenMillis: Long?)
    private class ProviderStreamFailure(val error: ProviderError, val emittedText: Boolean) : RuntimeException(error.friendlyMessage)

    private suspend fun executeProviderAttempt(
        selection: ProviderModelSelection,
        request: LlmRequest,
        onText: (String) -> Unit,
    ): AttemptMetrics {
        suspend fun collectEvents(): AttemptMetrics = coroutineScope {
            val started = SystemClock.elapsedRealtime()
            var receivedText = false
            var usage: TokenUsage? = null
            var ttft: Long? = null
            val events = container.aiProviders.generate(selection, request).produceIn(this)
            try {
                withTimeout(MAX_GENERATION_MILLIS) {
                    while (true) {
                        val event = if (!receivedText) {
                            withTimeout(FIRST_TOKEN_TIMEOUT_MILLIS) { events.receiveCatching().getOrNull() }
                        } else events.receiveCatching().getOrNull() ?: break
                        if (event == null) break
                        when (event) {
                            is LlmStreamEvent.TextDelta -> {
                                if (!receivedText) ttft = SystemClock.elapsedRealtime() - started
                                receivedText = true
                                onText(event.text)
                            }
                            is LlmStreamEvent.Usage -> usage = mergeUsage(usage, event.usage)
                            is LlmStreamEvent.Error -> throw ProviderStreamFailure(event.error, receivedText)
                            LlmStreamEvent.Completed -> break
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                throw ProviderStreamFailure(
                    ProviderError(ProviderErrorKind.TIMEOUT, "El proveedor tardó demasiado en responder."),
                    receivedText,
                )
            }
            if (!receivedText) throw ProviderStreamFailure(
                ProviderError(ProviderErrorKind.PROVIDER, "El proveedor terminó sin generar texto."),
                false,
            )
            AttemptMetrics(usage, ttft)
        }

        return if (selection.providerId == LOCAL_PROVIDER_ID) {
            container.llmTasks.run(LlmTaskPriority.CHAT_GENERATION) { collectEvents() }
        } else collectEvents()
    }

    private fun mergeUsage(current: TokenUsage?, update: TokenUsage) = TokenUsage(
        inputTokens = update.inputTokens ?: current?.inputTokens,
        outputTokens = update.outputTokens ?: current?.outputTokens,
        cachedInputTokens = update.cachedInputTokens ?: current?.cachedInputTokens,
    )

    private suspend fun refreshBrain(character: Character, conversation: Conversation) {
        val selection = container.aiProviders.resolveSelection(character.id, conversation.id)
        val definitions = container.aiProviders.definitions()
        val providerName = definitions.firstOrNull { it.providerId == selection.providerId }?.displayName ?: selection.providerId
        val options = container.aiProviders.allCachedModels()
        val selectedModel = options.firstOrNull {
            it.providerId == selection.providerId && (it.modelId == selection.modelId || selection.providerId == LOCAL_PROVIDER_ID)
        }
        mutableBrain.value = ChatBrainState(
            selection = selection,
            label = "$providerName · ${selectedModel?.displayName ?: selection.modelId}",
            isLocal = selection.providerId == LOCAL_PROVIDER_ID,
            options = options,
        )
    }

    private suspend fun refreshMessageCount() {
        totalMessageCount.value = container.chats.messageCount(conversationId)
    }

    private companion object {
        const val STREAMING_UI_INTERVAL_MS = 32L
        const val FIRST_TOKEN_TIMEOUT_MILLIS = 75_000L
        const val MAX_GENERATION_MILLIS = 3L * 60L * 1_000L
        const val BACKGROUND_MEMORY_DELAY_MILLIS = 3_000L
        const val PERFORMANCE_TAG = "LocalPerformance"
        const val UNKNOWN_REMOTE_CONTEXT = 8_192
        const val MAX_REMOTE_CONTEXT = 131_072
    }

    class Factory(
        private val conversationId: String,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(conversationId, container) as T
    }
}
