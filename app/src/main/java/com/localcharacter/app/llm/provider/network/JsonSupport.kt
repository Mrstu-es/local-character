package com.localcharacter.app.llm.provider.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
internal fun JsonObject.long(name: String): Long? = get(name)?.jsonPrimitive?.longOrNull
internal fun JsonObject.int(name: String): Int? = get(name)?.jsonPrimitive?.intOrNull
internal fun JsonObject.double(name: String): Double? = get(name)?.jsonPrimitive?.doubleOrNull
internal fun JsonObject.bool(name: String): Boolean? = get(name)?.jsonPrimitive?.booleanOrNull
internal fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
