package com.ccz.contentpack.json

import com.ccz.contentpack.NativeContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Loads a native content pack from JSON text into the domain [NativeContent].
 * Strict and fail-closed: unknown JSON keys, missing required fields, and unknown
 * enum strings are all rejected with a [ContentDecodeException]. Loading does NOT
 * run cross-reference validation — callers pass the result to
 * [com.ccz.contentpack.ContentValidator] (two layers: decode shape here, reference
 * integrity there). JSON keys are snake_case. Event-script ops are polymorphic and
 * decoded by a class-discriminator field ("type") whose value is the op's serial name
 * (the op whitelist); an unknown op fails closed here (see [EventDto]).
 */
object ContentJsonLoader {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        namingStrategy = JsonNamingStrategy.SnakeCase
        classDiscriminator = "type"
    }

    fun load(text: String): NativeContent =
        try {
            ContentMapper.toContent(json.decodeFromString<ContentDto>(text))
        } catch (e: SerializationException) {
            throw ContentDecodeException("malformed content pack: ${e.message}", e)
        }
}
