package com.ccz.modimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Minimal view of a legacy `terrainMap_*.json`: a width×height grid of terrain ids. */
@Serializable
internal data class LegacyTerrainMap(
    @SerialName("map_width") val width: Int,
    @SerialName("map_height") val height: Int,
    @SerialName("map_value") val mapValue: List<List<Int>>,
)

/**
 * Maps a legacy `terrainMap_*.json` into a native content-pack `maps` entry: the `map_value` grid of
 * terrain ids becomes `tiles` of `terrain_<id>` strings (matching [LegacyTerrainMapper]'s ids), with
 * `map_width`/`map_height` carried verbatim.
 *
 * Tile terrain ids are emitted as-is; the assembler/[com.ccz.contentpack.ContentValidator] still
 * require every referenced terrain to exist in the pack's `terrain` table, so a real battle must ship
 * the full terrain catalog (and any edge/void id the map uses) — coverage is validated there, not here.
 */
object LegacyMapMapper {
    const val TILESET: String = "legacy"
    private const val TERRAIN_PREFIX = "terrain_"

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** Parse + map a decrypted `terrainMap` (BOM tolerated) into a content-pack map with the given [id]. */
    fun mapMap(terrainMapJson: String, id: String): PackMap {
        require(id.isNotBlank()) { "map id must not be blank" }
        val src = reader.decodeFromString(LegacyTerrainMap.serializer(), terrainMapJson.removePrefix("﻿"))
        require(src.width > 0 && src.height > 0) { "map '$id' size must be positive: ${src.width}x${src.height}" }
        require(src.mapValue.size == src.height) {
            "map '$id' has ${src.mapValue.size} rows but map_height=${src.height}"
        }
        val tiles = src.mapValue.mapIndexed { y, rowValues ->
            require(rowValues.size == src.width) {
                "map '$id' row $y has ${rowValues.size} tiles but map_width=${src.width}"
            }
            rowValues.map { terrainId ->
                require(terrainId >= 0) { "map '$id' row $y has negative terrain id $terrainId" }
                TERRAIN_PREFIX + terrainId
            }
        }
        return PackMap(id = id, size = PackSize(src.width, src.height), tileset = TILESET, tiles = tiles)
    }

    /** Serialize mapped maps as the content-pack `maps` table JSON array. */
    fun toMapsJson(maps: List<PackMap>): String =
        writer.encodeToString(kotlinx.serialization.builtins.ListSerializer(PackMap.serializer()), maps)
}
