package com.ccz.modimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
)

/**
 * Maps a legacy `dic_job` table into native content-pack `classes` entries.
 *
 * Faithful fields: class name and move points carry over verbatim. `move_type` has no source in
 * `dic_job` (the per-terrain movement profile lives in `dic_jobWalk`), so it is set to a documented
 * placeholder; refining it from the walk matrix is a follow-up slice. Output is the `classes` table
 * JSON consumed by the native-content loader, keeping mod-import decoupled from its internal DTOs.
 */
object LegacyClassMapper {
    /** Placeholder movement category until `dic_jobWalk` profiles are mapped (see KDoc). */
    const val DEFAULT_MOVE_TYPE: String = "ground"
    private const val ID_PREFIX = "job_"

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** Parse + map a decrypted `dic_job` table (BOM tolerated) into content-pack classes. */
    fun mapClasses(dicJobJson: String): List<PackClass> {
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
                movement = PackMovement(moveType = DEFAULT_MOVE_TYPE, move = job.move),
            )
        }
    }

    /** Serialize mapped classes as the content-pack `classes` table JSON array. */
    fun toClassesJson(classes: List<PackClass>): String =
        writer.encodeToString(ListSerializer(PackClass.serializer()), classes)
}
