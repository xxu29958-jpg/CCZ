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
 * The battlefield is a CROP of the real `terrainMap_1` (大兴山): an 8×7 interior window of genuine legacy
 * terrain (荒地/山地/树林) — cropped only because the app board renders fixed 44dp cells with no scroll, so
 * a full 23×16 map would overflow a phone. Real `dic_jobWalk` (per-class move cost) and `dic_jobTerrain`
 * (terrain combat affinity) are wired in, so terrain is mechanically real, not cosmetic.
 *
 * Honest battle-design inputs the ore does not carry (mirroring deploy level, ADR 0006): real heroes'
 * `skill` field is 0 (per-hero specials live in `dic_jobSkill`/`dic_seid`, not yet wired), so every
 * combatant is granted a generic basic attack to make the battle playable; deploy levels come from the
 * battle spec. Base stats and class growth are mined verbatim from `dic_hero`/`dic_job`; grade is THIS
 * engine's own quality tier DERIVED from real strength by [LegacyUnitMapper] (designed thresholds), not a
 * table value.
 *
 * Run: `./gradlew :mod-import:generateLegacyBattle -PextractedDir=<extractedRoot> -PoutPath=<file>`,
 * where `<extractedRoot>` holds `json/` (the dic_* tables) and `terrainJson/` (the terrainMap_*.json maps).
 */
object LegacyPackGenerator {
    // 大兴山 is gkid 1, whose stage script is Scenes/S_00.eex_new (S_n ↔ gkid n+1; see docs/recon).
    private const val DAXINGSHAN_SCRIPT = "S_00.eex_new"
    private const val BASIC_ATTACK_ID = "skill_1"
    private const val BASIC_ATTACK_JSON = """[{"skid":1,"name":"普攻","type":0,"hurt_num":100,"mp_consume":0}]"""

    // 8×7 window of real terrainMap_1 — an all-real-terrain (no void) interior patch that fits the board.
    private const val WIN_X = 4
    private const val WIN_Y = 0
    private const val WIN_W = 8
    private const val WIN_H = 7

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    /** One combatant: a real hero id, where it stands (window-local), its side, and deploy level. */
    data class RosterEntry(val hid: Int, val x: Int, val y: Int, val enemy: Boolean, val level: Int)

    /**
     * 大兴山之战 — 桃园三兄弟 vs 黄巾，real heroes at deploy levels so growth × grade visibly differ.
     * All five spawn in the connected 荒地/树林 corridor (window cols 0-2) — never on the 山地 (terrain_5)
     * block, whose cost-2 tiles would strand the move-1 黄巾军 (邓茂). Players (bottom, y4-5) and enemies
     * (top, y1) start 3 rows apart, out of the skill's reach-1, so the aggressive AI advances every enemy
     * rather than anyone being stuck or fighting from spawn.
     */
    private val DAXINGSHAN = listOf(
        RosterEntry(hid = 2, x = 1, y = 4, enemy = false, level = 8), // 关羽  裨将   grade 2
        RosterEntry(hid = 3, x = 1, y = 5, enemy = false, level = 7), // 张飞  重骑兵 grade 1
        RosterEntry(hid = 1, x = 0, y = 4, enemy = false, level = 6), // 刘备  群雄   grade 2
        RosterEntry(hid = 226, x = 1, y = 1, enemy = true, level = 5), // 程远志 重骑兵 grade 1
        RosterEntry(hid = 227, x = 0, y = 1, enemy = true, level = 4), // 邓茂   黄巾军 grade 0
    )

    /**
     * Generate the 大兴山 battle pack JSON from the legacy tables under [extractedRoot] (`json/` + `terrainJson/`
     * + `Scenes/`). The win/lose objectives are DERIVED from the real stage script (gkid 1 大兴山 → `S_00.eex_new`)
     * via [LegacyObjectiveImporter], scoped to the deployed roster, instead of being hand-coded — for 大兴山 they
     * reconcile to annihilate-enemies / protect-刘备 (the script's protect-邹靖 falls out of roster since 邹靖 is
     * not deployed in this curated battle; its turn deadline is unsupported).
     */
    fun generate(extractedRoot: String): String {
        val root = File(extractedRoot)
        val jsonDir = File(root, "json")
        val hids = DAXINGSHAN.map { it.hid }.toSet()
        val allHeroes = readArray(jsonDir, "dic_hero.json")
        val roster = allHeroes.filter { it.jsonObject["hid"]?.jsonPrimitive?.int in hids }
        require(roster.size == hids.size) { "extracted dic_hero is missing some roster hids: $hids" }
        val jobIds = roster.mapNotNull { it.jsonObject["jobid"]?.jsonPrimitive?.int }.toSet()
        val jobs = readArray(jsonDir, "dic_job.json").filter { it.jsonObject["jobid"]?.jsonPrimitive?.int in jobIds }

        val sources = LegacyTableSources(
            dicJob = encode(jobs),
            dicSkill = BASIC_ATTACK_JSON,
            dicHero = encode(roster),
            mapTerrain = read(jsonDir, "map_terrain.json"),
            dicJobWalk = read(jsonDir, "dic_jobWalk.json"),
            dicJobTerrain = read(jsonDir, "dic_jobTerrain.json"),
        )
        val terrainMap = cropTerrainMap(read(File(root, "terrainJson"), "terrainMap_1.json"))
        val objectives = importObjectives(root, allHeroes, hids)
        val spec = MapBattleSpec(
            battleId = "daxingshan",
            mapId = "daxingshan_map",
            protect = "hero_1", // 刘备 falling = defeat (fallback if the script declares no win/lose)
            placements = DAXINGSHAN.map { Placement("hero_${it.hid}", it.x, it.y, it.enemy, it.level) },
            win = objectives.win.ifEmpty { null },
            lose = objectives.lose.ifEmpty { null },
        )
        return LegacyBattleBuilder.toJson(grantBasicAttack(LegacyBattleBuilder.buildBattleOnMap(meta(), sources, terrainMap, spec)))
    }

    /** Import 大兴山's win/lose objectives from the real `Scenes/S_00.eex_new`, scoped to the deployed roster.
     *  [allHeroes] (full dic_hero) resolves any name (incl. non-deployed allies like 邹靖) to its hero id. */
    private fun importObjectives(root: File, allHeroes: List<JsonElement>, hids: Set<Int>): LegacyObjectiveImporter.ImportedObjectives {
        val nameToId = allHeroes.associate { hero ->
            (hero.jsonObject["name"]?.jsonPrimitive?.content ?: "") to "hero_${hero.jsonObject["hid"]?.jsonPrimitive?.int}"
        }
        val rosterIds = hids.mapTo(HashSet()) { "hero_$it" }
        val scriptBytes = File(File(root, "Scenes"), DAXINGSHAN_SCRIPT).readBytes()
        return LegacyObjectiveImporter.importObjectives(scriptBytes, rosterIds, nameToId::get)
    }

    /** Crop the real terrainMap to the [WIN_W]×[WIN_H] window at ([WIN_X],[WIN_Y]) as a terrainMap JSON. */
    private fun cropTerrainMap(json: String): String {
        val grid = reader.parseToJsonElement(json.removePrefix("﻿")).jsonObject.getValue("map_value").jsonArray
            .map { row -> row.jsonArray.map { it.jsonPrimitive.int } }
        val rows = (WIN_Y until WIN_Y + WIN_H).joinToString(",") { y ->
            "[" + (WIN_X until WIN_X + WIN_W).joinToString(",") { x -> grid[y][x].toString() } + "]"
        }
        return """{"map_width":$WIN_W,"map_height":$WIN_H,"map_value":[$rows]}"""
    }

    /** Real heroes carry no usable skill (skill=0), so give every unit the basic attack to make the battle play. */
    private fun grantBasicAttack(pack: PackContent): PackContent {
        val units = pack.tables.units.map { it.copy(loadout = PackLoadout(skills = listOf(BASIC_ATTACK_ID))) }
        return pack.copy(tables = pack.tables.copy(units = units))
    }

    private fun meta(): PackMeta =
        PackMeta(contentId = "ccz_daxingshan", contentVersion = "0.1.0", mod = "trssgshz", entry = "daxingshan")

    private fun readArray(dir: File, name: String): List<JsonElement> =
        reader.parseToJsonElement(read(dir, name)).jsonArray

    private fun read(dir: File, name: String): String = File(dir, name).readText().removePrefix("﻿")

    private fun encode(rows: List<JsonElement>): String =
        writer.encodeToString(JsonElement.serializer(), JsonArray(rows))

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: <extractedRoot> <outPath>" }
        File(args[1]).apply { parentFile?.mkdirs() }.writeText(generate(args[0]))
    }
}
