package com.ccz.modimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A `units` entry in the native content-pack wire format. */
@Serializable
data class PackUnit(
    val identity: PackIdentity,
    val profile: PackProfile,
    val loadout: PackLoadout = PackLoadout(),
)

@Serializable
data class PackIdentity(
    val id: String,
    val name: String,
    @SerialName("class_id") val classId: String,
    val faction: String,
)

@Serializable
data class PackProfile(
    val level: Int,
    @SerialName("hp_max") val hpMax: Int,
    val stats: PackStats,
)

@Serializable
data class PackStats(val atk: Int, val def: Int, val mat: Int, val res: Int)

@Serializable
data class PackLoadout(val skills: List<String> = emptyList())

/**
 * Maps a legacy `dic_hero` table into native content-pack `units` (the recruitable roster).
 *
 * The legacy row is read field-by-field from JSON (it is a flat ~25-field object, too wide for a
 * single parse DTO). Faithful: name, level, `hp`→`hp_max`, `jobid`→`class_id` (`job_*`). Stat bridge
 * (documented): `atk`→atk, `def`→def, `ints`→mat (strategy power); `burst`→res fills the engine's
 * 4th stat slot — the legacy 5-stat model (atk/def/ints/burst/fortune) does not map 1:1 to the
 * engine's atk/def/mat/res, so this is provisional and reconciled in the engine-upgrade slice.
 * Faction defaults to PLAYER (the hero table is the player roster; enemies come from scenario placement).
 */
object LegacyUnitMapper {
    const val DEFAULT_FACTION: String = "PLAYER"
    private const val UNIT_PREFIX = "hero_"
    private const val CLASS_PREFIX = "job_"
    private const val SKILL_PREFIX = "skill_"

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** Parse + map a decrypted `dic_hero` table (BOM tolerated) into content-pack units. */
    fun mapUnits(dicHeroJson: String): List<PackUnit> {
        val rows = reader.parseToJsonElement(dicHeroJson.removePrefix("﻿")).jsonArray
        val seen = HashSet<Int>(rows.size)
        return rows.map { element ->
            val row = element.jsonObject
            val hid = row.reqInt("hid")
            require(hid >= 0) { "negative hid: $hid" }
            require(seen.add(hid)) { "duplicate hid: $hid" }
            val name = row.reqString("name")
            require(name.isNotBlank()) { "hero $hid has blank name" }
            val jobid = row.reqInt("jobid")
            require(jobid >= 0) { "hero $hid has negative jobid $jobid" }
            val level = row.optInt("level", 1)
            require(level >= 1) { "hero $hid has non-positive level $level" }
            val hp = row.optInt("hp", 1)
            require(hp >= 1) { "hero $hid has non-positive hp $hp" }
            val skill = row.optInt("skill", 0)
            PackUnit(
                identity = PackIdentity(UNIT_PREFIX + hid, name, CLASS_PREFIX + jobid, DEFAULT_FACTION),
                profile = PackProfile(
                    level = level,
                    hpMax = hp,
                    stats = PackStats(
                        atk = row.optInt("atk", 0),
                        def = row.optInt("def", 0),
                        mat = row.optInt("ints", 0),
                        res = row.optInt("burst", 0),
                    ),
                ),
                loadout = PackLoadout(skills = if (skill > 0) listOf(SKILL_PREFIX + skill) else emptyList()),
            )
        }
    }

    /** Serialize mapped units as the content-pack `units` table JSON array. */
    fun toUnitsJson(units: List<PackUnit>): String =
        writer.encodeToString(ListSerializer(PackUnit.serializer()), units)
}

private fun JsonObject.reqInt(key: String): Int =
    (this[key] ?: throw IllegalArgumentException("missing field '$key'")).jsonPrimitive.int

private fun JsonObject.optInt(key: String, default: Int): Int = this[key]?.jsonPrimitive?.int ?: default

private fun JsonObject.reqString(key: String): String =
    (this[key] ?: throw IllegalArgumentException("missing field '$key'")).jsonPrimitive.content
