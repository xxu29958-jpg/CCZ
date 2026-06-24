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

/** Battle scripts wrapper (`events.s_scripts`). */
@Serializable
data class PackEvents(
    @SerialName("s_scripts") val sScripts: List<PackBattle> = emptyList(),
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
)

@Serializable
data class PackPos(val x: Int, val y: Int)

/** Where a ported unit deploys, and which side it fights for. */
data class Placement(val unit: String, val x: Int, val y: Int, val enemy: Boolean = false)

/** A synthesized skirmish over ported data: a flat map plus a deploy-and-fight battle script. */
data class BattleSpec(
    val battleId: String,
    val width: Int,
    val height: Int,
    val terrainId: String,
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
    private const val ANNIHILATE = "annihilate_enemies"
    private const val PROTECT = "protect_alive"
    private const val ENEMY = "ENEMY"

    private val writer = Json { prettyPrint = true }

    /** Assemble ported tables + a synthesized map/battle into a full playable pack document. */
    fun buildBattle(meta: PackMeta, sources: LegacyTableSources, spec: BattleSpec): PackContent {
        require(spec.width > 0 && spec.height > 0) { "map size must be positive: ${spec.width}x${spec.height}" }
        require(spec.placements.isNotEmpty()) { "battle needs at least one placement" }
        val row = List(spec.width) { spec.terrainId }
        val map = PackMap(
            id = mapId(spec.battleId),
            size = PackSize(spec.width, spec.height),
            tileset = "legacy",
            tiles = List(spec.height) { row },
        )
        val battle = PackBattle(
            id = spec.battleId,
            win = listOf(PackCondition(ANNIHILATE)),
            lose = listOf(PackCondition(PROTECT, unit = spec.protect)),
            pre = spec.placements.map { p ->
                require(p.x in 0 until spec.width && p.y in 0 until spec.height) {
                    "placement of '${p.unit}' at (${p.x}, ${p.y}) is off the ${spec.width}x${spec.height} map"
                }
                PackSpawn(type = SPAWN, unit = p.unit, at = PackPos(p.x, p.y), faction = if (p.enemy) ENEMY else null)
            },
        )
        val base = LegacyContentImporter.buildPack(meta.copy(entry = spec.battleId), sources)
        return base.copy(
            tables = base.tables.copy(maps = listOf(map)),
            events = PackEvents(sScripts = listOf(battle)),
        )
    }

    /** Serialize a built battle pack as native content-pack JSON. */
    fun toJson(pack: PackContent): String = writer.encodeToString(PackContent.serializer(), pack)

    /** Build the battle pack and load it through the engine's content loader. */
    fun load(meta: PackMeta, sources: LegacyTableSources, spec: BattleSpec): NativeContent =
        ContentJsonLoader.load(toJson(buildBattle(meta, sources, spec)))

    /** The map id derived for a battle (one synthesized map per battle). */
    fun mapId(battleId: String): String = "${battleId}_map"
}
