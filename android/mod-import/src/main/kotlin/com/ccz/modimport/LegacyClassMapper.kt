package com.ccz.modimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Identity + move view of a legacy `dic_job` row; growth weights are folded separately (parseJobGrowth). */
@Serializable
internal data class LegacyJob(
    val jobid: Int,
    val name: String,
    val move: Int = 1,
)

/** A `classes` entry in the native content-pack wire format (snake_case keys mirror the engine schema). */
@Serializable
data class PackClass(
    val id: String,
    val name: String,
    val movement: PackMovement,
    val combat: PackCombat? = null,
)

@Serializable
data class PackMovement(
    @SerialName("move_type") val moveType: String,
    val move: Int,
    @SerialName("terrain_cost") val terrainCost: Map<String, Int> = emptyMap(),
)

@Serializable
data class PackCombat(
    @SerialName("terrain_affinity") val terrainAffinity: Map<String, Int> = emptyMap(),
    val growth: PackGrowth = PackGrowth(),
)

/** Per-class stat growth weights (per-level gain above level 1); mirrors the engine `ClassGrowth`. */
@Serializable
data class PackGrowth(
    val atk: Int = 0,
    val def: Int = 0,
    val mat: Int = 0,
    val res: Int = 0,
    val hp: Int = 0,
) {
    fun isZero(): Boolean = atk == 0 && def == 0 && mat == 0 && res == 0 && hp == 0
}

/**
 * Maps a legacy `dic_job` table into native content-pack `classes` entries, optionally folding the
 * per-class movement profile from `dic_jobWalk`.
 *
 * Faithful: class name and move points carry over verbatim. When `dic_jobWalk` is supplied, each
 * class gets a `terrain_cost` map (terrain id → enter cost): the legacy impassable marker 255 becomes
 * the engine's `0` sentinel, and cost-1 columns are omitted (they equal the terrain's base tile cost,
 * so falling back keeps the pack lean). The `dic_job` per-level growth weights (atk/def/ints→mat/hp_up)
 * fold into `combat.growth`, which the engine budgets into the unit's level-scaled panel at assembly
 * time (ADR 0006). `move_type` still has no `dic_job` source and stays a documented placeholder. Output
 * is the `classes` JSON consumed by the native-content loader.
 */
object LegacyClassMapper {
    /** Placeholder movement category (move points + per-terrain cost carry the real movement profile). */
    const val DEFAULT_MOVE_TYPE: String = "ground"
    private const val ID_PREFIX = "job_"
    private const val TERRAIN_PREFIX = "terrain_"
    private const val LEGACY_IMPASSABLE = 255
    private const val BASE_TILE_COST = 1
    private const val NEUTRAL_AFFINITY = 10
    private const val AFFINITY_SCALE = 10

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** Parse + map `dic_job` (BOM tolerated), optionally folding `dic_jobWalk` movement + `dic_jobTerrain` affinity. */
    fun mapClasses(
        dicJobJson: String,
        dicJobWalkJson: String? = null,
        dicJobTerrainJson: String? = null,
    ): List<PackClass> {
        val walk = dicJobWalkJson?.let(::parseJobWalk) ?: emptyMap()
        val affinity = dicJobTerrainJson?.let(::parseJobTerrain) ?: emptyMap()
        val growthByJob = parseJobGrowth(dicJobJson)
        val jobs = reader.decodeFromString(ListSerializer(LegacyJob.serializer()), dicJobJson.removePrefix("﻿"))
        val seen = HashSet<Int>(jobs.size)
        return jobs.map { job ->
            require(job.jobid >= 0) { "negative jobid: ${job.jobid}" }
            require(seen.add(job.jobid)) { "duplicate jobid: ${job.jobid}" }
            require(job.move >= 0) { "job ${job.jobid} has negative move ${job.move}" }
            require(job.name.isNotBlank()) { "job ${job.jobid} has blank name" }
            val terrainAffinity = affinity[job.jobid].orEmpty()
            val growth = growthByJob[job.jobid] ?: PackGrowth()
            PackClass(
                id = ID_PREFIX + job.jobid,
                name = job.name,
                movement = PackMovement(
                    moveType = DEFAULT_MOVE_TYPE,
                    move = job.move,
                    terrainCost = walk[job.jobid].orEmpty(),
                ),
                combat = if (terrainAffinity.isEmpty() && growth.isZero()) null else PackCombat(terrainAffinity, growth),
            )
        }
    }

    /** Serialize mapped classes as the content-pack `classes` table JSON array. */
    fun toClassesJson(classes: List<PackClass>): String =
        writer.encodeToString(ListSerializer(PackClass.serializer()), classes)

    /** dic_jobWalk → per-job {terrain_<col> → cost}: 255→0 (impassable), cost-1 columns omitted. */
    private fun parseJobWalk(json: String): Map<Int, Map<String, Int>> {
        val rows = reader.parseToJsonElement(json.removePrefix("﻿")).jsonArray
        val result = HashMap<Int, Map<String, Int>>(rows.size)
        for (element in rows) {
            val row = element.jsonObject
            val id = (row["id"] ?: continue).jsonPrimitive.int
            val cost = LinkedHashMap<String, Int>()
            for ((key, value) in row) {
                val col = if (key == "id") null else key.toIntOrNull()
                if (col == null) continue
                val raw = value.jsonPrimitive.int
                when {
                    raw >= LEGACY_IMPASSABLE -> cost[TERRAIN_PREFIX + col] = 0
                    raw != BASE_TILE_COST -> cost[TERRAIN_PREFIX + col] = raw
                }
            }
            result[id] = cost
        }
        return result
    }

    /**
     * dic_job → per-job [PackGrowth] per-level weights: atk/def straight, `ints`→mat (bridge, mirrors the
     * unit mapper), `hp_up`→hp; `res` has no legacy source (0, not invented). Rows without weights map to
     * a zero growth and are dropped at the call site.
     */
    private fun parseJobGrowth(json: String): Map<Int, PackGrowth> {
        val rows = reader.parseToJsonElement(json.removePrefix("﻿")).jsonArray
        val result = HashMap<Int, PackGrowth>(rows.size)
        for (element in rows) {
            val row = element.jsonObject
            val id = (row["jobid"] ?: continue).jsonPrimitive.int
            result[id] = PackGrowth(
                atk = row["atk"]?.jsonPrimitive?.int ?: 0,
                def = row["def"]?.jsonPrimitive?.int ?: 0,
                mat = row["ints"]?.jsonPrimitive?.int ?: 0,
                hp = row["hp_up"]?.jsonPrimitive?.int ?: 0,
            )
        }
        return result
    }

    /** dic_jobTerrain → per-job {terrain_<col> → combat %}: legacy coefficient ×10 (10→100 neutral, omitted). */
    private fun parseJobTerrain(json: String): Map<Int, Map<String, Int>> {
        val rows = reader.parseToJsonElement(json.removePrefix("﻿")).jsonArray
        val result = HashMap<Int, Map<String, Int>>(rows.size)
        for (element in rows) {
            val row = element.jsonObject
            val id = (row["id"] ?: continue).jsonPrimitive.int
            val pct = LinkedHashMap<String, Int>()
            for ((key, value) in row) {
                val col = if (key == "id") null else key.toIntOrNull()
                if (col == null) continue
                val raw = value.jsonPrimitive.int
                if (raw != NEUTRAL_AFFINITY) pct[TERRAIN_PREFIX + col] = raw * AFFINITY_SCALE
            }
            result[id] = pct
        }
        return result
    }
}
