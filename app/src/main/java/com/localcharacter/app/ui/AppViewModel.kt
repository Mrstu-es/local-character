package com.localcharacter.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.localcharacter.app.AppContainer
import com.localcharacter.app.LocalCharacterApplication
import com.localcharacter.app.data.charactercard.CharacterCardParser
import com.localcharacter.app.data.catalog.LocalCharacterCatalogProvider
import com.localcharacter.app.data.model.GgufMetadataParser
import com.localcharacter.app.data.model.ModelStorageManager
import com.localcharacter.app.data.model.ModelLoadPolicy
import com.localcharacter.app.data.settings.ThemeMode
import com.localcharacter.app.data.settings.MemorySettings
import com.localcharacter.app.data.settings.TtsSettings
import com.localcharacter.app.data.voice.RemoteVoice
import com.localcharacter.app.data.settings.CustomRepositorySettings
import com.localcharacter.app.data.settings.CatalogLanguage
import com.localcharacter.app.data.settings.AiProviderSettings
import com.localcharacter.app.data.settings.AiBudgetSettings
import com.localcharacter.app.data.settings.CustomAiProviderSettings
import com.localcharacter.app.domain.character.CatalogRequest
import com.localcharacter.app.domain.character.CatalogSort
import com.localcharacter.app.domain.character.ProviderAvailability
import com.localcharacter.app.ui.catalog.ExploreCatalogUiState
import com.localcharacter.app.ui.catalog.RemoteDetailUiState
import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.Conversation
import com.localcharacter.app.domain.model.GroupConversation
import com.localcharacter.app.domain.model.GroupParticipant
import com.localcharacter.app.domain.model.GroupTurnMode
import com.localcharacter.app.domain.model.GroupContext
import com.localcharacter.app.domain.model.GroupParticipantContext
import com.localcharacter.app.domain.model.GenerationSettings
import com.localcharacter.app.domain.model.ModelDescriptor
import com.localcharacter.app.domain.model.CharacterPreferences
import com.localcharacter.app.domain.model.ContentMode
import com.localcharacter.app.domain.model.UserPersona
import com.localcharacter.app.domain.model.VoiceModel
import com.localcharacter.app.tts.TtsManager
import com.localcharacter.app.tts.TtsSynthesisSettings
import com.localcharacter.app.llm.LlmState
import com.localcharacter.app.llm.LlmTaskPriority
import com.localcharacter.app.llm.provider.LlmModelInfo
import com.localcharacter.app.llm.provider.ProviderConnectionResult
import com.localcharacter.app.llm.provider.ProviderModelSelection
import com.localcharacter.app.llm.provider.ProviderSummary
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container: AppContainer = (application as LocalCharacterApplication).container
    private val resolver = application.contentResolver
    private val cardParser = CharacterCardParser()
    private val ggufParser = GgufMetadataParser()
    private val modelStorage = ModelStorageManager(application.applicationContext)

    val characters = container.characters.characters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val conversations = container.chats.allConversations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups = container.groups.allGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableGroupMembers = MutableStateFlow<Map<String, List<Character>>>(emptyMap())
    val groupMembers = mutableGroupMembers.asStateFlow()
    val models = container.models.models.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val themeMode = container.settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val catalogLanguage = container.settings.catalogLanguage.stateIn(
        viewModelScope, SharingStarted.Eagerly, CatalogLanguage.SPANISH,
    )
    val onboardingComplete = container.settings.onboardingComplete.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val generationSettings = container.settings.generationSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenerationSettings.Balanced)
    val memorySettings = container.settings.memorySettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemorySettings())
    val userPersonas = container.userPersonas.personas.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val contentMode = container.settings.contentMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, ContentMode.STANDARD,
    )
    val adultContentConfirmed = container.settings.adultContentConfirmed.stateIn(
        viewModelScope, SharingStarted.Eagerly, false,
    )
    val ttsSettings = container.settings.ttsSettings.stateIn(
        viewModelScope, SharingStarted.Eagerly, TtsSettings(),
    )
    val installedVoices = container.voices.voices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val voiceRepositories = container.voiceRepositories.repositories.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val ttsState = container.ttsManager.state
    private val mutableRemoteVoices = MutableStateFlow<List<RemoteVoice>>(emptyList())
    val remoteVoices = mutableRemoteVoices.asStateFlow()
    private val mutableVoiceOperation = MutableStateFlow<String?>(null)
    val voiceOperation = mutableVoiceOperation.asStateFlow()
    val aiProviderSettings = container.settings.aiProviderSettings.stateIn(
        viewModelScope, SharingStarted.Eagerly, AiProviderSettings(),
    )
    private val mutableProviderSummaries = MutableStateFlow<List<ProviderSummary>>(emptyList())
    val providerSummaries = mutableProviderSummaries.asStateFlow()
    private val mutableProviderOperation = MutableStateFlow<String?>(null)
    val providerOperation = mutableProviderOperation.asStateFlow()
    private val mutableMonthlyAiSpend = MutableStateFlow(0.0)
    val monthlyAiSpend = mutableMonthlyAiSpend.asStateFlow()
    val customRepositories = container.settings.customRepositories.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val enabledCatalogProviderIds = combine(
        container.settings.enabledCatalogProviders,
        container.settings.customRepositories,
    ) { builtIn, custom ->
        builtIn + custom.filter { it.enabled }.map { AppContainer.customRepositoryProviderId(it.id) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, setOf("ai_character_cards", "local"))
    val llmState = container.llm.state
    private val mutableModelPreparation = MutableStateFlow<String?>(null)
    val modelPreparation = mutableModelPreparation.asStateFlow()
    val device = container.device.inspect()

    private val mutableCatalog = MutableStateFlow(
        ExploreCatalogUiState(providers = container.catalogs.descriptors, language = CatalogLanguage.SPANISH),
    )
    val catalog = mutableCatalog.asStateFlow()
    private val mutableRemoteDetail = MutableStateFlow<RemoteDetailUiState>(RemoteDetailUiState.Idle)
    val remoteDetail = mutableRemoteDetail.asStateFlow()
    private var catalogSearchJob: Job? = null
    private var remoteDetailJob: Job? = null
    private var catalogStarted = false
    private val mutableRepositoryTestStatus = MutableStateFlow<String?>(null)
    val repositoryTestStatus = mutableRepositoryTestStatus.asStateFlow()
    private var repositoryTestUrl: String? = null
    private var repositoryTestCount: Int? = null

    private val mutableEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            groups.collect { current ->
                val mapped = current.associate { group ->
                    group.id to container.groups.getParticipants(group.id).mapNotNull { container.characters.getCharacter(it.characterId) }
                }
                mutableGroupMembers.value = mapped
            }
        }
        refreshAiProviderSummaries()
        viewModelScope.launch {
            val configured = container.settings.generationSettings.first()
            val normalized = configured.copy(
                threads = ModelLoadPolicy.effectiveThreads(configured.threads, device.cpuCores),
                batchSize = ModelLoadPolicy.effectiveBatchSize(
                    configured.batchSize.takeUnless { it <= 128 } ?: GenerationSettings.Balanced.batchSize,
                ),
            )
            if (normalized != configured) container.settings.setGenerationSettings(normalized)
            // Room remembers which model the user chose, but native RAM never survives a
            // process restart. Restore it here so the check mark always means "ready to chat".
            val lastActive = container.models.active()
            if (lastActive != null && container.llm.state.value is LlmState.NoModelLoaded) {
                runCatching { loadModelInternal(lastActive) }.onFailure { error ->
                    container.models.clearActive()
                    mutableEvents.emit(error.message ?: "No se pudo restaurar el último modelo.")
                }
            }
        }
        viewModelScope.launch {
            container.settings.customRepositories.collect { repositories ->
                container.replaceCustomRepositories(repositories)
                val providers = container.catalogs.descriptors
                val current = mutableCatalog.value
                mutableCatalog.value = current.copy(
                    providers = providers,
                    selectedProviderId = current.selectedProviderId.takeIf { selected -> providers.any { it.id == selected } }
                        ?: LocalCharacterCatalogProvider.ID,
                )
            }
        }
        viewModelScope.launch {
            container.settings.catalogLanguage.collect { language ->
                val current = mutableCatalog.value
                if (current.language != language) {
                    mutableCatalog.value = current.copy(language = language, items = emptyList(), nextCursor = null)
                    if (catalogStarted) scheduleCatalogSearch(delayMillis = 0)
                }
            }
        }
        viewModelScope.launch {
            container.settings.contentMode.collect {
                if (catalogStarted) scheduleCatalogSearch(delayMillis = 0)
            }
        }
    }

    fun completeOnboarding() = viewModelScope.launch { container.settings.completeOnboarding() }
    fun createGroup(name: String, characterIds: List<String>, avatarPath: String? = null, context: GroupContext? = null, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching {
            val persona = container.userPersonas.ensureDefault()
            container.groups.create(name, characterIds, persona.id, avatarPath, GroupTurnMode.SMART)
        }.onSuccess { group ->
            context?.let { container.groups.saveContext(it.copy(groupId = group.id, title = it.title.ifBlank { group.name }, updatedAt = System.currentTimeMillis())) }
            refreshGroupMembers(group.id); onCreated(group.id)
        }
            .onFailure { mutableEvents.emit(it.message ?: "No se pudo crear el grupo.") }
    }
    fun toggleGroupPinned(id: String) = viewModelScope.launch { container.groups.togglePinned(id) }
    fun deleteGroup(id: String) = viewModelScope.launch { container.groups.delete(id) }
    fun updateGroup(group: GroupConversation) = viewModelScope.launch { container.groups.updateSettings(group) }
    fun groupContext(groupId: String) = container.groups.observeContext(groupId)
    fun saveGroupContext(context: GroupContext) = viewModelScope.launch {
        container.groups.saveContext(context.copy(updatedAt = System.currentTimeMillis()))
        mutableEvents.emit("Contexto del grupo guardado.")
    }
    fun saveParticipantContext(context: GroupParticipantContext) = viewModelScope.launch {
        container.groups.saveParticipantContext(context.copy(updatedAt = System.currentTimeMillis()))
    }
    fun renameGroup(id: String, name: String) = viewModelScope.launch { container.groups.rename(id, name) }
    fun removeGroupParticipant(groupId: String, characterId: String) = viewModelScope.launch { container.groups.removeParticipant(groupId, characterId) }
    fun setGroupParticipantEnabled(groupId: String, characterId: String, enabled: Boolean) = viewModelScope.launch { container.groups.setParticipantEnabled(groupId, characterId, enabled) }
    fun refreshGroupMembers(groupId: String) = viewModelScope.launch {
        val current = mutableGroupMembers.value.toMutableMap()
        current[groupId] = container.groups.getParticipants(groupId).mapNotNull { container.characters.getCharacter(it.characterId) }
        mutableGroupMembers.value = current
    }
    fun addGroupParticipant(groupId: String, characterId: String, position: Int) = viewModelScope.launch {
        container.groups.addParticipant(GroupParticipant(groupId, characterId, position))
        refreshGroupMembers(groupId)
    }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setTheme(mode) }
    fun setCatalogLanguage(language: CatalogLanguage) = viewModelScope.launch {
        container.settings.setCatalogLanguage(language)
    }
    fun setContentMode(mode: ContentMode, confirmed: Boolean = false) = viewModelScope.launch {
        container.settings.setContentMode(mode, confirmed)
    }
    fun confirmAdultContent() = viewModelScope.launch { container.settings.confirmAdultContent() }
    fun setTtsSettings(settings: TtsSettings) = viewModelScope.launch { container.settings.setTtsSettings(settings) }

    fun addVoiceRepository(manifestUrl: String) = viewModelScope.launch {
        val url = manifestUrl.trim()
        mutableVoiceOperation.value = "Comprobando repositorio…"
        runCatching {
            val synced = withContext(Dispatchers.IO) { container.voiceCatalog.fetch(url) }
            container.voiceRepositories.save(synced.repository)
            mutableRemoteVoices.value = (
                mutableRemoteVoices.value.filterNot { it.repositoryId == synced.repository.id } + synced.voices
            ).sortedWith(compareBy<RemoteVoice> { it.language }.thenBy { it.name })
            synced
        }.onSuccess { synced ->
            mutableEvents.emit("${synced.repository.name}: ${synced.voices.size} voces disponibles.")
        }.onFailure { mutableEvents.emit(it.message ?: "No se pudo leer el repositorio de voces.") }
        mutableVoiceOperation.value = null
    }

    fun syncVoiceRepositories() = viewModelScope.launch {
        mutableVoiceOperation.value = "Sincronizando voces…"
        val collected = mutableListOf<RemoteVoice>()
        var failures = 0
        voiceRepositories.value.filter { it.enabled }.forEach { repository ->
            runCatching { withContext(Dispatchers.IO) { container.voiceCatalog.fetch(repository.manifestUrl, repository) } }
                .onSuccess { synced ->
                    container.voiceRepositories.save(synced.repository)
                    collected += synced.voices
                }
                .onFailure { failures++ }
        }
        mutableRemoteVoices.value = collected.sortedWith(compareBy<RemoteVoice> { it.language }.thenBy { it.name })
        mutableVoiceOperation.value = null
        mutableEvents.emit("Voces actualizadas: ${collected.size}${if (failures > 0) " · $failures repositorios fallaron" else ""}.")
    }

    fun setVoiceRepositoryEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        val current = container.voiceRepositories.get(id) ?: return@launch
        container.voiceRepositories.save(current.copy(enabled = enabled))
        if (!enabled) mutableRemoteVoices.value = mutableRemoteVoices.value.filterNot { it.repositoryId == id }
        else syncVoiceRepositories()
    }

    fun deleteVoiceRepository(id: String) = viewModelScope.launch {
        container.voiceRepositories.delete(id)
        mutableRemoteVoices.value = mutableRemoteVoices.value.filterNot { it.repositoryId == id }
        mutableEvents.emit("Repositorio de voces eliminado. Las voces instaladas se conservaron.")
    }

    fun installVoice(remote: RemoteVoice) = viewModelScope.launch {
        val repository = container.voiceRepositories.get(remote.repositoryId) ?: return@launch
        mutableVoiceOperation.value = "Descargando ${remote.name}…"
        runCatching {
            val host = repository.manifestUrl.toHttpUrl().host
            container.voiceInstaller.install(remote, setOf(host))
        }.onSuccess { mutableEvents.emit("${remote.name} está instalada y disponible offline.") }
            .onFailure { mutableEvents.emit(it.message ?: "No se pudo instalar la voz.") }
        mutableVoiceOperation.value = null
    }

    fun deleteVoice(voice: VoiceModel) = viewModelScope.launch {
        container.ttsManager.stop()
        container.voices.delete(voice.id)
        withContext(Dispatchers.IO) { container.voiceInstaller.removeLocalFiles(voice) }
        mutableEvents.emit("Voz eliminada del dispositivo.")
    }

    fun testVoice(voice: VoiceModel) {
        val sample = if (voice.language.startsWith("es", ignoreCase = true)) {
            "Hola. Esta es una prueba de la voz seleccionada."
        } else "Hello. This is a test of the selected voice."
        container.ttsManager.speak(
            "voice-test", sample, voice, TtsSynthesisSettings(),
        )
    }

    fun testSystemVoice() {
        val language = catalogLanguage.value.code ?: java.util.Locale.getDefault().toLanguageTag()
        testVoice(TtsManager.systemVoice(language))
    }

    fun stopVoice() = container.ttsManager.stop()

    fun characterPreferences(characterId: String) = container.characterPreferences.observe(characterId)

    fun saveCharacterPreferences(preferences: CharacterPreferences) = viewModelScope.launch {
        container.characterPreferences.save(preferences.copy(updatedAt = System.currentTimeMillis()))
    }

    fun refreshAiProviderSummaries() = viewModelScope.launch {
        mutableProviderSummaries.value = withContext(Dispatchers.IO) { container.aiProviders.summaries() }
        mutableMonthlyAiSpend.value = withContext(Dispatchers.IO) { container.apiUsageTracker.monthlySpendUsd() }
    }

    fun saveAiCredential(providerId: String, apiKey: String) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { container.aiProviders.saveCredential(providerId, apiKey) } }
            .onSuccess {
                mutableEvents.emit("API Key guardada de forma cifrada.")
                refreshAiProviderSummaries()
            }
            .onFailure { mutableEvents.emit(it.message ?: "No se pudo guardar la API Key.") }
    }

    fun deleteAiCredential(providerId: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { container.aiProviders.deleteCredential(providerId) }
        mutableEvents.emit("Credencial eliminada.")
        refreshAiProviderSummaries()
    }

    fun testAiProvider(providerId: String) = viewModelScope.launch {
        mutableProviderOperation.value = "Probando conexión…"
        val result = withContext(Dispatchers.IO) { container.aiProviders.testConnection(providerId) }
        mutableProviderOperation.value = when (result) {
            is ProviderConnectionResult.Success -> "Conectado · ${result.modelCount ?: 0} modelos disponibles"
            is ProviderConnectionResult.Failure -> result.error.friendlyMessage
        }
        if (result is ProviderConnectionResult.Success) {
            runCatching { withContext(Dispatchers.IO) { container.aiProviders.models(providerId, forceRefresh = true) } }
        }
        refreshAiProviderSummaries()
    }

    fun refreshAiModels(providerId: String) = viewModelScope.launch {
        mutableProviderOperation.value = "Actualizando modelos…"
        runCatching { withContext(Dispatchers.IO) { container.aiProviders.models(providerId, forceRefresh = true) } }
            .onSuccess { mutableProviderOperation.value = "${it.size} modelos actualizados" }
            .onFailure { mutableProviderOperation.value = it.message ?: "No se pudo actualizar el catálogo." }
    }

    fun setGlobalAiModel(model: LlmModelInfo) = viewModelScope.launch {
        container.aiProviders.setGlobalSelection(ProviderModelSelection(model.providerId, model.modelId))
        mutableEvents.emit("Modelo predeterminado: ${model.displayName}")
        refreshAiProviderSummaries()
    }

    fun toggleFavoriteAiModel(model: LlmModelInfo) = viewModelScope.launch {
        container.aiProviders.toggleFavorite(model)
    }

    fun setCharacterAiModel(characterId: String, model: LlmModelInfo?) = viewModelScope.launch {
        container.aiProviders.setCharacterSelection(
            characterId,
            model?.let { ProviderModelSelection(it.providerId, it.modelId) },
        )
    }

    fun setCharacterLocalOnly(characterId: String, enabled: Boolean) = viewModelScope.launch {
        container.aiProviders.setCharacterLocalOnly(characterId, enabled)
    }

    fun saveCustomAiProvider(config: CustomAiProviderSettings, apiKey: String?) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { container.aiProviders.saveCustomProvider(config, apiKey) } }
            .onSuccess {
                mutableEvents.emit(if (config.baseUrl.startsWith("http://")) "Proveedor guardado. Conexión HTTP sin cifrar." else "Proveedor guardado.")
                refreshAiProviderSummaries()
            }
            .onFailure { mutableEvents.emit(it.message ?: "No se pudo guardar el proveedor.") }
    }

    fun deleteCustomAiProvider(providerId: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { container.aiProviders.deleteCustomProvider(providerId) }
        refreshAiProviderSummaries()
    }

    fun updateAiPreferences(
        preferFree: Boolean? = null,
        automaticFallback: Boolean? = null,
        fallbackChain: List<ProviderModelSelection>? = null,
        budget: AiBudgetSettings? = null,
    ) = viewModelScope.launch {
        container.settings.updateAiProviderSettings { current ->
            current.copy(
                preferFreeModels = preferFree ?: current.preferFreeModels,
                automaticFallback = automaticFallback ?: current.automaticFallback,
                fallbackChain = fallbackChain ?: current.fallbackChain,
                budget = budget ?: current.budget,
            )
        }
    }
    fun setGenerationSettings(settings: GenerationSettings) = viewModelScope.launch { container.settings.setGenerationSettings(settings) }
    fun setMemorySettings(settings: MemorySettings) = viewModelScope.launch { container.settings.setMemorySettings(settings) }
    fun setCatalogProviderEnabled(providerId: String, enabled: Boolean) = viewModelScope.launch {
        container.settings.setCatalogProviderEnabled(providerId, enabled)
        if (!enabled && mutableCatalog.value.selectedProviderId == providerId) selectCatalogProvider(LocalCharacterCatalogProvider.ID)
    }

    fun saveCustomRepository(id: String?, name: String, indexUrl: String) {
        val testedUrl = repositoryTestUrl
        val testedStatus = mutableRepositoryTestStatus.value
        val testedCount = repositoryTestCount
        viewModelScope.launch {
            val previous = id?.let { currentId -> customRepositories.value.firstOrNull { it.id == currentId } }
            val normalizedUrl = indexUrl.trim()
            val hasFreshTest = testedUrl == normalizedUrl && testedStatus != null && testedStatus != "Comprobando conexión…"
            val repository = CustomRepositorySettings(
                id = id ?: UUID.randomUUID().toString(),
                name = name.trim().take(80),
                indexUrl = normalizedUrl,
                enabled = previous?.enabled ?: true,
                lastStatus = when {
                    hasFreshTest && testedStatus!!.startsWith("Conexión correcta") -> "Disponible"
                    hasFreshTest -> testedStatus!!
                    previous?.indexUrl == normalizedUrl -> previous.lastStatus
                    else -> "Sin comprobar"
                },
                lastCheckedAt = when {
                    hasFreshTest -> System.currentTimeMillis()
                    previous?.indexUrl == normalizedUrl -> previous.lastCheckedAt
                    else -> null
                },
                characterCount = when {
                    hasFreshTest -> testedCount
                    previous?.indexUrl == normalizedUrl -> previous.characterCount
                    else -> null
                },
            )
            runCatching {
                require(repository.name.isNotBlank()) { "Escribe un nombre para el repositorio." }
                container.repositoryProvider(repository)
                container.settings.saveCustomRepository(repository)
            }.onSuccess {
                mutableEvents.emit("Repositorio guardado.")
            }.onFailure { mutableEvents.emit(it.message ?: "La URL del repositorio no es válida.") }
        }
    }

    fun deleteCustomRepository(id: String) = viewModelScope.launch {
        val providerId = AppContainer.customRepositoryProviderId(id)
        container.settings.deleteCustomRepository(id)
        if (mutableCatalog.value.selectedProviderId == providerId) selectCatalogProvider(LocalCharacterCatalogProvider.ID)
        mutableEvents.emit("Repositorio eliminado. Los personajes instalados se conservaron.")
    }

    fun setCustomRepositoryEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        container.settings.setCustomRepositoryEnabled(id, enabled)
    }

    fun testCustomRepository(name: String, indexUrl: String) = viewModelScope.launch {
        repositoryTestUrl = indexUrl.trim()
        repositoryTestCount = null
        mutableRepositoryTestStatus.value = "Comprobando conexión…"
        val draft = CustomRepositorySettings("connection-test", name.trim().ifBlank { "Repositorio" }, indexUrl.trim())
        runCatching {
            val page = container.repositoryProvider(draft).search(CatalogRequest(pageSize = 1))
            page.total ?: page.items.size
        }.onSuccess { count ->
            repositoryTestCount = count
            mutableRepositoryTestStatus.value = "Conexión correcta · $count personajes"
        }.onFailure { error ->
            mutableRepositoryTestStatus.value = "Error: ${error.message ?: "no se pudo leer repository.json"}"
        }
    }

    fun syncCustomRepository(id: String) = viewModelScope.launch {
        val repository = customRepositories.value.firstOrNull { it.id == id } ?: return@launch
        mutableEvents.emit("Sincronizando ${repository.name}…")
        runCatching {
            val page = container.repositoryProvider(repository).search(CatalogRequest(pageSize = 1))
            page.total ?: page.items.size
        }.onSuccess { count ->
            container.settings.updateCustomRepositoryHealth(id, "Disponible", count, System.currentTimeMillis())
            mutableEvents.emit("${repository.name}: $count personajes disponibles.")
        }.onFailure { error ->
            val status = "Error: ${error.message ?: "sin conexión"}"
            container.settings.updateCustomRepositoryHealth(id, status, null, System.currentTimeMillis())
            mutableEvents.emit(status)
        }
    }

    fun clearRepositoryTestStatus() {
        mutableRepositoryTestStatus.value = null
        repositoryTestUrl = null
        repositoryTestCount = null
    }

    fun startCatalog() {
        if (catalogStarted) return
        catalogStarted = true
        catalogSearchJob = viewModelScope.launch {
            val enabled = enabledCatalogProviderIds.value
            mutableCatalog.value = mutableCatalog.value.copy(health = container.catalogs.health(enabled))
            loadCatalogPage(reset = true, cursor = null)
        }
    }

    fun setCatalogQuery(query: String) {
        mutableCatalog.value = mutableCatalog.value.copy(query = query.take(160))
        scheduleCatalogSearch()
    }

    fun selectCatalogProvider(providerId: String) {
        if (providerId == mutableCatalog.value.selectedProviderId) return
        mutableCatalog.value = mutableCatalog.value.copy(selectedProviderId = providerId, items = emptyList(), nextCursor = null, error = null)
        scheduleCatalogSearch(delayMillis = 0)
    }

    fun setCatalogSafeOnly(safeOnly: Boolean) {
        mutableCatalog.value = mutableCatalog.value.copy(safeOnly = safeOnly)
        scheduleCatalogSearch(delayMillis = 0)
    }

    fun setCatalogSort(sort: CatalogSort) {
        mutableCatalog.value = mutableCatalog.value.copy(sort = sort)
        scheduleCatalogSearch(delayMillis = 0)
    }

    fun toggleCatalogTag(tag: String) {
        val current = mutableCatalog.value
        val tags = if (tag in current.selectedTags) current.selectedTags - tag else current.selectedTags + tag
        mutableCatalog.value = current.copy(selectedTags = tags)
        scheduleCatalogSearch(delayMillis = 0)
    }

    fun preloadCatalogImage(url: String?) {
        if (url.isNullOrBlank()) return
        val application = getApplication<Application>()
        application.imageLoader.enqueue(
            ImageRequest.Builder(application).data(url).size(900, 900).build(),
        )
    }

    fun retryCatalog() = scheduleCatalogSearch(delayMillis = 0)

    fun loadMoreCatalog() {
        val state = mutableCatalog.value
        val cursor = state.nextCursor ?: return
        if (state.loading || state.loadingMore) return
        viewModelScope.launch { loadCatalogPage(reset = false, cursor = cursor) }
    }

    fun loadRemoteDetail(providerId: String, remoteId: String) {
        remoteDetailJob?.cancel()
        remoteDetailJob = viewModelScope.launch {
            mutableRemoteDetail.value = RemoteDetailUiState.Loading
            try {
                val detail = container.catalogs.detail(providerId, remoteId)
                val installed = container.characterSources.find(providerId, remoteId)?.characterId
                mutableRemoteDetail.value = RemoteDetailUiState.Ready(detail, installedCharacterId = installed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableRemoteDetail.value = RemoteDetailUiState.Error(error.message ?: "No se pudo abrir el personaje remoto.")
            }
        }
    }

    fun installRemoteCharacter(onInstalled: (String) -> Unit = {}) = viewModelScope.launch {
        val ready = mutableRemoteDetail.value as? RemoteDetailUiState.Ready ?: return@launch
        ready.installedCharacterId?.let { onInstalled(it); return@launch }
        mutableRemoteDetail.value = ready.copy(installing = true, installStep = "Descargando tarjeta original y avatar…")
        runCatching {
            val provider = container.catalogs.provider(ready.detail.summary.providerId)
            withContext(Dispatchers.IO) { container.characterInstaller.install(provider, ready.detail) }
        }.onSuccess { result ->
            mutableRemoteDetail.value = ready.copy(installedCharacterId = result.characterId, installing = false, installStep = null)
            val catalogState = mutableCatalog.value
            mutableCatalog.value = catalogState.copy(
                items = catalogState.items.map { item ->
                    if (item.providerId == ready.detail.summary.providerId && item.remoteId == ready.detail.summary.remoteId) {
                        item.copy(installedCharacterId = result.characterId)
                    } else item
                },
            )
            val suffix = result.avatarWarning?.let { " La tarjeta quedó instalada; el avatar usa el PNG original." }.orEmpty()
            mutableEvents.emit("${ready.detail.summary.name} ya está disponible sin conexión.$suffix")
            onInstalled(result.characterId)
        }.onFailure {
            mutableRemoteDetail.value = ready.copy(installing = false, installStep = null)
            mutableEvents.emit(it.message ?: "No se pudo instalar la tarjeta.")
        }
    }

    private fun scheduleCatalogSearch(delayMillis: Long = 450) {
        catalogSearchJob?.cancel()
        catalogSearchJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            loadCatalogPage(reset = true, cursor = null)
        }
    }

    private suspend fun loadCatalogPage(reset: Boolean, cursor: String?) {
        val before = mutableCatalog.value
        val enabledProviders = enabledCatalogProviderIds.value
        if (before.selectedProviderId == ALL_PROVIDERS_ID) {
            if (reset) loadAllCatalogsProgressively(before, enabledProviders)
            return
        }
        val provider = before.providers.firstOrNull { it.id == before.selectedProviderId } ?: return
        if (before.selectedProviderId !in enabledProviders) {
            mutableCatalog.value = before.copy(items = emptyList(), nextCursor = null, loading = false, loadingMore = false, error = "Este proveedor está deshabilitado en Ajustes.")
            return
        }
        if (provider.availability == ProviderAvailability.UNSUPPORTED) {
            mutableCatalog.value = before.copy(items = emptyList(), nextCursor = null, loading = false, loadingMore = false, error = provider.statusMessage)
            return
        }
        mutableCatalog.value = before.copy(
            items = if (reset) emptyList() else before.items,
            loading = reset,
            loadingMore = !reset,
            error = null,
        )
        val request = CatalogRequest(
            query = before.query,
            cursor = cursor,
            safeOnly = before.safeOnly || contentMode.value == ContentMode.STANDARD,
            sort = before.sort,
            tags = before.selectedTags,
            language = before.language.code,
        )
        try {
            val rawPage = container.catalogs.page(before.selectedProviderId, request)
            val page = rawPage.copy(items = markInstalled(rawPage.items))
            val current = mutableCatalog.value
            if (
                current.selectedProviderId != before.selectedProviderId ||
                current.query != before.query ||
                current.safeOnly != before.safeOnly ||
                current.sort != before.sort ||
                current.selectedTags != before.selectedTags ||
                current.language != before.language
            ) return
            mutableCatalog.value = current.copy(
                items = if (reset) page.items else (current.items + page.items).distinctBy { it.providerId to it.remoteId },
                loading = false,
                loadingMore = false,
                nextCursor = page.nextCursor,
                total = page.total,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val current = mutableCatalog.value
            mutableCatalog.value = current.copy(loading = false, loadingMore = false, error = error.message ?: "No se pudo consultar el catálogo.")
        }
    }

    private suspend fun loadAllCatalogsProgressively(before: ExploreCatalogUiState, enabledProviders: Set<String>) {
        val providerIds = before.providers
            .filter { it.availability != ProviderAvailability.UNSUPPORTED && it.capabilities.search && it.id in enabledProviders }
            .map { it.id }
            .toSet()
        if (providerIds.isEmpty()) return
        mutableCatalog.value = before.copy(items = emptyList(), loading = true, loadingMore = false, nextCursor = null, error = null)
        val pages = linkedMapOf<String, List<com.localcharacter.app.domain.character.RemoteCharacterSummary>>()
        val errors = linkedMapOf<String, String>()
        var completed = 0
        val request = CatalogRequest(
            query = before.query,
            safeOnly = before.safeOnly || contentMode.value == ContentMode.STANDARD,
            sort = before.sort,
            tags = before.selectedTags,
            language = before.language.code,
        )
        container.catalogs.searchProgressively(providerIds, request).collect { result ->
            if (result.loading) return@collect
            completed++
            result.page?.let { pages[result.provider.id] = markInstalled(it.items) }
            result.error?.let { errors[result.provider.displayName] = it }
            val current = mutableCatalog.value
            if (
                current.selectedProviderId != ALL_PROVIDERS_ID || current.query != before.query ||
                current.safeOnly != before.safeOnly || current.sort != before.sort || current.selectedTags != before.selectedTags ||
                current.language != before.language
            ) return@collect
            mutableCatalog.value = current.copy(
                items = pages.values.flatten(),
                loading = completed < providerIds.size,
                total = pages.values.sumOf { it.size },
                error = errors.takeIf { it.isNotEmpty() }?.entries?.joinToString(" · ") { "${it.key}: ${it.value}" },
            )
        }
    }

    private suspend fun markInstalled(
        items: List<com.localcharacter.app.domain.character.RemoteCharacterSummary>,
    ): List<com.localcharacter.app.domain.character.RemoteCharacterSummary> {
        if (items.isEmpty()) return items
        val installed = container.characterSources.all().associateBy { it.providerId to it.remoteId }
        return items.map { item ->
            val localId = installed[item.providerId to item.remoteId]?.characterId ?: item.installedCharacterId
            item.copy(installedCharacterId = localId)
        }
    }

    companion object {
        const val ALL_PROVIDERS_ID = "all"
        private const val MODEL_PROBE_TIMEOUT_MILLIS = 45_000L
    }

    fun saveCharacter(character: Character, onSaved: (String) -> Unit = {}) = viewModelScope.launch {
        container.characters.save(character.copy(updatedAt = System.currentTimeMillis()))
        onSaved(character.id)
    }

    fun saveCharacterWithAvatar(
        character: Character,
        selectedAvatar: Uri?,
        removeAvatar: Boolean,
        onSaved: (String) -> Unit = {},
    ) = viewModelScope.launch {
        var importedAvatar: String? = null
        runCatching {
            val previous = container.characters.getCharacter(character.id)
            val persistedAvatar = when {
                selectedAvatar != null -> container.characterAvatars.import(character.id, selectedAvatar).also {
                    importedAvatar = it
                }
                removeAvatar -> null
                else -> previous?.avatarUri ?: character.avatarUri
            }
            container.characters.save(
                character.copy(avatarUri = persistedAvatar, updatedAt = System.currentTimeMillis()),
            )
            if (previous?.avatarUri != persistedAvatar) {
                runCatching { container.characterAvatars.deleteManaged(previous?.avatarUri) }
            }
            onSaved(character.id)
        }.onFailure { error ->
            importedAvatar?.let { runCatching { container.characterAvatars.deleteManaged(it) } }
            mutableEvents.emit(error.message ?: "No se pudo guardar la foto del personaje.")
        }
    }

    fun saveUserPersonaWithAvatar(
        persona: UserPersona,
        selectedAvatar: Uri?,
        removeAvatar: Boolean,
        onSaved: () -> Unit = {},
    ) = viewModelScope.launch {
        var importedAvatar: String? = null
        runCatching {
            val previous = container.userPersonas.get(persona.id)
            val persistedAvatar = when {
                selectedAvatar != null -> container.characterAvatars.import("persona-${persona.id}", selectedAvatar).also {
                    importedAvatar = it
                }
                removeAvatar -> null
                else -> previous?.avatarUri ?: persona.avatarUri
            }
            val now = System.currentTimeMillis()
            container.userPersonas.save(
                persona.copy(
                    avatarUri = persistedAvatar,
                    createdAt = previous?.createdAt ?: persona.createdAt,
                    updatedAt = now,
                    isDefault = true,
                ),
            )
            if (previous?.avatarUri != persistedAvatar) {
                runCatching { container.characterAvatars.deleteManaged(previous?.avatarUri) }
            }
            onSaved()
        }.onFailure { error ->
            importedAvatar?.let { runCatching { container.characterAvatars.deleteManaged(it) } }
            mutableEvents.emit(error.message ?: "No se pudo guardar tu perfil.")
        }
    }

    fun deleteCharacter(character: Character, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        val source = container.characterSources.forCharacter(character.id)
        container.characters.delete(character)
        if (source != null) {
            try {
                withContext(Dispatchers.IO) { container.characterInstaller.removeLocalFiles(source) }
            } catch (_: Exception) {
                mutableEvents.emit("El personaje se eliminó, pero Android no pudo limpiar todos sus archivos.")
            }
        }
        runCatching { container.characterAvatars.deleteManaged(character.avatarUri) }
        onDeleted()
    }

    fun duplicateCharacter(character: Character, onSaved: (String) -> Unit = {}) {
        val copy = character.copy(
            id = UUID.randomUUID().toString(),
            name = "${character.name} (copia)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        saveCharacter(copy, onSaved)
    }

    fun importCharacter(uri: Uri, onImported: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching {
            persistReadPermission(uri)
            val name = displayName(uri) ?: "character.json"
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { stream -> stream.readAtMost(20 * 1024 * 1024 + 1) }
                    ?: error("No se pudo abrir el archivo.")
            }
            val imported = cardParser.parse(bytes, name)
            container.characters.save(imported.character)
            imported.lore.forEach { container.contextRepository.saveLore(it) }
            mutableEvents.emit("${imported.character.name} se importó correctamente.")
            onImported(imported.character.id)
        }.onFailure { mutableEvents.emit(it.message ?: "La tarjeta de personaje está dañada.") }
    }

    fun exportCharacter(characterId: String, destination: Uri) = viewModelScope.launch {
        runCatching {
            val character = container.characters.getCharacter(characterId) ?: error("Personaje no encontrado.")
            val lore = container.contextRepository.lore(characterId)
            val json = cardParser.exportJson(character, lore)
            withContext(Dispatchers.IO) {
                val output = resolver.openOutputStream(destination, "wt") ?: error("No se pudo crear el archivo.")
                output.use { it.write(json.encodeToByteArray()) }
            }
            mutableEvents.emit("Personaje exportado en formato Character Card V2.")
        }.onFailure { mutableEvents.emit(it.message ?: "No se pudo exportar el personaje.") }
    }

    fun addModel(uri: Uri, loadAfterImport: Boolean = true) = viewModelScope.launch {
        runCatching {
            persistReadPermission(uri)
            val name = displayName(uri) ?: "model.gguf"
            val size = querySize(uri)
            val metadata = withContext(Dispatchers.IO) {
                val input = resolver.openInputStream(uri) ?: error("No se pudo abrir el modelo.")
                input.use { stream -> ggufParser.parse(stream) }
            }
            val model = ModelDescriptor(
                id = UUID.randomUUID().toString(),
                displayName = metadata.name?.takeIf(String::isNotBlank) ?: name.removeSuffix(".gguf"),
                uri = uri.toString(),
                sizeBytes = size,
                architecture = metadata.architecture,
                quantization = metadata.quantization,
                contextSize = metadata.contextLength,
                tensorCount = metadata.tensorCount,
                parameterCount = metadata.parameterCount,
                tokenizer = metadata.tokenizer,
                embeddedChatTemplate = metadata.chatTemplate,
            )
            container.models.save(model)
            mutableEvents.emit("Modelo GGUF añadido. Se intentará cargarlo directamente desde su ubicación.")
            if (loadAfterImport) {
                loadModelInternal(model)
                container.aiProviders.setGlobalSelection(ProviderModelSelection("local", model.id))
                refreshAiProviderSummaries()
            }
        }.onFailure { error ->
            mutableEvents.emit(error.message ?: "Este archivo no parece ser un modelo GGUF válido.")
        }
    }

    fun loadModel(model: ModelDescriptor) = viewModelScope.launch {
        runCatching {
            loadModelInternal(model)
            container.aiProviders.setGlobalSelection(ProviderModelSelection("local", model.id))
            refreshAiProviderSummaries()
        }
            .onFailure { mutableEvents.emit(it.message ?: "El modelo no pudo cargarse.") }
    }

    fun deleteModel(model: ModelDescriptor) = viewModelScope.launch {
        if (model.isActive) container.llm.unloadModel()
        container.models.delete(model.id)
        withContext(Dispatchers.IO) { modelStorage.deleteIfOwned(model.uri) }
    }

    fun unloadModel() = viewModelScope.launch {
        container.llm.unloadModel()
        container.models.clearActive()
        mutableEvents.emit("Modelo descargado de la RAM.")
    }

    fun setModelChatTemplate(model: ModelDescriptor, mode: String, customTemplate: String?) = viewModelScope.launch {
        val normalizedCustom = customTemplate?.trim()?.takeIf(String::isNotBlank)
        if (mode == "CUSTOM" && normalizedCustom?.contains("{{prompt}}") != true) {
            mutableEvents.emit("La plantilla personalizada debe contener {{prompt}}.")
            return@launch
        }
        val updated = model.copy(chatTemplateMode = mode, customChatTemplate = normalizedCustom)
        container.models.save(updated)
        if (model.isActive) container.llm.configureChatTemplate(mode, normalizedCustom)
        mutableEvents.emit("Plantilla de chat actualizada.")
    }

    fun openChat(characterId: String, forceNew: Boolean = false, onReady: (String) -> Unit) = viewModelScope.launch {
        val character = container.characters.getCharacter(characterId) ?: return@launch
        val persona = container.userPersonas.ensureDefault()
        val existing = if (forceNew) null else container.chats.latestForCharacter(characterId)
        val conversation = when {
            existing == null -> container.chats.create(character, persona.id)
            existing.userPersonaId == null -> {
                val updated = existing.copy(userPersonaId = persona.id)
                container.chats.saveConversation(updated)
                updated
            }
            else -> existing
        }
        onReady(conversation.id)
    }

    fun deleteConversation(id: String) = viewModelScope.launch { container.chats.deleteConversation(id) }
    fun togglePinned(id: String) = viewModelScope.launch { container.chats.togglePinned(id) }

    private suspend fun loadModelInternal(model: ModelDescriptor) {
        val compatibility = container.device.compatibility(model)
        if (compatibility.level.name == "HIGH_RISK") mutableEvents.emit("Aviso: ${compatibility.message}")
        val configured = container.settings.generationSettings.first()
        val loadSettings = configured.copy(
            contextSize = ModelLoadPolicy.effectiveContextSize(configured.contextSize, model.contextSize),
            threads = ModelLoadPolicy.effectiveThreads(configured.threads, container.device.inspect().cpuCores),
            batchSize = ModelLoadPolicy.effectiveBatchSize(
                configured.batchSize.takeUnless { it <= 128 } ?: GenerationSettings.Balanced.batchSize,
            ),
        )
        // Loading replaces the only native context. Never leave the previous Room row checked
        // when the replacement fails or its generation probe times out.
        container.models.clearActive()
        container.llm.configureChatTemplate(model.chatTemplateMode, model.customChatTemplate)
        val ownedPath = modelStorage.ownedPath(model.uri)
        val directResult = if (ownedPath != null) {
            container.llm.loadModel(ownedPath, model.displayName, loadSettings)
        } else {
            loadFromDocumentUri(model, loadSettings)
        }
        directResult.exceptionOrNull()?.takeIf(::isUnsupportedArchitecture)?.let { nativeError ->
            val architecture = model.architecture?.let { " Arquitectura: $it." }.orEmpty()
            error("Este modelo GGUF utiliza una arquitectura que la versión actual de llama.cpp no puede cargar.$architecture ${nativeError.message.orEmpty()}".trim())
        }
        var storedModel = model
        if (directResult.isFailure && ownedPath == null && shouldPreparePrivateCopy(directResult.exceptionOrNull())) {
            mutableModelPreparation.value = "El proveedor no permite cargar el GGUF directamente. Preparando copia local…"
            mutableEvents.emit("Preparando una copia local compatible con llama.cpp. El archivo original no se modifica.")
            val copied = try {
                withContext(Dispatchers.IO) { modelStorage.createPrivateCopy(Uri.parse(model.uri), model) }
            } finally {
                mutableModelPreparation.value = null
            }
            val retry = container.llm.loadModel(copied.absolutePath, model.displayName, loadSettings)
            if (retry.isFailure) {
                withContext(Dispatchers.IO) { copied.delete() }
                val directMessage = directResult.exceptionOrNull()?.message.orEmpty()
                val retryMessage = retry.exceptionOrNull()?.message ?: "GGUF no compatible."
                error(listOf(directMessage, retryMessage).filter(String::isNotBlank).distinct().joinToString(" "))
            }
            storedModel = model.copy(uri = modelStorage.toStoredUri(copied))
            container.models.save(storedModel)
        } else {
            directResult.getOrThrow()
        }
        verifyLoadedModel(storedModel, loadSettings)
        container.models.activate(storedModel.id)
        mutableEvents.emit("${storedModel.displayName} está verificado y listo con contexto ${loadSettings.contextSize}.")
    }

    /** A successful mmap/open is not enough: prove that the selected GGUF can decode tokens. */
    private suspend fun verifyLoadedModel(model: ModelDescriptor, settings: GenerationSettings) {
        mutableModelPreparation.value = "Verificando que ${model.displayName} pueda generar texto…"
        try {
            val generated = withTimeout(MODEL_PROBE_TIMEOUT_MILLIS) {
                container.llmTasks.run(LlmTaskPriority.CHAT_GENERATION) {
                    container.llm.generate(
                        prompt = "Reply with OK.",
                        settings = settings.copy(maxTokens = 4, temperature = 0.2f, topK = 20),
                    ).toList().joinToString(separator = "")
                }
            }
            require(generated.isNotBlank()) {
                "El GGUF abrió sus pesos, pero terminó sin producir texto. Prueba la plantilla Auto o un modelo de texto instruct/chat."
            }
        } catch (error: TimeoutCancellationException) {
            container.llm.unloadModel()
            throw IllegalStateException(
                "${model.displayName} abrió sus pesos, pero no pudo generar texto en 45 segundos.",
                error,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            container.llm.unloadModel()
            throw IllegalStateException(
                "${model.displayName} abrió sus pesos, pero falló la prueba real de generación: ${error.message.orEmpty()}",
                error,
            )
        } finally {
            mutableModelPreparation.value = null
        }
    }

    private suspend fun loadFromDocumentUri(model: ModelDescriptor, settings: GenerationSettings): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.openFileDescriptor(Uri.parse(model.uri), "r")?.use { descriptor ->
                    container.llm.loadModel(
                        "/proc/self/fd/${descriptor.fd}", model.displayName, settings,
                    ).getOrThrow()
                } ?: error("Android no pudo volver a abrir el archivo del modelo.")
            }
        }

    private fun shouldPreparePrivateCopy(error: Throwable?): Boolean {
        val message = error?.message.orEmpty().lowercase()
        val definitiveModelErrors = listOf(
            "unsupported", "no compatible", "unknown model", "unknown architecture", "arquitectura desconocida",
            "invalid tensor", "truncated", "incompleto", "failed to allocate", "out of memory", "sin memoria",
        )
        return definitiveModelErrors.none(message::contains)
    }

    private fun isUnsupportedArchitecture(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return listOf("unknown architecture", "unknown model", "unsupported architecture", "arquitectura desconocida")
            .any(message::contains)
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun displayName(uri: Uri): String? = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun querySize(uri: Uri): Long = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
    } ?: 0L

    fun nativeVersion(): String = container.llm.nativeVersion()
}

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(limit.coerceAtMost(64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - total))
        if (read < 0) break
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}
