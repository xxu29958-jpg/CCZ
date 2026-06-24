package com.ccz.modimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Minimal view of a legacy `dic_job` row; the rich growth/asset fields are ignored for this slice. */
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
)

@Serializable
data class PackMovement(
    @SerialName("move_type") val moveType: String,
    val move: Int,
    @SerialName("terrain_cost") val terrainCost: Map<String, Int> = emptyMap(),
)

/**
 * Maps a legacy `dic_job` table into native content-pack `classes` entries, optionally folding the
 * per-class movement profile from `dic_jobWalk`.
 *
 * Faithful: class name and move points carry over verbatim. When `dic_jobWalk` is supplied, each
 * class gets a `terrain_cost` map (terrain id → enter cost): the legacy impassable marker 255 becomes
 * the engine's `0` sentinel, and cost-1 columns are omitted (they equal the terrain's base tile cost,
 * so falling back keeps the pack lean). `move_type` still has no `dic_job` source and stays a
 * documented placeholder. Output is the `classes` JSON consumed by the native-content loader.
 */
object LegacyClassMapper {
    /** Placeholder movement category (move points + per-terrain cost carry the real movement profile). */
    const val DEFAULT_MOVE_TYPE: String = "ground"
    private const val ID_PREFIX = "job_"
    private const val TERRAIN_PREFIX = "terrain_"
    private const val LEGACY_IMPASSABLE = 255
    private const val BASE_TILE_COST = 1

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** Parse + map a decrypted `dic_job` table (BOM tolerated), optionally with `dic_jobWalk` movement. */
    fun mapClasses(dicJobJson: String, dicJobWalkJson: String? = null): List<PackClass> {
        val walk = dicJobWalkJson?.let(::parseJobWalk) ?: emptyMap()
        val jobs = reader.decodeFromString(ListSerializer(LegacyJob.serializer()), dicJobJson.removePrefix("﻿"))
        val seen = HashSet<Int>(jobs.size)
        return jobs.map { job ->
            require(job.jobid >= 0) { "negative jobid: ${job.jobid}" }
            require(seen.add(job.jobid)) { "duplicate jobid: ${job.jobid}" }
            require(job.move >= 0) { "job ${job.jobid} has negative move ${job.move}" }
            require(job.name.isNotBlank()) { "job ${job.jobid} has blank name" }
            PackClass(
                id = ID_PREFIX + job.jobid,
                name = job.name,
                movement = PackMovement(
                    moveType = DEFAULT_MOVE_TYPE,
                    move = job.move,
                    terrainCost = walk[job.jobid].orEmpty(),
                ),
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
}
