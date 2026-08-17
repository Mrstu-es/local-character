package com.localcharacter.app

import android.content.Context
import androidx.room.Room
import com.localcharacter.app.data.database.AppDatabase
import com.localcharacter.app.data.database.MIGRATION_1_2
import com.localcharacter.app.data.database.MIGRATION_2_3
import com.localcharacter.app.data.database.MIGRATION_3_4
import com.localcharacter.app.data.database.MIGRATION_4_5
import com.localcharacter.app.data.database.MIGRATION_5_6
import com.localcharacter.app.data.database.MIGRATION_6_7
import com.localcharacter.app.data.database.MIGRATION_7_8
import com.localcharacter.app.data.catalog.AiCharacterCardsProvider
import com.localcharacter.app.data.catalog.CharacterCatalogManager
import com.localcharacter.app.data.catalog.CharacterInstaller
import com.localcharacter.app.data.catalog.LocalCharacterCatalogProvider
import com.localcharacter.app.data.catalog.GenericRepositoryProvider
import com.localcharacter.app.data.catalog.RoomCharacterInstallStore
import com.localcharacter.app.data.catalog.SecureCatalogHttpClient
import com.localcharacter.app.data.charactercard.CharacterCardParser
import com.localcharacter.app.data.character.CharacterAvatarStore
import com.localcharacter.app.data.repository.CharacterRepository
import com.localcharacter.app.data.repository.ChatRepository
import com.localcharacter.app.data.repository.GroupRepository
import com.localcharacter.app.data.repository.ContextRepository
import com.localcharacter.app.data.repository.MemoryRepository
import com.localcharacter.app.data.repository.ModelRepository
import com.localcharacter.app.data.repository.PendingEventRepository
import com.localcharacter.app.data.repository.RelationshipRepository
import com.localcharacter.app.data.repository.SummaryRepository
import com.localcharacter.app.data.repository.AiUsageRepository
import com.localcharacter.app.data.repository.CharacterPreferencesRepository
import com.localcharacter.app.data.repository.UserPersonaRepository
import com.localcharacter.app.data.repository.VoiceModelsRepository
import com.localcharacter.app.data.repository.VoiceRepositoryRepository
import com.localcharacter.app.data.settings.SettingsRepository
import com.localcharacter.app.data.settings.CustomRepositorySettings
import com.localcharacter.app.device.DeviceCapabilityManager
import com.localcharacter.app.llm.LlamaCppEngine
import com.localcharacter.app.llm.LlmTaskQueue
import com.localcharacter.app.llm.provider.AiProviderManager
import com.localcharacter.app.llm.provider.ApiUsageTracker
import com.localcharacter.app.llm.provider.LocalLlamaProvider
import com.localcharacter.app.security.ApiCredentialStore
import com.localcharacter.app.domain.memory.ConversationSummarizer
import com.localcharacter.app.domain.memory.MemoryExtractionService
import com.localcharacter.app.domain.memory.MemoryOrchestrator
import com.localcharacter.app.domain.character.ProviderAvailability
import com.localcharacter.app.domain.character.ProviderCapabilities
import com.localcharacter.app.domain.character.ProviderDescriptor
import com.localcharacter.app.domain.character.UnsupportedCharacterProvider
import com.localcharacter.app.tts.TtsManager
import com.localcharacter.app.data.voice.RoomVoiceInstallStore
import com.localcharacter.app.data.voice.VoiceCatalogClient
import com.localcharacter.app.data.voice.VoiceInstaller
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(appContext: Context) {
    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "local_character.db")
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .build()
    val characters = CharacterRepository(database.characterDao())
    val characterAvatars = CharacterAvatarStore(appContext.applicationContext)
    val characterSources = com.localcharacter.app.data.repository.CharacterSourceRepository(database.characterSourceDao())
    val chats = ChatRepository(database.conversationDao(), database.messageDao())
    val groups = GroupRepository(
        database.groupConversationDao(), database.groupParticipantDao(),
        database.groupMessageDao(), database.groupMemoryDao(), database.groupContextDao(), database.groupParticipantContextDao(),
    )
    val contextRepository = ContextRepository(database.loreDao())
    val memories = MemoryRepository(database.memoryDao())
    val summaries = SummaryRepository(database.conversationSummaryDao())
    val relationships = RelationshipRepository(database.characterRelationshipDao())
    val pendingEvents = PendingEventRepository(database.pendingEventDao())
    val models = ModelRepository(database.modelDao())
    val aiUsage = AiUsageRepository(database.aiUsageDao())
    val userPersonas = UserPersonaRepository(database.userPersonaDao())
    val voiceRepositories = VoiceRepositoryRepository(database.voiceRepositoryDao())
    val voices = VoiceModelsRepository(database.voiceDao())
    val characterPreferences = CharacterPreferencesRepository(database.characterPreferencesDao())
    val ttsManager = TtsManager(appContext.applicationContext)
    val voiceCatalog = VoiceCatalogClient()
    val voiceInstaller = VoiceInstaller(
        java.io.File(appContext.filesDir, "voices"), RoomVoiceInstallStore(database),
    )
    val settings = SettingsRepository(appContext.applicationContext)
    val device = DeviceCapabilityManager(appContext.applicationContext)
    val llm = LlamaCppEngine(appContext.applicationInfo.nativeLibraryDir)
    val llmTasks = LlmTaskQueue(llm)
    val apiCredentials = ApiCredentialStore(appContext.applicationContext)
    private val aiHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Streaming providers decide completion; first-token and total deadlines live above HTTP.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
    val localLlmProvider = LocalLlamaProvider(llm, models::active)
    val aiProviders = AiProviderManager(settings, apiCredentials, localLlmProvider, aiHttpClient)
    val apiUsageTracker = ApiUsageTracker(aiUsage, settings)
    val memoryExtraction = MemoryExtractionService(llm, llmTasks)
    val conversationSummarizer = ConversationSummarizer(llm, llmTasks)
    val memoryOrchestrator = MemoryOrchestrator(
        memories, summaries, relationships, pendingEvents, chats, memoryExtraction, conversationSummarizer,
    )

    private val catalogHttp = SecureCatalogHttpClient()
    private fun unsupported(id: String, name: String, reason: String) = UnsupportedCharacterProvider(
        ProviderDescriptor(
            id, name, ProviderAvailability.UNSUPPORTED, reason,
            ProviderCapabilities(false, false, false, false, false),
        ),
    )
    val catalogs = CharacterCatalogManager(
        listOf(
            AiCharacterCardsProvider(catalogHttp),
            LocalCharacterCatalogProvider(characters),
            unsupported(
                "chub", "Chub / CharacterHub",
                "La API oficial bloquea esta región (HTTP 403). No se usa scraping ni endpoints antiguos.",
            ),
            unsupported(
                "pygmalion", "Pygmalion",
                "No se verificó una API pública y estable para catálogo y descarga sin cuenta.",
            ),
            unsupported(
                "character_tavern", "Character Tavern",
                "El sitio permite descargar, pero no publica un contrato API estable para terceros.",
            ),
        ),
    )

    fun repositoryProvider(repository: CustomRepositorySettings): GenericRepositoryProvider {
        val parsed = repository.indexUrl.toHttpUrl()
        require(parsed.isHttps) { "La URL del repositorio debe usar HTTPS." }
        val providerId = customRepositoryProviderId(repository.id)
        return GenericRepositoryProvider(
            http = catalogHttp,
            indexUrl = parsed.toString(),
            allowedHosts = setOf(parsed.host),
            descriptor = ProviderDescriptor(
                id = providerId,
                displayName = repository.name,
                availability = if (repository.lastStatus.startsWith("Error")) ProviderAvailability.DEGRADED else ProviderAvailability.AVAILABLE,
                statusMessage = repository.lastStatus,
                capabilities = ProviderCapabilities(true, true, true, true, true),
            ),
        )
    }

    fun replaceCustomRepositories(repositories: List<CustomRepositorySettings>) {
        catalogs.replaceCustomProviders(
            repositories.filter { it.enabled }.mapNotNull { runCatching { repositoryProvider(it) }.getOrNull() },
        )
    }

    companion object {
        fun customRepositoryProviderId(id: String) = "repository_$id"
    }
    val characterInstaller = CharacterInstaller(
        java.io.File(appContext.filesDir, "characters"),
        CharacterCardParser(),
        RoomCharacterInstallStore(database),
    )
}
