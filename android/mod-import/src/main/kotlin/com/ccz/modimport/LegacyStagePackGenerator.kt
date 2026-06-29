package com.ccz.modimport

import com.ccz.contentpack.ContentValidator
import com.ccz.contentpack.assembly.CampaignAssembler
import com.ccz.contentpack.json.ContentJsonLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Production generator for one catalog stage (`legacy_stage_<gkid>`): reads the local decrypted legacy tables,
 * the stage's `S_<gkid-1>.eex_new`, and `terrainMap_<gkid>.json`, then emits a native content pack that is
 * validated and assembled before it is written. This promotes planner evidence into a committed app runtime pack
 * without making Android load legacy files.
 */
object LegacyStagePackGenerator {
    private const val SOURCE_MOD = "trssgshz"
    private const val HERO_PREFIX = "hero_"
    private const val BASIC_ATTACK_ID = "skill_1"
    private const val BASIC_ATTACK_JSON = """[{"skid":1,"name":"普攻","type":0,"hurt_num":100,"mp_consume":0}]"""

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    private data class PlayerSeed(val hid: Int, val level: Int)

    private val defaultPlayers = listOf(
        PlayerSeed(hid = 1, level = 6),
        PlayerSeed(hid = 2, level = 8),
        PlayerSeed(hid = 3, level = 7),
    )

    fun generate(extractedRoot: String, stageId: Int): String {
        require(stageId > 0) { "stageId must be positive: $stageId" }
        val root = File(extractedRoot)
        val jsonDir = File(root, "json")
        val scene = File(File(root, "Scenes"), scriptName(stageId))
        val terrainMap = read(File(root, "terrainJson"), mapName(stageId))
        val map = LegacyMapMapper.mapMap(terrainMap, mapId(stageId))
        val scriptBytes = scene.readBytes()
        val profile = LegacyRosterImporter.detectOpcodeProfile(scriptBytes, map.size.width, map.size.height)
        val deployment = LegacyRosterImporter.importDeployment(scriptBytes, map.size.width, map.size.height, profile)
        require(deployment.units.isNotEmpty()) { "stage $stageId has no opening deployment" }
        requireNoDeploymentCollisions(stageId, deployment)

        val imported = deployment.units.map { unit ->
            Placement(
                unit = "$HERO_PREFIX${unit.hid}",
                x = unit.x,
                y = unit.y,
                level = maxOf(1, unit.level),
                faction = if (unit.side == LegacyRosterImporter.Side.ENEMY) {
                    LegacyBattleBuilder.ENEMY_FACTION
                } else {
                    LegacyBattleBuilder.ALLY_FACTION
                },
            )
        }
        val players = playerPlacements(map, imported)
        val placements = players + imported
        val allHeroes = readArray(jsonDir, "dic_hero.json")
        val hids = placements.mapTo(HashSet()) { it.unit.removePrefix(HERO_PREFIX).toInt() }
        val objectives = importObjectives(allHeroes, hids, scriptBytes, profile)
        val battleId = battleId(stageId)
        val pack = grantBasicAttack(
            LegacyBattleBuilder.buildBattleOnMap(
                meta = PackMeta(
                    contentId = "trssgshz_$battleId",
                    contentVersion = "0.1.0",
                    mod = SOURCE_MOD,
                    entry = battleId,
                ),
                sources = sourcesFor(jsonDir, allHeroes, hids),
                terrainMapJson = terrainMap,
                spec = MapBattleSpec(
                    battleId = battleId,
                    mapId = mapId(stageId),
                    protect = "${HERO_PREFIX}1",
                    placements = placements,
                    win = objectives.win.ifEmpty { null },
                    lose = objectives.lose.ifEmpty { null },
                ),
            ),
        )
        val json = LegacyBattleBuilder.toJson(pack)
        validateAssembles(json, battleId, mapId(stageId))
        return json
    }

    private fun requireNoDeploymentCollisions(stageId: Int, deployment: LegacyRosterImporter.Deployment) {
        val collisions = deployment.units.groupBy { it.x to it.y }.filterValues { it.size > 1 }
        require(collisions.isEmpty()) {
            "stage $stageId has deployment collisions; run planLegacyStages and promote it only after applying proposals"
        }
    }

    private fun playerPlacements(map: PackMap, imported: List<Placement>): List<Placement> {
        val occupied = imported.mapTo(HashSet()) { it.x to it.y }
        val passable = map.tiles.flatMapIndexed { y, row ->
            row.mapIndexedNotNull { x, terrain -> if (terrain != LegacyMapMapper.VOID_TERRAIN_ID) x to y else null }
        }.filterNot { it in occupied }
        val center = map.size.width / 2
        val slots = passable.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { kotlin.math.abs(it.first - center) }.thenBy { it.first })
            .take(defaultPlayers.size)
        require(slots.size == defaultPlayers.size) { "map '${map.id}' has only ${slots.size} free player deployment tiles" }
        return defaultPlayers.zip(slots).map { (player, pos) ->
            Placement("$HERO_PREFIX${player.hid}", x = pos.first, y = pos.second, level = player.level)
        }
    }

    private fun importObjectives(
        allHeroes: List<JsonElement>,
        hids: Set<Int>,
        scriptBytes: ByteArray,
        profile: LegacyEexOpcodeProfile,
    ): LegacyObjectiveImporter.ImportedObjectives {
        val nameToId = allHeroes.associate { hero ->
            (hero.jsonObject["name"]?.jsonPrimitive?.content ?: "") to "$HERO_PREFIX${hero.jsonObject["hid"]?.jsonPrimitive?.int}"
        }
        val rosterIds = hids.mapTo(HashSet()) { "$HERO_PREFIX$it" }
        return LegacyObjectiveImporter.importObjectives(scriptBytes, rosterIds, nameToId::get, profile)
    }

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
            dicJobWalk = optionalRead(File(jsonDir, "dic_jobWalk.json")),
            dicJobTerrain = optionalRead(File(jsonDir, "dic_jobTerrain.json")),
        )
    }

    private fun grantBasicAttack(pack: PackContent): PackContent {
        val units = pack.tables.units.map { it.copy(loadout = PackLoadout(skills = listOf(BASIC_ATTACK_ID))) }
        return pack.copy(tables = pack.tables.copy(units = units))
    }

    private fun validateAssembles(json: String, battleId: String, mapId: String) {
        val content = ContentJsonLoader.load(json)
        val issues = ContentValidator.validate(content)
        require(issues.isEmpty()) { issues.joinToString("; ") { "${it.path}: ${it.message}" } }
        CampaignAssembler.assemble(content, battleId, mapId)
    }

    private fun hidOf(hero: JsonElement): Int? = hero.jsonObject["hid"]?.jsonPrimitive?.int

    private fun readArray(dir: File, name: String): List<JsonElement> = reader.parseToJsonElement(read(dir, name)).jsonArray

    private fun read(dir: File, name: String): String = File(dir, name).readText(Charsets.UTF_8).removePrefix("\uFEFF")

    private fun optionalRead(file: File): String =
        if (file.isFile) file.readText(Charsets.UTF_8).removePrefix("\uFEFF") else ""

    private fun encode(rows: List<JsonElement>): String =
        writer.encodeToString(JsonElement.serializer(), JsonArray(rows))

    private fun scriptName(stageId: Int): String = "S_${(stageId - 1).toString().padStart(2, '0')}.eex_new"

    private fun mapName(stageId: Int): String = "terrainMap_$stageId.json"

    fun battleId(stageId: Int): String = "legacy_stage_$stageId"

    fun mapId(stageId: Int): String = "legacy_stage_${stageId}_map"

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3 && args[0].isNotBlank() && args[1].isNotBlank()) {
            "usage: <extractedRoot> <outPath> <stageId>"
        }
        val stageId = args[2].toIntOrNull() ?: error("stageId must be an integer: '${args[2]}'")
        File(args[1]).apply { parentFile?.mkdirs() }.writeText(generate(args[0], stageId), Charsets.UTF_8)
    }
}
