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
    val passable: Boolean = true,
    val bonuses: PackBonuses = PackBonuses(),
)

/**
 * Per-terrain defender bonuses (snake_case keys mirror native `TerrainBonusesDto`). These are an ENGINE
 * DESIGN choice, NOT mined values: the legacy `map_terrain.effect` field carries opaque effect-set codes
 * (e.g. "0&3"/"2&3") whose magnitudes live in the native binary, not the table — so rather than guess
 * those, we assign sensible 曹操传-style cover by terrain TYPE. Default all-zero = no bonus.
 */
@Serializable
data class PackBonuses(
    @SerialName("def_bonus") val defBonus: Int = 0,
    @SerialName("avoid_bonus") val avoidBonus: Int = 0,
    val heal: Int = 0,
)

/**
 * Maps a legacy `map_terrain` table into native content-pack `terrain` entries (the terrain catalog).
 *
 * Faithful: id (`terrain_<mapid>`) and name. `move_cost` is the engine base cost 1 for every terrain
 * (per-class movement cost lives in `dic_jobWalk`, folded into class movement, not the catalog). Defender
 * [bonuses] (def/avoid/heal) are an engine-DESIGN cover table keyed by terrain type — the legacy `effect`
 * codes carry no magnitudes, so we assign sensible cover rather than guess (see [PackBonuses]).
 */
object LegacyTerrainMapper {
    const val BASE_MOVE_COST: Int = 1
    private const val ID_PREFIX = "terrain_"

    /**
     * Designed defender-cover by legacy terrain mapid (see [PackBonuses] — engine tuning, not mined):
     * forest gives evasion, mountain/rock defense + evasion, fort/wall heavy defense, settlements heal.
     * Open ground (平原/草原) and anything absent gets nothing.
     */
    private val BONUSES: Map<Int, PackBonuses> = mapOf(
        3 to PackBonuses(avoidBonus = 15), // 树林 forest
        4 to PackBonuses(avoidBonus = 5), // 荒地 rough
        5 to PackBonuses(defBonus = 15, avoidBonus = 10), // 山地 high ground
        6 to PackBonuses(defBonus = 25, avoidBonus = 10), // 岩山
        7 to PackBonuses(defBonus = 25, avoidBonus = 10), // 山崖
        8 to PackBonuses(avoidBonus = 5), // 雪原
        16 to PackBonuses(defBonus = 30), // 城墙
        17 to PackBonuses(defBonus = 20), // 城内
        18 to PackBonuses(defBonus = 20), // 城门
        19 to PackBonuses(defBonus = 20), // 城池
        20 to PackBonuses(defBonus = 20), // 关隘
        21 to PackBonuses(defBonus = 20), // 鹿砦
        22 to PackBonuses(defBonus = 10, heal = 10), // 村庄
        23 to PackBonuses(defBonus = 10, heal = 10), // 兵营
        24 to PackBonuses(defBonus = 10, heal = 10), // 民居
    )

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
            PackTerrain(
                id = ID_PREFIX + row.mapid,
                name = row.name,
                moveCost = BASE_MOVE_COST,
                bonuses = BONUSES[row.mapid] ?: PackBonuses(),
            )
        }
    }

    /** Serialize mapped terrain as the content-pack `terrain` table JSON array. */
    fun toTerrainJson(terrain: List<PackTerrain>): String =
        writer.encodeToString(ListSerializer(PackTerrain.serializer()), terrain)
}
