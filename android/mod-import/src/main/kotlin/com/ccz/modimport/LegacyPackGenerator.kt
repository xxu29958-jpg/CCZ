package com.ccz.modimport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Offline generator (a converter tool, never part of runtime — see CCZ_ENGINE_RULES §Legacy MOD Boundary):
 * reads the user's locally-decrypted legacy tables and emits a small, PLAYABLE native content pack for one
 * real battle, driven through the same [LegacyBattleBuilder] mappers the rest of the importer uses.
 *
 * Why a curated subset, not the whole 2729-hero dump: a content pack bundled in the app only needs the
 * battle's roster, so the generator trims `dic_hero`/`dic_job` to the chosen hids/jobids before importing.
 *
 * Battle-design inputs the ore does not carry (honest, mirroring deploy level per ADR 0006): real heroes'
 * `skill` field is 0 (per-hero special skills live in `dic_jobSkill`/`dic_seid`, not yet wired), so every
 * combatant is granted a generic basic attack to make the battle playable; deploy levels come from the
 * battle spec; and the 7×5 flat field is a SYNTHESIZED map (the real `terrainMap` is intentionally not
 * imported here — [LegacyBattleBuilder.buildBattleOnMap] could — so growth × grade × level is the only
 * variable). Base stats and class growth are mined verbatim from `dic_hero`/`dic_job`; grade is THIS
 * engine's own quality tier DERIVED from real strength by [LegacyUnitMapper] (designed thresholds, not a
 * legacy constant), not a table value.
 *
 * Run: `./gradlew :mod-import:generateLegacyBattle -PextractedDir=<dir> -PoutPath=<file>`.
 */
object LegacyPackGenerator {
    private const val BASIC_ATTACK_ID = "skill_1"
    private const val BASIC_ATTACK_JSON = """[{"skid":1,"name":"普攻","type":0,"hurt_num":100,"mp_consume":0}]"""

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** One combatant in the generated battle: a real hero id, where it stands, its side, and deploy level. */
    data class RosterEntry(val hid: Int, val x: Int, val y: Int, val enemy: Boolean, val level: Int)

    /** 大兴山之战 — 桃园三兄弟 vs 黄巾，real heroes at deploy levels so growth × grade visibly differ. */
    private val DAXINGSHAN = listOf(
        RosterEntry(hid = 2, x = 1, y = 1, enemy = false, level = 8), // 关羽  裨将   grade 2
        RosterEntry(hid = 3, x = 1, y = 2, enemy = false, level = 7), // 张飞  重骑兵 grade 1
        RosterEntry(hid = 1, x = 0, y = 2, enemy = false, level = 6), // 刘备  群雄   grade 2
        RosterEntry(hid = 226, x = 6, y = 1, enemy = true, level = 5), // 程远志 重骑兵 grade 1
        RosterEntry(hid = 227, x = 6, y = 3, enemy = true, level = 4), // 邓茂   黄巾军 grade 0
    )

    /** Generate the 大兴山 battle pack JSON from the legacy tables in [extractedDir]. */
    fun generate(extractedDir: String): String {
        val dir = File(extractedDir)
        val hids = DAXINGSHAN.map { it.hid }.toSet()
        val roster = readArray(dir, "dic_hero.json").filter { it.jsonObject["hid"]?.jsonPrimitive?.int in hids }
        require(roster.size == hids.size) { "extracted dic_hero is missing some roster hids: $hids" }
        val jobIds = roster.mapNotNull { it.jsonObject["jobid"]?.jsonPrimitive?.int }.toSet()
        val jobs = readArray(dir, "dic_job.json").filter { it.jsonObject["jobid"]?.jsonPrimitive?.int in jobIds }

        val sources = LegacyTableSources(
            dicJob = encode(jobs),
            dicSkill = BASIC_ATTACK_JSON,
            dicHero = encode(roster),
            mapTerrain = """[{"mapid":1,"name":"平原"}]""",
        )
        val spec = BattleSpec(
            battleId = "daxingshan",
            width = 7,
            height = 5,
            terrainId = "terrain_1",
            protect = "hero_1", // 刘备 falling = defeat
            placements = DAXINGSHAN.map { Placement("hero_${it.hid}", it.x, it.y, it.enemy, it.level) },
        )
        return LegacyBattleBuilder.toJson(grantBasicAttack(LegacyBattleBuilder.buildBattle(meta(), sources, spec)))
    }

    /** Real heroes carry no usable skill (skill=0), so give every unit the basic attack to make the battle play. */
    private fun grantBasicAttack(pack: PackContent): PackContent {
        val units = pack.tables.units.map { it.copy(loadout = PackLoadout(skills = listOf(BASIC_ATTACK_ID))) }
        return pack.copy(tables = pack.tables.copy(units = units))
    }

    private fun meta(): PackMeta =
        PackMeta(contentId = "ccz_daxingshan", contentVersion = "0.1.0", mod = "trssgshz", entry = "daxingshan")

    private fun readArray(dir: File, name: String): List<JsonElement> =
        reader.parseToJsonElement(File(dir, name).readText().removePrefix("﻿")).jsonArray

    private fun encode(rows: List<JsonElement>): String =
        writer.encodeToString(JsonElement.serializer(), JsonArray(rows))

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: <extractedDir> <outPath>" }
        File(args[1]).apply { parentFile?.mkdirs() }.writeText(generate(args[0]))
    }
}
