package com.ccz.core.save

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Thrown when on-disk save text cannot be decoded into a [SaveEnvelope] (fail-closed).
 * A [RuntimeException] (corrupt data is unrecoverable), mirroring native-content's
 * `ContentDecodeException` so both decode boundaries share one exception style.
 */
class SaveDecodeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * On-disk codec for [SaveEnvelope] <-> JSON text. Strict and fail-closed: unknown keys,
 * missing required fields, unknown command kinds, and unknown faction/outcome strings are
 * all rejected with a [SaveDecodeException]. Decoding validates *shape* only — version-axis
 * gating (future save schema / rules drift) stays in [SaveLoader.check], so the two layers
 * mirror ContentJsonLoader (shape) + ContentValidator (semantics). Saves are written with
 * defaults encoded so the file is explicit and self-contained (resilient to future default
 * drift). game-core domain types carry no serialization annotations; the [SaveEnvelopeDto]
 * layer isolates JSON.
 */
object SaveCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        classDiscriminator = "type"
    }

    fun encode(envelope: SaveEnvelope): String =
        json.encodeToString(SaveEnvelopeDto.serializer(), SaveMappers.toDto(envelope))

    fun decode(text: String): SaveEnvelope =
        try {
            SaveMappers.toDomain(json.decodeFromString(SaveEnvelopeDto.serializer(), text))
        } catch (e: SerializationException) {
            throw SaveDecodeException("malformed save: ${e.message}", e)
        }
}
