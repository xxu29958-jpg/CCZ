package com.ccz.contentpack.assembly

import com.ccz.contentpack.ClassDef
import com.ccz.contentpack.MapDef
import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.SkillDef
import com.ccz.contentpack.TerrainDef
import com.ccz.contentpack.UnitDef
import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleMap
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Event
import com.ccz.core.battle.MapTile
import com.ccz.core.battle.ScriptContext
import com.ccz.core.battle.TriggerRunner
import com.ccz.core.event.SScript
import com.ccz.core.model.CounterRelation
import com.ccz.core.model.Faction
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
import com.ccz.core.model.UnitClass

/**
 * Raised when a content pack cannot be assembled into a runnable battle — a missing
 * script/map id, a tile naming an unknown terrain, or a deployment op the engine
 * rejected (off-board / impassable / occupied tile). Fail-closed: the assembler never
 * fabricates a half-built battle, it stops loudly so the authoring bug surfaces at load
 * instead of as a silently-missing unit on the board.
 */
class CampaignAssemblyException(message: String) : RuntimeException(message)

/**
 * The runnable battle a content pack assembles into: the immutable [context] game-core
 * validates commands against, the deployed opening [initialState], and the [script] whose
 * win/lose lists decide the outcome. This is plain input handed to game-core — the holder
 * computes no combat truth; it only carries what [CampaignAssembler] derived from content.
 */
data class BattleSetup(
    val context: BattleContext,
    val initialState: BattleState,
    val script: SScript,
)

/**
 * Turns a validated [NativeContent] pack into a [BattleSetup] the presentation layer can
 * drive through game-core, the content-driven counterpart to the hardcoded seeds the app
 * shipped before a campaign driver existed. It maps content tables onto the engine's input
 * value objects (terrain+map → [BattleMap], classes → [UnitClass], skills → [Skill], unit
 * loadouts → the loadout table) and **deploys** the opening roster by running the battle
 * script's `pre` ops (SpawnUnit drawing from [BattleAssembler] reserves) through the
 * authoritative [TriggerRunner.applyPre] — the same path scripted spawns use mid-battle, so
 * placement honors the single-occupant invariant and, because the [BattleMap] is threaded
 * into the [ScriptContext], its bounds/passability checks too (no second placement codepath
 * in the driver).
 *
 * Content is assumed already validated by [com.ccz.contentpack.ContentValidator] (unique ids,
 * resolvable references); the assembler still guards fail-closed the references it actually
 * dereferences — the entry script/map ids, each tile's terrain id, and every deployment op's
 * landing — so a pack that slips past validation cannot quietly produce a broken battle.
 *
 * Deployment fail-closes on the placement rejections the engine *surfaces* as events
 * (SpawnRejected / MoveRejected / HpSetRejected). Deploy a roster with SpawnUnit ops: a
 * SpawnUnit that lands off-board, on an impassable tile, on an occupied tile, or names a unit
 * with no reserve is caught here. One gap is honestly out of reach — a `pre` MoveUnit/RemoveUnit
 * naming a unit not yet on the board silently no-ops in [com.ccz.core.battle.BattleOps] (its
 * documented mid-battle fail-safe) without an event, so the assembler cannot see it; deploy via
 * SpawnUnit and treat such ops in `pre` as an authoring error (see KNOWN_ISSUES).
 */
object CampaignAssembler {
    private const val DEFAULT_SEED = 1L

    /**
     * Assembles the battle [battleScriptId] fought on [mapId] from [content]. [seed] is the
     * initial RNG state (deterministic; the resolver advances it). Throws
     * [CampaignAssemblyException] if either id is absent, a tile names an unknown terrain, or
     * the script's `pre` deployment is rejected by the engine.
     */
    fun assemble(
        content: NativeContent,
        battleScriptId: String,
        mapId: String,
        seed: Long = DEFAULT_SEED,
    ): BattleSetup {
        val script = content.events.sScripts.firstOrNull { it.id == battleScriptId }
            ?: throw CampaignAssemblyException("no s-script '$battleScriptId' in content '${content.manifest.contentId}'")
        val mapDef = content.tables.maps.firstOrNull { it.id == mapId }
            ?: throw CampaignAssemblyException("no map '$mapId' in content '${content.manifest.contentId}'")
        val map = battleMap(mapDef, content.tables.terrain)
        val context = BattleContext(
            map = map,
            classes = classes(content.tables.classes),
            skills = skills(content.tables.skills),
            loadouts = loadouts(content.tables.units),
        )
        return BattleSetup(context, deploy(content.tables.units, script, map, seed), script)
    }

    private fun battleMap(mapDef: MapDef, terrain: List<TerrainDef>): BattleMap {
        val byId = terrain.associateBy { it.id }
        val rows = mapDef.tiles.map { row -> row.map { tile(it, byId) } }
        return BattleMap(mapDef.size.width, mapDef.size.height, rows)
    }

    private fun tile(terrainId: String, byId: Map<String, TerrainDef>): MapTile {
        val def = byId[terrainId]
            ?: throw CampaignAssemblyException("map references unknown terrain '$terrainId'")
        return MapTile(terrainId = def.id, moveCost = def.moveCost, passable = def.passable)
    }

    private fun classes(defs: List<ClassDef>): Map<String, UnitClass> =
        defs.associate { it.id to UnitClass(it.id, it.name, it.movement.moveType, it.movement.move, counters(it)) }

    private fun counters(def: ClassDef): Map<String, CounterRelation> =
        def.combat.counters.mapValues { (cls, relation) ->
            runCatching { CounterRelation.valueOf(relation) }.getOrElse {
                throw CampaignAssemblyException("class '${def.id}' counter '$cls' names unknown relation '$relation'")
            }
        }

    private fun skills(defs: List<SkillDef>): Map<String, Skill> =
        defs.associate {
            it.id to Skill(it.id, it.name, it.kind, it.powerCoeff, RangeSpec(it.use.range.min, it.use.range.max))
        }

    // Only units that declare a loadout enter the table; an empty loadout means "unconstrained"
    // (any table skill) per BattleContext.loadoutAllows, so omitting it preserves that default
    // rather than locking the unit out of every skill.
    private fun loadouts(units: List<UnitDef>): Map<String, List<String>> =
        units.filter { it.loadout.skills.isNotEmpty() }.associate { it.id to it.loadout.skills }

    private fun deploy(units: List<UnitDef>, script: SScript, map: BattleMap, seed: Long): BattleState {
        val ctx = ScriptContext(reserves = BattleAssembler.reserves(units), map = map)
        val empty = BattleState(units = emptyMap(), turn = 1, active = Faction.PLAYER, rngState = seed)
        val resolution = TriggerRunner.applyPre(empty, script, ctx)
        val rejects = resolution.events.mapNotNull(::rejectMessage)
        if (rejects.isNotEmpty()) {
            throw CampaignAssemblyException("battle '${script.id}' deployment rejected: ${rejects.joinToString("; ")}")
        }
        return resolution.state
    }

    private fun rejectMessage(event: Event): String? = when (event) {
        is Event.SpawnRejected -> "spawn ${event.unit} (${event.reason})"
        is Event.MoveRejected -> "move ${event.unit} (${event.reason})"
        is Event.HpSetRejected -> "set-hp ${event.unit} (${event.reason})"
        else -> null
    }
}
