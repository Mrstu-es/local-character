package com.localcharacter.app.llm.provider.network

import android.util.Log
import com.localcharacter.app.AppBuildInfo
import com.localcharacter.app.llm.provider.LlmStreamEvent
import com.localcharacter.app.llm.provider.ProviderConnectionResult
import com.localcharacter.app.llm.provider.ProviderError
import com.localcharacter.app.llm.provider.ProviderErrorKind
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

abstract class BaseHttpProvider(
    protected val client: OkHttpClient,
    private val apiKeyProvider: suspend () -> String?,
    private val requiresApiKey: Boolean = true,
) {
    protected val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val activeCall = AtomicReference<Call?>(null)

    protected suspend fun connectionTest(loadModels: suspend () -> List<*>): ProviderConnectionResult = try {
        ProviderConnectionResult.Success(loadModels().size)
    } catch (error: Throwable) {
        ProviderConnectionResult.Failure(ProviderErrorMapper.throwable(error))
    }

    protected suspend fun executeJson(buildRequest: (String) -> Request): JsonObject = withContext(Dispatchers.IO) {
        val key = requireCredential()
        val call = client.newCall(buildRequest(key))
        activeCall.set(call)
        val started = System.nanoTime()
        try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                debugHttp(response.code, started)
                if (!response.isSuccessful) throw ProviderException(ProviderErrorMapper.http(response.code, body, response.headers))
                json.parseToJsonElement(body).jsonObject
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    protected fun streamSse(
        buildRequest: (String) -> Request,
        parseEvent: (SseEvent) -> List<LlmStreamEvent>,
    ): Flow<LlmStreamEvent> = flow {
        val key = requireCredential()
        val call = client.newCall(buildRequest(key))
        activeCall.set(call)
        val started = System.nanoTime()
        var completed = false
        try {
            call.execute().use { response ->
                debugHttp(response.code, started)
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    emit(LlmStreamEvent.Error(ProviderErrorMapper.http(response.code, body, response.headers)))
                    return@use
                }
                val source = response.body?.source()
                    ?: throw ProviderException(ProviderError(ProviderErrorKind.PROVIDER, "El proveedor devolvió una respuesta vacía."))
                for (event in SseParser.events(source)) {
                    if (event.data == "[DONE]") {
                        completed = true
                        emit(LlmStreamEvent.Completed)
                        break
                    }
                    for (mapped in parseEvent(event)) {
                        if (mapped is LlmStreamEvent.Completed) completed = true
                        emit(mapped)
                    }
                    if (completed) break
                }
                if (!completed) emit(LlmStreamEvent.Completed)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            emit(LlmStreamEvent.Error(ProviderErrorMapper.throwable(error)))
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }.flowOn(Dispatchers.IO)

    protected suspend fun cancelHttpGeneration(): Unit = withContext(Dispatchers.IO) {
        activeCall.getAndSet(null)?.cancel()
    }

    private suspend fun requireCredential(): String {
        val key = apiKeyProvider()?.trim().orEmpty()
        if (requiresApiKey && key.isBlank()) throw ProviderException(
            ProviderError(ProviderErrorKind.AUTHENTICATION, "Configura una API Key para este proveedor."),
        )
        return key
    }

    private fun debugHttp(status: Int, started: Long) {
        if (AppBuildInfo.DEBUG) {
            val latencyMs = (System.nanoTime() - started) / 1_000_000
            Log.d("AiProvider", "HTTP status=$status latencyMs=$latencyMs")
        }
    }
}
