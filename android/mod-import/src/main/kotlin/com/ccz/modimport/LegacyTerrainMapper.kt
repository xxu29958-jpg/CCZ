package com.ccz.modimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Minimal view of a legacy `map_terrain` row; `effect`/`music`/`seid` are out of scope for this slice. */
@Serializable
internal data class LegacyTerrain(
    val mapid: Int,
    val name: String,
)

/** A `terrain` entry in the native content-pack wire format. */
@Serializable
data class PackTerrain(
    val id: String,
    val name: String,
    @SerialName("move_cost") val moveCost: Int,
)

/**
 * Maps a legacy `map_terrain` table into native content-pack `terrain` entries (the terrain catalog).
 *
 * Faithful: id (`terrain_<mapid>`) and name. `move_cost` is set to the engine base cost 1 for every
 * terrain: legacy movement cost is per-class-per-terrain (`dic_jobWalk`, incl. impassability), which
 * the engine's per-tile `MapTile` model cannot yet represent — that, plus the `dic_jobTerrain` combat
 * coefficient matrix and the `effect` bonus semantics, lands in the engine-upgrade slice.
 */
object LegacyTerrainMapper {
    const val BASE_MOVE_COST: Int = 1
    private const val ID_PREFIX = "terrain_"

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** Parse + map a decrypted `map_terrain` table (BOM tolerated) into content-pack terrain. */
    fun mapTerrain(mapTerrainJson: String): List<PackTerrain> {
        val rows = reader.decodeFromString(ListSerializer(LegacyTerrain.serializer()), mapTerrainJson.removePrefix("﻿"))
        val seen = HashSet<Int>(rows.size)
        return rows.map { row ->
            require(row.mapid >= 0) { "negative mapid: ${row.mapid}" }
            require(seen.add(row.mapid)) { "duplicate mapid: ${row.mapid}" }
            require(row.name.isNotBlank()) { "terrain ${row.mapid} has blank name" }
            PackTerrain(id = ID_PREFIX + row.mapid, name = row.name, moveCost = BASE_MOVE_COST)
        }
    }

    /** Serialize mapped terrain as the content-pack `terrain` table JSON array. */
    fun toTerrainJson(terrain: List<PackTerrain>): String =
        writer.encodeToString(ListSerializer(PackTerrain.serializer()), terrain)
}
