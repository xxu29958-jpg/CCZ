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
    private const val HERO_PREFIX = "hero_"
    private const val FULL_BATTLE_ID = "daxingshan_full"
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
        val sources = sourcesFor(jsonDir, allHeroes, hids)
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

    // 大兴山 full-stage player army (NOT in the stage file — the legacy game deploys it interactively — so it is
    // a battle-design input). The 桃园三兄弟 stage in a southern open patch (terrainMap_1 cols 9-11, row 14,
    // passable code-0 tiles clear of every dispatched enemy/ally), at designed levels so growth × grade bite.
    private data class PlayerSeed(val hid: Int, val x: Int, val y: Int, val level: Int)
    private val FULL_PLAYERS = listOf(
        PlayerSeed(hid = 1, x = 10, y = 14, level = 6), // 刘备  群雄
        PlayerSeed(hid = 2, x = 9, y = 14, level = 8), //  关羽  裨将
        PlayerSeed(hid = 3, x = 11, y = 14, level = 7), // 张飞  重骑兵
    )

    /**
     * Generate the FAITHFUL FULL-STAGE 大兴山 pack: the REAL enemy + allied deployment decoded from the stage
     * script's dispatch records ([LegacyRosterImporter]) on the COMPLETE 23×16 `terrainMap_1` (uncropped — the
     * board scroll #96 supports the full map), versus the designed 桃园三兄弟 trio. Unlike the curated [generate]
     * demo (a hand-picked 5-unit subset on an 8×7 crop), the roster and coordinates come straight from the
     * script, so this is the "真·整关" port. Allied NPCs deploy as ALLY faction — the engine groups PLAYER‖ALLY
     * as one side, so they fight the 黄巾 alongside the trio (and the enemy AI targets them too). Emitted under a
     * distinct content id; it does NOT overwrite the committed curated pack (which carries hand-added skills).
     *
     * Same mappers/pipeline as [generate]: every dispatched hero is mined from `dic_hero`/`dic_job`, granted a
     * generic basic attack (per-hero specials are a separate effort), and the win/lose objectives come from the
     * real script ([LegacyObjectiveImporter]) scoped to the now-full roster.
     */
    fun generateFullStage(extractedRoot: String): String {
        val root = File(extractedRoot)
        val jsonDir = File(root, "json")
        val terrainMap = read(File(root, "terrainJson"), "terrainMap_1.json")
        val (mapWidth, mapHeight) = mapDims(terrainMap)
        val scriptBytes = File(File(root, "Scenes"), DAXINGSHAN_SCRIPT).readBytes()
        val deployment = LegacyRosterImporter.importDeployment(scriptBytes, mapWidth, mapHeight)
        val placements = fullStagePlacements(deployment)
        val hids = placements.map { it.unit.removePrefix(HERO_PREFIX).toInt() }.toSet()
        val allHeroes = readArray(jsonDir, "dic_hero.json")
        val sources = sourcesFor(jsonDir, allHeroes, hids)
        val objectives = importObjectives(root, allHeroes, hids)
        val spec = MapBattleSpec(
            battleId = FULL_BATTLE_ID,
            mapId = "${FULL_BATTLE_ID}_map",
            protect = "${HERO_PREFIX}1", // 刘备 falling = defeat (fallback if the script declares no win/lose)
            placements = placements,
            win = objectives.win.ifEmpty { null },
            lose = objectives.lose.ifEmpty { null },
        )
        return LegacyBattleBuilder.toJson(grantBasicAttack(LegacyBattleBuilder.buildBattleOnMap(metaFull(), sources, terrainMap, spec)))
    }

    /**
     * Full-stage placements: the designed player trio (PLAYER) + every dispatched enemy (ENEMY) and ally (ALLY)
     * at its real coordinate and legacy level (coerced to the engine's minimum level 1).
     *
     * The engine enforces one unit per tile, but the legacy ore lets a few entries share a cell (S_00 has one:
     * a level-0 黄巾 spare on 程远志's tile). [distinctBy] keeps the FIRST placement per cell — players first,
     * then dispatch order — so the protagonist/general wins the tile and the duplicate spare is dropped
     * deterministically (a faithful single-occupant deployment, not a fabricated stack). Listing players first
     * is intentional: a player tile is reserved over any imported unit sharing it, so the design-input trio is
     * never evicted. Dropped entries are echoed by [surfaceDroppedCollisions] — counted, never silently capped.
     */
    private fun fullStagePlacements(deployment: LegacyRosterImporter.Deployment): List<Placement> {
        val players = FULL_PLAYERS.map { Placement("$HERO_PREFIX${it.hid}", it.x, it.y, level = it.level) }
        val imported = deployment.units.map { unit ->
            val faction = if (unit.side == LegacyRosterImporter.Side.ENEMY) {
                LegacyBattleBuilder.ENEMY_FACTION
            } else {
                LegacyBattleBuilder.ALLY_FACTION
            }
            Placement("$HERO_PREFIX${unit.hid}", unit.x, unit.y, level = maxOf(1, unit.level), faction = faction)
        }
        val all = players + imported
        val deployed = all.distinctBy { it.x to it.y }
        surfaceDroppedCollisions(all - deployed.toSet())
        return deployed
    }

    /** Echo any placements dropped because their tile was already taken (engine = one unit per tile). A faithful
     *  single-occupant deploy must SAY which legacy entries shared a cell, not just quietly shrink the roster —
     *  so a future stage that drops many units is visible at the converter boundary, not a silent cap. */
    private fun surfaceDroppedCollisions(dropped: List<Placement>) {
        if (dropped.isEmpty()) return
        println(
            "LegacyPackGenerator: dropped ${dropped.size} placement(s) on already-occupied tiles " +
                "(engine = one unit per tile): " + dropped.joinToString { "${it.unit}@(${it.x},${it.y})" },
        )
    }

    /** Build [LegacyTableSources] for [hids]: filter `dic_hero` to those heroes and `dic_job` to their classes,
     *  with the shared basic-attack skill + real terrain/move tables. Fails loud if any hid is absent. */
    private fun sourcesFor(jsonDir: File, allHeroes: List<JsonElement>, hids: Set<Int>): LegacyTableSources {
        val roster = allHeroes.filter { hidOf(it) in hids }
        require(roster.size == hids.size) {
            "extracted dic_hero is missing roster hids: ${hids - roster.mapNotNull { hidOf(it) }.toSet()}"
        }
        val jobIds = roster.mapNotNull { it.jsonObject["jobid"]?.jsonPrimitive?.int }.toSet()
        val jobs = readArray(jsonDir, "dic_job.json").filter { it.jsonObject["jobid"]?.jsonPrimitive?.int in jobIds }
        return LegacyTableSources(
            dicJob = encode(jobs),
            dicSkill = BASIC_ATTACK_JSON,
            dicHero = encode(roster),
            mapTerrain = read(jsonDir, "map_terrain.json"),
            dicJobWalk = read(jsonDir, "dic_jobWalk.json"),
            dicJobTerrain = read(jsonDir, "dic_jobTerrain.json"),
        )
    }

    private fun hidOf(hero: JsonElement): Int? = hero.jsonObject["hid"]?.jsonPrimitive?.int

    /** Read a legacy `terrainMap` JSON's declared (width, height) without mapping the whole grid. */
    private fun mapDims(terrainMapJson: String): Pair<Int, Int> {
        val obj = reader.parseToJsonElement(terrainMapJson.removePrefix("﻿")).jsonObject
        return obj.getValue("map_width").jsonPrimitive.int to obj.getValue("map_height").jsonPrimitive.int
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

    private fun metaFull(): PackMeta =
        PackMeta(contentId = "ccz_daxingshan_full", contentVersion = "0.1.0", mod = "trssgshz", entry = FULL_BATTLE_ID)

    private fun readArray(dir: File, name: String): List<JsonElement> =
        reader.parseToJsonElement(read(dir, name)).jsonArray

    private fun read(dir: File, name: String): String = File(dir, name).readText().removePrefix("﻿")

    private fun encode(rows: List<JsonElement>): String =
        writer.encodeToString(JsonElement.serializer(), JsonArray(rows))

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size in 2..3) { "usage: <extractedRoot> <outPath> [full]" }
        require(args.size < 3 || args[2] == "full") { "unknown mode '${args[2]}', expected 'full'" }
        val json = if (args.size == 3) generateFullStage(args[0]) else generate(args[0])
        File(args[1]).apply { parentFile?.mkdirs() }.writeText(json)
    }
}
