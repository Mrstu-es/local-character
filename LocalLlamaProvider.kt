package com.localcharacter.app.llm.provider

import com.localcharacter.app.domain.model.ModelDescriptor
import com.localcharacter.app.llm.GenerationPurpose
import com.localcharacter.app.llm.LlmEngine
import com.localcharacter.app.llm.LlmState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class LocalLlamaProvider(
    private val engine: LlmEngine,
    private val activeModel: suspend () -> ModelDescriptor?,
) : LlmProvider {
    override val providerId = LOCAL_PROVIDER_ID
    override val displayName = "Local GGUF"
    override val capabilities = ProviderCatalog.builtIn(providerId)!!.capabilities
    override val pricingType = PricingType.LOCAL

    override suspend fun testConnection(): ProviderConnectionResult = when (engine.state.value) {
        is LlmState.ModelReady, is LlmState.Generating, is LlmState.InternalWork -> ProviderConnectionResult.Success(1)
        else -> ProviderConnectionResult.Failure(
            ProviderError(ProviderErrorKind.MODEL_NOT_FOUND, "Carga un modelo GGUF local antes de conversar."),
        )
    }

    override suspend fun getModels(): List<LlmModelInfo> = activeModel()?.let { model ->
        listOf(
            LlmModelInfo(
                providerId, model.id, model.displayName, PricingType.LOCAL,
                contextLength = model.contextSize,
                supportsStreaming = true,
                description = listOfNotNull(model.architecture, model.quantization).joinToString(" · "),
            ),
        )
    }.orEmpty()

    override fun generate(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val prompt = request.localFormattedPrompt ?: buildString {
            append(request.systemPrompt)
            request.messages.forEach { append("\n\n${it.role.name}: ${it.content}") }
        }
        val settings = (request.localGenerationSettings ?: com.localcharacter.app.domain.model.GenerationSettings()).copy(
            temperature = request.temperature ?: request.localGenerationSettings?.temperature ?: 0.85f,
            topP = request.topP ?: request.localGenerationSettings?.topP ?: 0.92f,
            topK = request.topK ?: request.localGenerationSettings?.topK ?: 40,
            maxTokens = request.maxTokens,
        )
        engine.generate(prompt, settings, GenerationPurpose.CHAT_GENERATION).collect {
            emit(LlmStreamEvent.TextDelta(it))
        }
        emit(LlmStreamEvent.Completed)
    }.catch {
        if (it is CancellationException) throw it
        emit(LlmStreamEvent.Error(com.localcharacter.app.llm.provider.network.ProviderErrorMapper.throwable(it)))
    }

    override suspend fun cancelGeneration() = engine.stopGeneration()
}
