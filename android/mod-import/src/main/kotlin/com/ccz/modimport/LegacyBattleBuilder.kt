package com.ccz.modimport

import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.json.ContentJsonLoader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A `maps` entry in the native content-pack wire format (tiles reference terrain ids). */
@Serializable
data class PackMap(
    val id: String,
    val size: PackSize,
    val tileset: String,
    val tiles: List<List<String>>,
)

@Serializable
data class PackSize(val width: Int, val height: Int)

/** Event scripts wrapper: battle scripts (`s_scripts`) and cutscene scripts (`r_scripts`). */
@Serializable
data class PackEvents(
    @SerialName("s_scripts") val sScripts: List<PackBattle> = emptyList(),
    @SerialName("r_scripts") val rScripts: List<PackRScript> = emptyList(),
)

/** One cutscene script (`events.r_scripts`): an ordered list of scenario ops. */
@Serializable
data class PackRScript(
    val id: String,
    val ops: List<PackScenarioOp>,
)

/**
 * A scenario (cutscene) op. `type` is the polymorphic discriminator the native loader reads; only the fields
 * a given op needs are set and the writer omits null defaults, so a `dialogue` op serializes as
 * `{type, line}` and a `scene_transition` as `{type, target}` — matching the native `ScenarioOpDto` variants.
 */
@Serializable
data class PackScenarioOp(
    val type: String,
    val line: PackDialogueLine? = null,
    val target: String? = null,
) {
    companion object {
        const val DIALOGUE = "dialogue"
        const val SCENE_TRANSITION = "scene_transition"

        fun dialogue(speaker: String?, text: String): PackScenarioOp =
            PackScenarioOp(DIALOGUE, line = PackDialogueLine(speaker = speaker, text = text))

        fun sceneTransition(target: String): PackScenarioOp = PackScenarioOp(SCENE_TRANSITION, target = target)
    }
}

/** A `dialogue` op's line: an optional [speaker] (null = narration, key omitted) and the spoken [text]. */
@Serializable
data class PackDialogueLine(
    val speaker: String? = null,
    val text: String,
)

/** One battle script: deploy roster via `pre` spawns, with win/lose conditions. */
@Serializable
data class PackBattle(
    val id: String,
    val win: List<PackCondition>,
    val lose: List<PackCondition>,
    val pre: List<PackSpawn>,
)

/** A `spawn_unit` op. `type` is the polymorphic discriminator; `faction` overrides the unit's own. */
@Serializable
data class PackSpawn(
    val type: String,
    val unit: String,
    val at: PackPos,
    val faction: String? = null,
)

/** A win/lose condition op (`type` is the discriminator; `unit` only set for unit-scoped ones). */
@Serializable
data class PackCondition(
    val type: String,
    val unit: String? = null,
) {
    /** The content-pack win/lose discriminator strings, single-sourced for every producer (the battle
     *  builder and the legacy semantic mapper) so the wire-format ids cannot drift between them. */
    companion object {
        const val ANNIHILATE_ENEMIES = "annihilate_enemies"
        const val PROTECT_ALIVE = "protect_alive"
        const val DEFEAT_UNIT = "defeat_unit"
    }
}

@Serializable
data class PackPos(val x: Int, val y: Int)

/**
 * Where a ported unit deploys, which side it fights for, and the LEVEL it deploys at. The legacy ore
 * carries no usable per-hero deploy level (`dic_hero.level` is uniformly 1; `dic_turn` is an appearance
 * group, not a level), so deploy levels are a battle-spec design input — exactly the "战役元数据指定的
 * 出场等级" ADR 0006 names. A unit placed above level 1 is budgeted by its class growth × quality grade
 * at assembly time; this is what makes the growth/grade levers actually bite for ported heroes.
 */
data class Placement(val unit: String, val x: Int, val y: Int, val enemy: Boolean = false, val level: Int = 1)

/** A synthesized skirmish over ported data: a flat map plus a deploy-and-fight battle script. */
data class BattleSpec(
    val battleId: String,
    val width: Int,
    val height: Int,
    val terrainId: String,
    val protect: String,
    val placements: List<Placement>,
)

/** A battle fought on a *real* ported legacy map (its size/terrain come from the map, not the spec). */
data class MapBattleSpec(
    val battleId: String,
    val mapId: String,
    val protect: String,
    val placements: List<Placement>,
)

/**
 * Builds a *playable* native content pack from ported legacy tables plus a synthesized map and
 * battle script — the end-to-end seam that drives ported data through [ContentJsonLoader] and
 * (downstream) [com.ccz.contentpack.assembly.CampaignAssembler] into a real battle.
 *
 * The map/battle are synthesized (a flat field + a deploy roster) rather than ported from the legacy
 * `Scenes` scripts (`.eex_new`), which is a separate effort. Enemy sides reuse ported units via the
 * `spawn_unit` faction override, so no separate enemy roster is needed.
 */
object LegacyBattleBuilder {
    private const val SPAWN = "spawn_unit"
    private const val ANNIHILATE = PackCondition.ANNIHILATE_ENEMIES
    private const val PROTECT = PackCondition.PROTECT_ALIVE
    private const val ENEMY = "ENEMY"

    private val writer = Json { prettyPrint = true }

    /** Assemble ported tables + a synthesized map/battle into a full playable pack document. */
    fun buildBattle(meta: PackMeta, sources: LegacyTableSources, spec: BattleSpec): PackContent {
        require(spec.width > 0 && spec.height > 0) { "map size must be positive: ${spec.width}x${spec.height}" }
        require(spec.placements.isNotEmpty()) { "battle needs at least one placement" }
        val size = PackSize(spec.width, spec.height)
        val row = List(spec.width) { spec.terrainId }
        val map = PackMap(id = mapId(spec.battleId), size = size, tileset = "legacy", tiles = List(spec.height) { row })
        val battle = battleScript(spec.battleId, spec.protect, spec.placements, size, mapLabel = "")
        return assemble(meta, sources, battle, map, spec.placements)
    }

    /** Serialize a built battle pack as native content-pack JSON. */
    fun toJson(pack: PackContent): String = writer.encodeToString(PackContent.serializer(), pack)

    /** Build the battle pack and load it through the engine's content loader. */
    fun load(meta: PackMeta, sources: LegacyTableSources, spec: BattleSpec): NativeContent =
        ContentJsonLoader.load(toJson(buildBattle(meta, sources, spec)))

    /**
     * Build a playable pack that fights on a **real ported legacy map** (decrypted `terrainMap`):
     * its size and terrain come from the map. Any terrain id the map references but the `terrain`
     * catalog lacks (e.g. an edge/void id like `terrain_0`) is auto-filled as a default passable
     * terrain, so coverage validation passes without hand-curating the catalog.
     */
    fun buildBattleOnMap(
        meta: PackMeta,
        sources: LegacyTableSources,
        terrainMapJson: String,
        spec: MapBattleSpec,
    ): PackContent {
        require(spec.placements.isNotEmpty()) { "battle needs at least one placement" }
        val map = LegacyMapMapper.mapMap(terrainMapJson, spec.mapId)
        val battle = battleScript(spec.battleId, spec.protect, spec.placements, map.size, mapLabel = " '${spec.mapId}'")
        val pack = assemble(meta, sources, battle, map, spec.placements)
        // A real ported map can reference terrain ids absent from the imported catalog (edge/void ids);
        // auto-fill them as default passable terrain so coverage validation passes (synth maps need none).
        return pack.copy(tables = pack.tables.copy(terrain = pack.tables.terrain + missingTerrain(pack.tables.terrain, map)))
    }

    /** Build the real-map battle pack and load it through the engine's content loader. */
    fun loadOnMap(meta: PackMeta, sources: LegacyTableSources, terrainMapJson: String, spec: MapBattleSpec): NativeContent =
        ContentJsonLoader.load(toJson(buildBattleOnMap(meta, sources, terrainMapJson, spec)))

    /**
     * The deploy-and-fight battle script: annihilate-enemies to win, protect [protect] to avoid loss, and a
     * `pre` spawn per placement (faction-overridden for enemies). Single-sources the spawn mapping AND the
     * off-map bounds check for both builders — [size] is the bounding map and [mapLabel] names it in the
     * error message ("" for a synthesized field, " 'id'" for a real ported map).
     */
    private fun battleScript(
        battleId: String,
        protect: String,
        placements: List<Placement>,
        size: PackSize,
        mapLabel: String,
    ): PackBattle =
        PackBattle(
            id = battleId,
            win = listOf(PackCondition(ANNIHILATE)),
            lose = listOf(PackCondition(PROTECT, unit = protect)),
            pre = placements.map { p ->
                require(p.x in 0 until size.width && p.y in 0 until size.height) {
                    "placement of '${p.unit}' at (${p.x}, ${p.y}) is off the ${size.width}x${size.height} map$mapLabel"
                }
                PackSpawn(type = SPAWN, unit = p.unit, at = PackPos(p.x, p.y), faction = if (p.enemy) ENEMY else null)
            },
        )

    /**
     * Assemble the imported base pack with the [map], the roster stamped with each placement's deploy level,
     * and the single [battle] script. Single-sources the base-pack assembly for both builders; the real-map
     * builder layers its terrain coverage-fill on top of the returned pack.
     */
    private fun assemble(
        meta: PackMeta,
        sources: LegacyTableSources,
        battle: PackBattle,
        map: PackMap,
        placements: List<Placement>,
    ): PackContent {
        val base = LegacyContentImporter.buildPack(meta.copy(entry = battle.id), sources)
        return base.copy(
            tables = base.tables.copy(
                units = rosterWithDeployLevels(base.tables.units, placements),
                maps = listOf(map),
            ),
            events = PackEvents(sScripts = listOf(battle)),
        )
    }

    /**
     * Stamp each placement's deploy level onto its unit in the roster (units not placed keep their
     * imported level 1). The override is the one input that lets a ported hero deploy above level 1, so
     * its class growth × quality grade actually scale the assembled panel (ADR 0006); level 1 placements
     * leave the roster untouched, so a battle that names no levels is byte-identical to before.
     */
    private fun rosterWithDeployLevels(units: List<PackUnit>, placements: List<Placement>): List<PackUnit> {
        placements.forEach { require(it.level >= 1) { "placement of '${it.unit}' has non-positive deploy level ${it.level}" } }
        val levels = placements.associate { it.unit to it.level }
        return units.map { unit ->
            val level = levels[unit.identity.id] ?: unit.profile.level
            if (level == unit.profile.level) unit else unit.copy(profile = unit.profile.copy(level = level))
        }
    }

    /** Default passable terrain entries for every map tile id missing from [catalog] (coverage fill). */
    private fun missingTerrain(catalog: List<PackTerrain>, map: PackMap): List<PackTerrain> {
        val have = catalog.mapTo(HashSet()) { it.id }
        return map.tiles.flatten().toSortedSet().filterNot { it in have }
            .map { PackTerrain(id = it, name = it, moveCost = LegacyTerrainMapper.BASE_MOVE_COST) }
    }

    /** The map id derived for a battle (one synthesized map per battle). */
    fun mapId(battleId: String): String = "${battleId}_map"
}
