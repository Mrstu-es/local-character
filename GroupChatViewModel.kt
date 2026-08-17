package com.localcharacter.app.ui.group

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localcharacter.app.AppBuildInfo
import com.localcharacter.app.AppContainer
import com.localcharacter.app.data.model.ModelLoadPolicy
import com.localcharacter.app.data.settings.MemoryLevel
import com.localcharacter.app.domain.conversation.ActionModeFormatter
import com.localcharacter.app.domain.conversation.ComposerMode
import com.localcharacter.app.domain.conversation.ContentPolicyResolver
import com.localcharacter.app.domain.conversation.GenerationMode
import com.localcharacter.app.domain.group.GroupSpeakerSelector
import com.localcharacter.app.domain.group.GroupTurnOrchestrator
import com.localcharacter.app.domain.group.GroupContextResolver
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.ChatMessage
import com.localcharacter.app.domain.model.GroupConversation
import com.localcharacter.app.domain.model.GroupMessage
import com.localcharacter.app.domain.model.GroupMessageRole
import com.localcharacter.app.domain.model.GroupParticipant
import com.localcharacter.app.domain.model.GroupContext
import com.localcharacter.app.domain.model.GroupParticipantContext
import com.localcharacter.app.domain.model.LoreEntry
import com.localcharacter.app.domain.model.GroupMemory
import com.localcharacter.app.domain.model.Memory
import com.localcharacter.app.domain.model.MessageRole
import com.localcharacter.app.domain.prompt.PromptBuilder
import com.localcharacter.app.domain.prompt.PromptRequest
import com.localcharacter.app.llm.LlmState
import com.localcharacter.app.llm.LlmTaskPriority
import com.localcharacter.app.llm.provider.LlmModelInfo
import com.localcharacter.app.llm.provider.LlmRequest
import com.localcharacter.app.llm.provider.LlmStreamEvent
import com.localcharacter.app.llm.provider.ProviderError
import com.localcharacter.app.llm.provider.ProviderErrorKind
import com.localcharacter.app.llm.provider.ProviderModelSelection
import com.localcharacter.app.llm.provider.TokenUsage
import com.localcharacter.app.llm.provider.LOCAL_PROVIDER_ID
import com.localcharacter.app.domain.conversation.TtsTextSanitizer
import com.localcharacter.app.domain.model.VoiceAutoplayOverride
import com.localcharacter.app.tts.TtsManager
import com.localcharacter.app.tts.TtsSynthesisSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.isActive
import java.util.UUID

data class GroupHeaderState(
    val group: GroupConversation? = null,
    val participants: List<GroupParticipant> = emptyList(),
    val characters: List<Character> = emptyList(),
    val context: GroupContext = GroupContext("") ,
    val participantContexts: List<GroupParticipantContext> = emptyList(),
)

class GroupChatViewModel(
    private val groupId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val promptBuilder = PromptBuilder()
    private val selector = GroupSpeakerSelector()
    private val orchestrator = GroupTurnOrchestrator(selector)
    private val contextResolver = GroupContextResolver()
    private val mutableHeader = MutableStateFlow(GroupHeaderState())
    val header: StateFlow<GroupHeaderState> = mutableHeader.asStateFlow()
    val messages = container.groups.messages(groupId, 120)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableStreaming = MutableStateFlow<GroupMessage?>(null)
    val streamingMessage: StateFlow<GroupMessage?> = mutableStreaming.asStateFlow()
    private val mutableGenerating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = mutableGenerating.asStateFlow()
    private val mutableSelecting = MutableStateFlow(false)
    val selectingSpeaker: StateFlow<Boolean> = mutableSelecting.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()
    private val mutablePinnedMemories = MutableStateFlow<List<GroupMemory>>(emptyList())
    val pinnedMemories: StateFlow<List<GroupMemory>> = mutablePinnedMemories.asStateFlow()
    private val mutableDecision = MutableStateFlow<String?>(null)
    val decisionReason: StateFlow<String?> = mutableDecision.asStateFlow()
    private val mutableManualSpeaker = MutableStateFlow<String?>(null)
    val manualSpeaker: StateFlow<String?> = mutableManualSpeaker.asStateFlow()
    val ttsState = container.ttsManager.state
    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            val group = container.groups.get(groupId) ?: return@launch
            val participants = container.groups.getParticipants(groupId)
            val chars = participants.mapNotNull { container.characters.getCharacter(it.characterId) }
            mutableHeader.value = GroupHeaderState(
                group, participants, chars,
                container.groups.context(groupId), container.groups.participantContexts(groupId),
            )
        }
        refreshPinnedMemories()
    }

    fun send(text: String, mode: ComposerMode = ComposerMode.NORMAL) {
        val content = ActionModeFormatter.format(text, mode)
        if (content.isBlank() || generationJob?.isActive == true) return
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            val state = mutableHeader.value
            val group = state.group ?: return@launch
            val persona = group.userPersonaId?.let { container.userPersonas.get(it) } ?: container.userPersonas.ensureDefault()
            container.groups.saveMessage(GroupMessage(UUID.randomUUID().toString(), groupId, GroupMessageRole.USER, content, userPersonaId = persona.id))
            if (group.sharedMemoryEnabled && isSharedEventCandidate(content)) {
                val now = System.currentTimeMillis()
                container.groups.saveMemory(com.localcharacter.app.domain.model.GroupMemory(UUID.randomUUID().toString(), groupId, "SHARED_EVENT", content, importance = 0.55f, createdAt = now, updatedAt = now))
            }
            generateForTurn(content, false, mutableManualSpeaker.value)
            mutableManualSpeaker.value = null
        }
    }

    fun continueGroup() {
        if (generationJob?.isActive == true) return
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            generateForTurn(null, true, mutableManualSpeaker.value)
            mutableManualSpeaker.value = null
        }
    }

    fun selectManualSpeaker(characterId: String) { mutableManualSpeaker.value = characterId }

    fun stop() {
        generationJob?.cancel()
        viewModelScope.launch { container.aiProviders.cancelGeneration(); container.ttsManager.stop() }
    }

    fun stopAudio() = container.ttsManager.stop()
    fun dismissError() { mutableError.value = null }
    fun dismissNotice() { mutableNotice.value = null }

    fun rewindTo(id: String) = viewModelScope.launch {
        messages.value.firstOrNull { it.id == id }?.let {
            container.groups.rewindFrom(groupId, it.createdAt)
            mutableNotice.value = "Grupo rebobinado."
        }
    }

    fun branchFrom(id: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        val group = mutableHeader.value.group ?: return@launch
        val target = messages.value.firstOrNull { it.id == id } ?: return@launch
        val participants = container.groups.getParticipants(groupId)
        val newGroup = container.groups.create(
            name = "${group.name} · rama",
            characterIds = participants.map { it.characterId },
            userPersonaId = group.userPersonaId,
            avatarPath = group.avatarPath,
            turnMode = group.turnMode,
        )
        container.groups.saveContext(container.groups.context(groupId).copy(groupId = newGroup.id))
        container.groups.participantContexts(groupId).forEach { context ->
            container.groups.saveParticipantContext(context.copy(groupId = newGroup.id))
        }
        container.groups.messagesUpTo(groupId, target.createdAt).forEach { message ->
            container.groups.saveMessage(message.copy(id = UUID.randomUUID().toString(), groupId = newGroup.id))
        }
        mutableNotice.value = "Rama de grupo creada."
        onCreated(newGroup.id)
    }

    fun speakMessage(message: GroupMessage) = viewModelScope.launch {
        if (message.role != GroupMessageRole.CHARACTER || message.content.isBlank()) return@launch
        val character = mutableHeader.value.characters.firstOrNull { it.id == message.senderCharacterId } ?: return@launch
        val preferences = container.characterPreferences.get(character.id)
        val global = container.settings.ttsSettings.first()
        val voice = preferences.voiceId?.let { container.voices.get(it) }
            ?: if (global.systemFallback) TtsManager.systemVoice(container.settings.catalogLanguage.first().code ?: java.util.Locale.getDefault().toLanguageTag()) else return@launch
        val text = TtsTextSanitizer.sanitize(message.content, preferences.readMode)
        if (text.isNotBlank()) container.ttsManager.speak(message.id, text, voice, TtsSynthesisSettings(preferences.speed, preferences.pitch, preferences.volume), global.unloadWhenIdle)
    }

    fun pinMessageAsMemory(message: GroupMessage) = viewModelScope.launch {
        if (message.content.isBlank()) return@launch
        val now = System.currentTimeMillis()
        val memory = GroupMemory(UUID.randomUUID().toString(), groupId, "PINNED", message.content.trim(), 0.9f, now, now, true)
        container.groups.saveMemory(memory)
        mutablePinnedMemories.value = (mutablePinnedMemories.value.filterNot { it.content == memory.content } + memory)
        mutableNotice.value = "Memoria de grupo fijada."
    }

    private fun refreshPinnedMemories() = viewModelScope.launch {
        mutablePinnedMemories.value = container.groups.activeMemories(groupId).filter { it.type == "PINNED" }
    }

    fun reportMessage() { mutableNotice.value = "Reporte guardado localmente." }
    fun notify(message: String) { mutableNotice.value = message }

    private suspend fun generateForTurn(input: String?, isNext: Boolean, forcedCharacterId: String? = null) {
        val state = mutableHeader.value
        val group = state.group ?: return
        val recent = container.groups.recent(groupId, 80)
        mutableSelecting.value = true
        val speakers = orchestrator.speakersForTurn(group, input, recent, state.participants, state.characters, forcedCharacterId)
        mutableSelecting.value = false
        if (speakers.isEmpty()) { mutableError.value = "Selecciona un personaje para responder."; return }
        mutableDecision.value = "${state.characters.firstOrNull { it.id == speakers.first() }?.name.orEmpty()} · selección contextual"
        var chain = 0
        val planned = speakers.toMutableList()
        var cursor = 0
        while (cursor < planned.size) {
            val speakerId = planned[cursor]
            if (!kotlinx.coroutines.currentCoroutineContext().isActive || chain >= group.maxBotChain) break
            val character = state.characters.firstOrNull { it.id == speakerId }
            if (character == null) { cursor++; continue }
            val generated = generateCharacter(group, character, state, input.takeIf { chain == 0 && !isNext })
            chain++
            // A bot may invite another participant by name. Only then do we extend the
            // chain, and the strict maxBotChain still bounds it.
            if (generated != null && cursor + 1 >= planned.size && chain < group.maxBotChain) {
                val followUp = selector.decide(generated, container.groups.recent(groupId, 80), state.participants, state.characters, group.turnMode)
                if (followUp.reason == "direct mention" && followUp.characterId != speakerId && followUp.characterId != null) planned += followUp.characterId
            }
            cursor++
        }
    }

    private suspend fun generateCharacter(group: GroupConversation, character: Character, state: GroupHeaderState, userInput: String?): String? {
        val selection = container.aiProviders.resolveSelection(character.id, groupId)
        if (selection.providerId == LOCAL_PROVIDER_ID) {
            val llm = container.llm.state.value
            if (llm !is LlmState.ModelReady && llm !is LlmState.InternalWork) {
                mutableError.value = "Selecciona y carga un modelo GGUF antes de conversar."; return null
            }
        }
        val persona = group.userPersonaId?.let { container.userPersonas.get(it) } ?: container.userPersonas.ensureDefault()
        val configured = container.settings.generationSettings.first()
        val activeModel = container.models.active()
        val model = container.aiProviders.modelInfo(selection)
        val contextWindow = if (selection.providerId == LOCAL_PROVIDER_ID) {
            ModelLoadPolicy.effectiveContextSize(configured.contextSize, activeModel?.contextSize)
        } else (model?.contextLength ?: 8_192).coerceIn(512, 131_072)
        val maxTokens = minOf(configured.maxTokens, model?.maxOutputTokens ?: configured.maxTokens)
        val transcript = container.groups.recent(groupId, 60)
        val transcriptAsMessages = transcript.map { message ->
            ChatMessage(
                id = message.id, conversationId = groupId,
                role = if (message.role == GroupMessageRole.USER) MessageRole.USER else MessageRole.CHARACTER,
                content = if (message.role == GroupMessageRole.CHARACTER) {
                    val name = state.characters.firstOrNull { it.id == message.senderCharacterId }?.name ?: "Personaje"
                    "$name: ${message.content}"
                } else message.content,
                createdAt = message.createdAt,
            )
        }
        val participantSummary = state.characters.filter { it.id != character.id }.joinToString("\n") {
            "- ${it.name}: ${it.description.take(240)} ${it.personality.take(160)}"
        }
        val liveContext = container.groups.context(groupId)
        val participantContext = container.groups.participantContext(groupId, character.id)
        val resolvedContext = contextResolver.resolve(
            character = character,
            context = liveContext,
            participant = participantContext,
            lore = container.contextRepository.lore(character.id),
        )
        val groupInstruction = """
            GROUP CONTEXT (highest priority; do not contradict it)
            Title: ${liveContext.title.ifBlank { group.name }}
            ${resolvedContext.hardRules}
            User role: ${liveContext.userRole}
            Opening situation: ${liveContext.openingMessage}
            Notes: ${liveContext.notes}
            ${if (resolvedContext.participantInstructions.isBlank()) "" else "CURRENT PARTICIPANT OVERRIDES\n${resolvedContext.participantInstructions}"}
            GROUP SCENE
            Participants: ${state.characters.joinToString(", ") { it.name }}, ${persona.name}
            CURRENT SPEAKER: ${character.name}
            OTHER PARTICIPANT SUMMARIES:
            $participantSummary
            Only write ${character.name}'s dialogue and actions. Never write dialogue, thoughts, actions, or decisions for another participant or for ${persona.name}.
            Every group message includes its speaker. Private memories from individual chats are not shared here.
        """.trimIndent()
        val current = userInput?.let {
            ChatMessage(UUID.randomUUID().toString(), groupId, MessageRole.USER, it, true, System.currentTimeMillis())
        }
        val request = PromptRequest(
            character = character.copy(
                scenario = resolvedContext.scenario,
                systemPrompt = listOf(character.systemPrompt, groupInstruction).filter(String::isNotBlank).joinToString("\n\n"),
            ),
            userName = persona.name,
            userPersona = persona.description,
            messages = (transcriptAsMessages.filterNot { userInput != null && it.role == MessageRole.USER && it.content == userInput } + listOfNotNull(current)).takeLast(70),
            loreEntries = resolvedContext.lore,
            memories = if (container.settings.memorySettings.first().intelligentMemory) {
                container.memories.candidates(character.id, groupId, group.userPersonaId, false)
            } else emptyList(),
            conversationSummary = container.groups.activeMemories(groupId).joinToString("\n") { it.content },
            contextWindow = contextWindow,
            responseReserve = maxTokens,
            responseLanguage = container.settings.catalogLanguage.first().promptName,
            generationMode = if (current == null) GenerationMode.CHARACTER_CONTINUE else GenerationMode.NORMAL_REPLY,
            contentMode = ContentPolicyResolver.resolve(container.settings.contentMode.first(), container.characterPreferences.get(character.id).contentOverride),
        )
        val prompt = promptBuilder.build(request)
        val response = GroupMessage(UUID.randomUUID().toString(), groupId, GroupMessageRole.CHARACTER, "", false, senderCharacterId = character.id, createdAt = System.currentTimeMillis() + 1)
        mutableStreaming.value = response
        mutableGenerating.value = true
        val buffer = StringBuilder()
        try {
            val requestBody = LlmRequest(
                modelId = if (selection.providerId == LOCAL_PROVIDER_ID) activeModel?.id ?: selection.modelId else selection.modelId,
                systemPrompt = prompt.systemPrompt, messages = prompt.messages, temperature = configured.temperature,
                topP = configured.topP, topK = configured.topK, maxTokens = maxTokens, localFormattedPrompt = prompt.text,
                localGenerationSettings = configured.copy(contextSize = contextWindow, maxTokens = maxTokens),
                metadata = mapOf("groupId" to groupId, "characterId" to character.id),
            )
            execute(selection, requestBody) { delta ->
                buffer.append(delta)
                mutableStreaming.value = response.copy(content = buffer.toString())
            }
            if (buffer.isNotBlank()) {
                val done = response.copy(content = buffer.toString().trim(), isComplete = true)
                mutableStreaming.value = done
                container.groups.saveMessage(done)
                maybeSpeak(character, done)
                return done.content
            } else mutableError.value = "El proveedor no generó texto para ${character.name}."
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Throwable) {
            Log.e("GroupGeneration", "Generation failed", error)
            mutableError.value = error.message ?: "No se pudo generar la respuesta de ${character.name}."
        } finally {
            mutableStreaming.value = null
            mutableGenerating.value = false
        }
        return null
    }

    private suspend fun execute(selection: ProviderModelSelection, request: LlmRequest, onText: (String) -> Unit) {
        suspend fun collect() = coroutineScope {
            val events = container.aiProviders.generate(selection, request).produceIn(this)
            var received = false
            withTimeout(180_000L) {
                while (true) {
                    val event = events.receiveCatching().getOrNull() ?: break
                    when (event) {
                        is LlmStreamEvent.TextDelta -> { received = true; onText(event.text) }
                        is LlmStreamEvent.Error -> throw IllegalStateException(event.error.friendlyMessage)
                        is LlmStreamEvent.Usage, LlmStreamEvent.Completed -> if (event is LlmStreamEvent.Completed) break
                    }
                }
            }
            if (!received) error("El proveedor terminó sin generar texto.")
        }
        if (selection.providerId == LOCAL_PROVIDER_ID) container.llmTasks.run(LlmTaskPriority.CHAT_GENERATION) { collect() } else collect()
    }

    private suspend fun maybeSpeak(character: Character, message: GroupMessage) {
        val preferences = container.characterPreferences.get(character.id)
        val global = container.settings.ttsSettings.first()
        val autoplay = when (preferences.autoplayOverride) {
            VoiceAutoplayOverride.USE_GLOBAL -> global.autoPlayResponses
            VoiceAutoplayOverride.ALWAYS -> true
            VoiceAutoplayOverride.NEVER -> false
        }
        if (!autoplay) return
        val voice = preferences.voiceId?.let { container.voices.get(it) }
            ?: if (global.systemFallback) TtsManager.systemVoice(container.settings.catalogLanguage.first().code ?: java.util.Locale.getDefault().toLanguageTag()) else return
        val text = TtsTextSanitizer.sanitize(message.content, preferences.readMode)
        if (text.isNotBlank()) {
            // Chain replies wait for the previous voice to become idle, so voices never overlap.
            container.ttsManager.speak(message.id, text, voice, TtsSynthesisSettings(preferences.speed, preferences.pitch, preferences.volume), global.unloadWhenIdle)
            container.ttsManager.state.filter { it is com.localcharacter.app.tts.TtsPlaybackState.Idle || it is com.localcharacter.app.tts.TtsPlaybackState.Failed }.first()
        }
    }

    private fun isSharedEventCandidate(content: String): Boolean {
        val value = content.lowercase()
        return value.length >= 18 && listOf("acord", "mañana", "manana", "fuimos", "visitamos", "iremos", "vamos a").any(value::contains)
    }

    class Factory(private val groupId: String, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = GroupChatViewModel(groupId, container) as T
    }
}
