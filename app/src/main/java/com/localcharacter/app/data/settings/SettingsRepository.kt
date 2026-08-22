package com.localcharacter.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.localcharacter.app.domain.model.GenerationSettings
import com.localcharacter.app.domain.model.ContentMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MemoryLevel { MINIMAL, NORMAL, DETAILED }

enum class CatalogLanguage(val code: String?, val displayName: String, val promptName: String?) {
    SPANISH("es", "Español", "Spanish"),
    ENGLISH("en", "English", "English"),
    PORTUGUESE("pt", "Português", "Portuguese"),
    FRENCH("fr", "Français", "French"),
    GERMAN("de", "Deutsch", "German"),
    ITALIAN("it", "Italiano", "Italian"),
    RUSSIAN("ru", "Русский", "Russian"),
    JAPANESE("ja", "日本語", "Japanese"),
    KOREAN("ko", "한국어", "Korean"),
    CHINESE("zh", "中文", "Chinese"),
    POLISH("pl", "Polski", "Polish"),
    TURKISH("tr", "Türkçe", "Turkish"),
    ARABIC("ar", "العربية", "Arabic"),
    DUTCH("nl", "Nederlands", "Dutch"),
    SWEDISH("sv", "Svenska", "Swedish"),
    DANISH("da", "Dansk", "Danish"),
    NORWEGIAN("no", "Norsk", "Norwegian"),
    FINNISH("fi", "Suomi", "Finnish"),
    HUNGARIAN("hu", "Magyar", "Hungarian"),
    THAI("th", "ไทย", "Thai"),
    VIETNAMESE("vi", "Tiếng Việt", "Vietnamese"),
    INDONESIAN("id", "Bahasa Indonesia", "Indonesian"),
    ALL(null, "Todos", null),
}

data class MemorySettings(
    val intelligentMemory: Boolean = true,
    val automaticFollowUps: Boolean = true,
    val conversationSummaries: Boolean = true,
    val level: MemoryLevel = MemoryLevel.NORMAL,
    // Character beliefs and relationship continuity should follow the same character
    // and user persona into a new chat. Users can explicitly disable this in Settings.
    val shareAcrossChats: Boolean = true,
)

/**
 * Maps only the exact sampling tuples shipped by older builds to the stable 3B
 * defaults. Context, output, CPU and batching choices are preserved, and any
 * customized sampling value leaves the complete configuration untouched.
 */
object GenerationSettingsCompatibility {
    fun migrateLegacyDefaults(settings: GenerationSettings): GenerationSettings = when {
        settings.hasSampling(0.85f, 0.92f, 40, 0.05f, 1.08f) -> settings.copy(
            temperature = 0.45f,
            topP = 0.90f,
            topK = 40,
            minP = 0.05f,
            repeatPenalty = 1.08f,
        )
        settings.hasSampling(0.95f, 0.93f, 50, 0.05f, 1.12f) -> settings.copy(
            temperature = 0.60f,
            topP = 0.90f,
            topK = 40,
            minP = 0.05f,
            repeatPenalty = 1.08f,
        )
        else -> settings
    }

    private fun GenerationSettings.hasSampling(
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
    ): Boolean =
        this.temperature == temperature && this.topP == topP && this.topK == topK &&
            this.minP == minP && this.repeatPenalty == repeatPenalty
}

data class TtsSettings(
    val autoPlayResponses: Boolean = false,
    val systemFallback: Boolean = true,
    val unloadWhenIdle: Boolean = false,
)

@Serializable
data class CustomRepositorySettings(
    val id: String,
    val name: String,
    val indexUrl: String,
    val enabled: Boolean = true,
    val lastStatus: String = "Sin comprobar",
    val lastCheckedAt: Long? = null,
    val characterCount: Int? = null,
)

object CustomRepositoryCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(value: String?): List<CustomRepositorySettings> = value
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { json.decodeFromString<List<CustomRepositorySettings>>(it) }.getOrDefault(emptyList()) }
        ?: emptyList()
    fun encode(value: List<CustomRepositorySettings>): String = json.encodeToString(value)
}

object CustomRepositoryState {
    fun setEnabled(items: List<CustomRepositorySettings>, id: String, enabled: Boolean) =
        items.map { if (it.id == id) it.copy(enabled = enabled) else it }
}

private val Context.dataStore by preferencesDataStore("local_character_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val temperature = floatPreferencesKey("generation_temperature")
        val topP = floatPreferencesKey("generation_top_p")
        val topK = intPreferencesKey("generation_top_k")
        val minP = floatPreferencesKey("generation_min_p")
        val repeatPenalty = floatPreferencesKey("generation_repeat_penalty")
        val contextSize = intPreferencesKey("generation_context_size")
        val maxTokens = intPreferencesKey("generation_max_tokens")
        val threads = intPreferencesKey("generation_threads")
        val batchSize = intPreferencesKey("generation_batch_size")
        val intelligentMemory = booleanPreferencesKey("memory_intelligent")
        val automaticFollowUps = booleanPreferencesKey("memory_follow_ups")
        val conversationSummaries = booleanPreferencesKey("memory_summaries")
        val memoryLevel = stringPreferencesKey("memory_level")
        val shareAcrossChats = booleanPreferencesKey("memory_share_across_chats")
        val enabledCatalogProviders = stringPreferencesKey("enabled_catalog_providers")
        val customRepositories = stringPreferencesKey("custom_character_repositories")
        val catalogLanguage = stringPreferencesKey("catalog_language")
        val aiProviderSettings = stringPreferencesKey("ai_provider_settings")
        val contentMode = stringPreferencesKey("conversation_content_mode")
        val adultContentConfirmed = booleanPreferencesKey("adult_content_confirmed")
        val ttsAutoPlay = booleanPreferencesKey("tts_auto_play")
        val ttsSystemFallback = booleanPreferencesKey("tts_system_fallback")
        val ttsUnloadWhenIdle = booleanPreferencesKey("tts_unload_when_idle")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        runCatching { ThemeMode.valueOf(preferences[Keys.theme] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboardingComplete] ?: false }
    val catalogLanguage: Flow<CatalogLanguage> = context.dataStore.data.map { preferences ->
        runCatching {
            CatalogLanguage.valueOf(preferences[Keys.catalogLanguage] ?: CatalogLanguage.SPANISH.name)
        }.getOrDefault(CatalogLanguage.SPANISH)
    }
    val generationSettings: Flow<GenerationSettings> = context.dataStore.data.map { preferences ->
        val defaults = GenerationSettings.Balanced
        GenerationSettingsCompatibility.migrateLegacyDefaults(GenerationSettings(
            temperature = preferences[Keys.temperature] ?: defaults.temperature,
            topP = preferences[Keys.topP] ?: defaults.topP,
            topK = preferences[Keys.topK] ?: defaults.topK,
            minP = preferences[Keys.minP] ?: defaults.minP,
            repeatPenalty = preferences[Keys.repeatPenalty] ?: defaults.repeatPenalty,
            contextSize = preferences[Keys.contextSize] ?: defaults.contextSize,
            maxTokens = preferences[Keys.maxTokens] ?: defaults.maxTokens,
            threads = preferences[Keys.threads] ?: defaults.threads,
            batchSize = preferences[Keys.batchSize] ?: defaults.batchSize,
        ))
    }
    val memorySettings: Flow<MemorySettings> = context.dataStore.data.map { preferences ->
        MemorySettings(
            intelligentMemory = preferences[Keys.intelligentMemory] ?: true,
            automaticFollowUps = preferences[Keys.automaticFollowUps] ?: true,
            conversationSummaries = preferences[Keys.conversationSummaries] ?: true,
            level = runCatching {
                MemoryLevel.valueOf(preferences[Keys.memoryLevel] ?: MemoryLevel.NORMAL.name)
            }.getOrDefault(MemoryLevel.NORMAL),
            shareAcrossChats = preferences[Keys.shareAcrossChats] ?: true,
        )
    }
    val enabledCatalogProviders: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[Keys.enabledCatalogProviders]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.toSet()
            ?: setOf("ai_character_cards", "local")
    }
    val customRepositories: Flow<List<CustomRepositorySettings>> = context.dataStore.data.map { preferences ->
        CustomRepositoryCodec.decode(preferences[Keys.customRepositories])
    }
    val aiProviderSettings: Flow<AiProviderSettings> = context.dataStore.data.map { preferences ->
        AiProviderSettingsCodec.decode(preferences[Keys.aiProviderSettings])
    }
    val contentMode: Flow<ContentMode> = context.dataStore.data.map { preferences ->
        runCatching { ContentMode.valueOf(preferences[Keys.contentMode] ?: ContentMode.STANDARD.name) }
            .getOrDefault(ContentMode.STANDARD)
    }
    val adultContentConfirmed: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.adultContentConfirmed] ?: false
    }
    val ttsSettings: Flow<TtsSettings> = context.dataStore.data.map {
        TtsSettings(
            autoPlayResponses = it[Keys.ttsAutoPlay] ?: false,
            systemFallback = it[Keys.ttsSystemFallback] ?: true,
            unloadWhenIdle = it[Keys.ttsUnloadWhenIdle] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setCatalogLanguage(language: CatalogLanguage) = context.dataStore.edit {
        it[Keys.catalogLanguage] = language.name
    }
    suspend fun setContentMode(mode: ContentMode, confirmed: Boolean = false) = context.dataStore.edit {
        it[Keys.contentMode] = mode.name
        if (confirmed) it[Keys.adultContentConfirmed] = true
    }
    suspend fun confirmAdultContent() = context.dataStore.edit { it[Keys.adultContentConfirmed] = true }
    suspend fun setTtsSettings(settings: TtsSettings) = context.dataStore.edit {
        it[Keys.ttsAutoPlay] = settings.autoPlayResponses
        it[Keys.ttsSystemFallback] = settings.systemFallback
        it[Keys.ttsUnloadWhenIdle] = settings.unloadWhenIdle
    }
    suspend fun completeOnboarding() = context.dataStore.edit { it[Keys.onboardingComplete] = true }
    suspend fun setGenerationSettings(settings: GenerationSettings) = context.dataStore.edit { preferences ->
        preferences[Keys.temperature] = settings.temperature
        preferences[Keys.topP] = settings.topP
        preferences[Keys.topK] = settings.topK
        preferences[Keys.minP] = settings.minP
        preferences[Keys.repeatPenalty] = settings.repeatPenalty
        preferences[Keys.contextSize] = settings.contextSize
        preferences[Keys.maxTokens] = settings.maxTokens
        preferences[Keys.threads] = settings.threads
        preferences[Keys.batchSize] = settings.batchSize
    }

    suspend fun setMemorySettings(settings: MemorySettings) = context.dataStore.edit { preferences ->
        preferences[Keys.intelligentMemory] = settings.intelligentMemory
        preferences[Keys.automaticFollowUps] = settings.automaticFollowUps
        preferences[Keys.conversationSummaries] = settings.conversationSummaries
        preferences[Keys.memoryLevel] = settings.level.name
        preferences[Keys.shareAcrossChats] = settings.shareAcrossChats
    }

    suspend fun setCatalogProviderEnabled(providerId: String, enabled: Boolean) = context.dataStore.edit { preferences ->
        val current = preferences[Keys.enabledCatalogProviders]
            ?.split(',')?.filter(String::isNotBlank)?.toMutableSet()
            ?: mutableSetOf("ai_character_cards", "local")
        if (enabled) current += providerId else current -= providerId
        current += "local"
        preferences[Keys.enabledCatalogProviders] = current.sorted().joinToString(",")
    }

    suspend fun saveCustomRepository(repository: CustomRepositorySettings) = context.dataStore.edit { preferences ->
        val current = CustomRepositoryCodec.decode(preferences[Keys.customRepositories]).toMutableList()
        val index = current.indexOfFirst { it.id == repository.id }
        if (index >= 0) current[index] = repository else current += repository
        preferences[Keys.customRepositories] = CustomRepositoryCodec.encode(current.sortedBy { it.name.lowercase() })
    }

    suspend fun deleteCustomRepository(id: String) = context.dataStore.edit { preferences ->
        val current = CustomRepositoryCodec.decode(preferences[Keys.customRepositories]).filterNot { it.id == id }
        preferences[Keys.customRepositories] = CustomRepositoryCodec.encode(current)
    }

    suspend fun setCustomRepositoryEnabled(id: String, enabled: Boolean) = context.dataStore.edit { preferences ->
        val current = CustomRepositoryState.setEnabled(
            CustomRepositoryCodec.decode(preferences[Keys.customRepositories]), id, enabled,
        )
        preferences[Keys.customRepositories] = CustomRepositoryCodec.encode(current)
    }

    suspend fun updateCustomRepositoryHealth(id: String, status: String, count: Int?, checkedAt: Long) =
        context.dataStore.edit { preferences ->
            val current = CustomRepositoryCodec.decode(preferences[Keys.customRepositories])
                .map { if (it.id == id) it.copy(lastStatus = status, characterCount = count, lastCheckedAt = checkedAt) else it }
            preferences[Keys.customRepositories] = CustomRepositoryCodec.encode(current)
        }

    suspend fun updateAiProviderSettings(transform: (AiProviderSettings) -> AiProviderSettings) =
        context.dataStore.edit { preferences ->
            val current = AiProviderSettingsCodec.decode(preferences[Keys.aiProviderSettings])
            preferences[Keys.aiProviderSettings] = AiProviderSettingsCodec.encode(transform(current))
        }
}
