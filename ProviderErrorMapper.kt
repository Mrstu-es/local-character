package com.localcharacter.app.llm.provider.network

import com.localcharacter.app.llm.provider.ProviderError
import com.localcharacter.app.llm.provider.ProviderErrorKind
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers

class ProviderException(val providerError: ProviderError) : IOException(providerError.friendlyMessage)

object ProviderErrorMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun http(status: Int, body: String?, headers: Headers = Headers.headersOf()): ProviderError {
        val details = extractDetails(body)
        val retry = headers["Retry-After"]?.trim()?.toLongOrNull()
        val kind = when (status) {
            400 -> if (details.code?.contains("billing", true) == true || details.code?.contains("credit", true) == true) {
                ProviderErrorKind.BILLING
            } else ProviderErrorKind.INVALID_REQUEST
            401 -> ProviderErrorKind.AUTHENTICATION
            402 -> ProviderErrorKind.BILLING
            403 -> ProviderErrorKind.ACCESS_DENIED
            404 -> ProviderErrorKind.MODEL_NOT_FOUND
            408 -> ProviderErrorKind.TIMEOUT
            429 -> ProviderErrorKind.RATE_LIMIT
            in 500..599 -> ProviderErrorKind.PROVIDER
            else -> ProviderErrorKind.UNKNOWN
        }
        val friendly = when (kind) {
            ProviderErrorKind.AUTHENTICATION -> "API Key inválida o no autorizada."
            ProviderErrorKind.ACCESS_DENIED -> "El proveedor denegó el acceso a este recurso."
            ProviderErrorKind.MODEL_NOT_FOUND -> "El modelo seleccionado ya no está disponible."
            ProviderErrorKind.TIMEOUT -> "El proveedor tardó demasiado en responder."
            ProviderErrorKind.RATE_LIMIT -> "Has alcanzado el límite de solicitudes de este proveedor."
            ProviderErrorKind.BILLING -> "El proveedor rechazó la solicitud por saldo o límite de facturación."
            ProviderErrorKind.INVALID_REQUEST -> details.message ?: "El proveedor rechazó los parámetros de la solicitud."
            ProviderErrorKind.PROVIDER -> "El proveedor tiene un problema temporal."
            else -> "No se pudo completar la solicitud al proveedor."
        }
        return ProviderError(kind, friendly, details.message, status, retry, details.code)
    }

    fun throwable(error: Throwable): ProviderError = when (error) {
        is ProviderException -> error.providerError
        is CancellationException -> ProviderError(ProviderErrorKind.CANCELLED, "Generación cancelada.", error.message)
        is SocketTimeoutException -> ProviderError(ProviderErrorKind.TIMEOUT, "El proveedor tardó demasiado en responder.", error.message)
        is IOException -> ProviderError(ProviderErrorKind.NETWORK, "Sin conexión con el proveedor.", error.message)
        else -> ProviderError(ProviderErrorKind.UNKNOWN, "No se pudo completar la solicitud.", error.message)
    }

    private data class Details(val message: String?, val code: String?)

    private fun extractDetails(body: String?): Details {
        if (body.isNullOrBlank()) return Details(null, null)
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]?.let { it as? JsonObject }
            Details(
                message = error?.get("message")?.jsonPrimitive?.content
                    ?: root["message"]?.jsonPrimitive?.content,
                code = error?.get("code")?.jsonPrimitive?.content
                    ?: error?.get("type")?.jsonPrimitive?.content
                    ?: root["code"]?.jsonPrimitive?.content,
            )
        }.getOrDefault(Details(body.take(300), null))
    }
}

