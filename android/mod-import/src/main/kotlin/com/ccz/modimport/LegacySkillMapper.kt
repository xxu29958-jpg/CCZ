package com.ccz.modimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Minimal view of a legacy `dic_skill` row; FX/marker/limit fields are out of scope for this slice. */
@Serializable
internal data class LegacySkill(
    val skid: Int,
    val name: String,
    val type: Int = 0,
    @SerialName("hurt_num") val hurtNum: Int = 0,
    @SerialName("mp_consume") val mpConsume: Int = 0,
)

/** A `skills` entry in the native content-pack wire format. */
@Serializable
data class PackSkill(
    val id: String,
    val name: String,
    val kind: String,
    @SerialName("power_coeff") val powerCoeff: Int,
    val use: PackUse,
)

@Serializable
data class PackUse(
    val range: PackRange,
    val area: String,
    val targeting: String,
    @SerialName("mp_cost") val mpCost: Int = 0,
)

@Serializable
data class PackRange(val min: Int, val max: Int)

/**
 * Maps a legacy `dic_skill` table into native content-pack `skills` entries.
 *
 * Faithful: name, `hurt_num`→`power_coeff`, `mp_consume`→`use.mp_cost`, `type`→`kind` (the legacy
 * numeric `type` 0 is treated as PHYSICAL, others STRATEGY — provisional; the engine's binary
 * physical/strategy split does not cleanly mirror the legacy element/atkid model, so kind refinement
 * is deferred). Defaulted: melee `range` 1..1, `single` area, `enemy` targeting — the legacy range
 * lives in `dic_atk_se`/`atkid`, a follow-up slice. Output is the loader-consumed `skills` JSON.
 */
object LegacySkillMapper {
    const val DEFAULT_AREA: String = "single"
    const val DEFAULT_TARGETING: String = "enemy"
    private const val ID_PREFIX = "skill_"
    private const val PHYSICAL = "PHYSICAL"
    private const val STRATEGY = "STRATEGY"
    private val DEFAULT_RANGE = PackRange(min = 1, max = 1)

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** Parse + map a decrypted `dic_skill` table (BOM tolerated) into content-pack skills. */
    fun mapSkills(dicSkillJson: String): List<PackSkill> {
        val skills = reader.decodeFromString(ListSerializer(LegacySkill.serializer()), dicSkillJson.removePrefix("﻿"))
        val seen = HashSet<Int>(skills.size)
        return skills.map { skill ->
            require(skill.skid >= 0) { "negative skid: ${skill.skid}" }
            require(seen.add(skill.skid)) { "duplicate skid: ${skill.skid}" }
            require(skill.name.isNotBlank()) { "skill ${skill.skid} has blank name" }
            require(skill.hurtNum >= 0) { "skill ${skill.skid} has negative hurt_num ${skill.hurtNum}" }
            require(skill.mpConsume >= 0) { "skill ${skill.skid} has negative mp_consume ${skill.mpConsume}" }
            PackSkill(
                id = ID_PREFIX + skill.skid,
                name = skill.name,
                kind = if (skill.type == 0) PHYSICAL else STRATEGY,
                powerCoeff = skill.hurtNum,
                use = PackUse(
                    range = DEFAULT_RANGE,
                    area = DEFAULT_AREA,
                    targeting = DEFAULT_TARGETING,
                    mpCost = skill.mpConsume,
                ),
            )
        }
    }

    /** Serialize mapped skills as the content-pack `skills` table JSON array. */
    fun toSkillsJson(skills: List<PackSkill>): String =
        writer.encodeToString(ListSerializer(PackSkill.serializer()), skills)
}
