package com.localcharacter.app.llm

import com.localcharacter.app.domain.model.GenerationSettings
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class LlamaCppEngine(private val nativeLibraryDir: String) : LlmEngine {
    private val mutableState = MutableStateFlow<LlmState>(LlmState.NoModelLoaded)
    override val state: StateFlow<LlmState> = mutableState.asStateFlow()
    private var modelName: String? = null
    private val generating = AtomicBoolean(false)
    private val generationSequence = AtomicLong(0)
    private val activeGeneration = AtomicLong(0)
    @Volatile private var chatTemplateMode = "AUTO"
    @Volatile private var customChatTemplate: String? = null
    private val inferenceDispatcher = Executors.newSingleThreadExecutor { work ->
        // OpenMP workers inherit this priority. A background-priority parent became
        // nice 10 on Xiaomi/HyperOS and made even a 0.6B model take minutes to prefill.
        Thread(work, "local-llama-inference").apply { priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()

    override suspend fun loadModel(path: String, displayName: String, settings: GenerationSettings): Result<String> = withContext(Dispatchers.IO) {
        mutableState.value = LlmState.LoadingModel(displayName)
        runCatching {
            if (modelName != null) LlamaBridge.unloadModel()
            val details = LlamaBridge.loadModel(
                path, nativeLibraryDir, settings.contextSize, settings.threads, settings.batchSize,
            )
            modelName = displayName
            mutableState.value = LlmState.ModelReady(displayName, details)
            details
        }.onFailure { error ->
            modelName = null
            mutableState.value = LlmState.Error("El modelo no pudo cargarse.", error.message)
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (generating.get()) LlamaBridge.stopGeneration()
        LlamaBridge.unloadModel()
        modelName = null
        mutableState.value = LlmState.NoModelLoaded
    }

    override fun configureChatTemplate(mode: String, customTemplate: String?) {
        // Configuration can be changed from UI without ever waiting on the native generation mutex.
        chatTemplateMode = mode
        customChatTemplate = customTemplate
    }

    override fun generate(prompt: String, settings: GenerationSettings, purpose: GenerationPurpose): Flow<String> = callbackFlow {
        val name = modelName
        if (name == null) {
            close(IllegalStateException("No hay ningún modelo cargado."))
            return@callbackFlow
        }
        val generationId = generationSequence.incrementAndGet()
        activeGeneration.set(generationId)
        generating.set(true)
        mutableState.value = if (purpose == GenerationPurpose.CHAT_GENERATION) {
            LlmState.Generating(name)
        } else {
            LlmState.InternalWork(name, purpose)
        }
        // JNI blocks until generation finishes; a dedicated worker keeps Default free for UI state work.
        val work = launch(inferenceDispatcher) {
            val result = runCatching {
                LlamaBridge.setChatTemplate(chatTemplateMode, customChatTemplate)
                LlamaBridge.generate(
                    prompt = prompt,
                    maxTokens = settings.maxTokens,
                    temperature = settings.temperature,
                    topP = settings.topP,
                    topK = settings.topK,
                    minP = settings.minP,
                    repeatPenalty = settings.repeatPenalty,
                    seed = (System.nanoTime() and 0x7fffffff).toInt(),
                    callback = NativeTokenCallback { trySend(it) },
                )
            }
            result.onFailure { error ->
                if (activeGeneration.get() == generationId) {
                    mutableState.value = LlmState.Error("No se pudo generar la respuesta.", error.message)
                }
            }
            result.fold(onSuccess = { close() }, onFailure = { close(it) })
        }
        work.invokeOnCompletion {
            if (activeGeneration.compareAndSet(generationId, 0L)) {
                generating.set(false)
                if (mutableState.value !is LlmState.Error) {
                    mutableState.value = modelName?.let { LlmState.ModelReady(it, "llama.cpp") }
                        ?: LlmState.NoModelLoaded
                }
            }
        }
        awaitClose {
            if (work.isActive && activeGeneration.get() == generationId) LlamaBridge.stopGeneration()
            work.cancel()
        }
    }

    override suspend fun stopGeneration() = withContext(Dispatchers.Default) {
        val name = modelName ?: return@withContext
        if (generating.get()) {
            mutableState.value = LlmState.Stopping(name)
            LlamaBridge.stopGeneration()
        }
    }

    override fun nativeVersion(): String = runCatching { LlamaBridge.getVersion() }.getOrElse { "JNI no disponible: ${it.message}" }
}
