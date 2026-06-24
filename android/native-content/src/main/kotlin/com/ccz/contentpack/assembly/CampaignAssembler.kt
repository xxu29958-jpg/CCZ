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
import com.ccz.core.event.BattleOp
import com.ccz.core.event.BattleTrigger
import com.ccz.core.event.SScript
import com.ccz.core.event.TriggerCondition
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.CounterRelation
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
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
 * validates commands against, the deployed opening [initialState], the [script] whose
 * win/lose lists decide the outcome, and the [scriptContext] (reserves + map) the battle
 * loop threads into [com.ccz.core.battle.TriggerRunner] so the script's `pre` deployment and
 * `mid` triggers draw from the same reserves and honor the same map. This is plain input
 * handed to game-core — the holder computes no combat truth; it only carries what
 * [CampaignAssembler] derived from content.
 */
data class BattleSetup(
    val context: BattleContext,
    val initialState: BattleState,
    val script: SScript,
    val scriptContext: ScriptContext,
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
 * It also checks every map coordinate in the selected battle script against the selected
 * [MapDef] before running the battle, including mid-trigger reach conditions/actions and
 * win/lose reach tiles.
 *
 * Deployment fail-closes on the placement rejections the engine *surfaces* as events
 * (SpawnRejected / MoveRejected / HpSetRejected). Deploy a roster with SpawnUnit ops: a
 * SpawnUnit that lands off-board, on an impassable tile, on an occupied tile, or names a unit
 * with no reserve is caught here. It also preflights `pre` MoveUnit/RemoveUnit ordering so
 * opening deployment cannot silently no-op a unit that has not been spawned yet, while leaving
 * game-core's documented mid-battle fail-safe unchanged.
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
        validatePreDeploymentOrder(script)
        validateScriptMapBounds(script, mapDef)
        val map = battleMap(mapDef, content.tables.terrain)
        val context = BattleContext(
            map = map,
            classes = classes(content.tables.classes),
            skills = skills(content.tables.skills),
            loadouts = loadouts(content.tables.units),
        )
        // One ScriptContext for the whole battle: reserves + map drive the `pre` deployment here AND the
        // `mid` triggers the battle loop later runs through TriggerRunner.tick (so a mid spawn draws the
        // same reserves and lands on the same map).
        val scriptContext = ScriptContext(reserves = BattleAssembler.reserves(content.tables.units), map = map)
        return BattleSetup(context, deploy(script, scriptContext, seed), script, scriptContext)
    }

    private fun validatePreDeploymentOrder(script: SScript) {
        val deployed = mutableSetOf<String>()
        val invalid = mutableListOf<String>()
        script.pre.forEachIndexed { index, op ->
            when (op) {
                is BattleOp.SpawnUnit -> deployed += op.unit
                is BattleOp.MoveUnit ->
                    if (op.unit !in deployed) invalid += preDeploymentRef(script.id, index, "move", op.unit)
                is BattleOp.RemoveUnit ->
                    if (!deployed.remove(op.unit)) invalid += preDeploymentRef(script.id, index, "remove", op.unit)
                is BattleOp.Script,
                is BattleOp.SetHp,
                is BattleOp.SetStatus,
                is BattleOp.GiveItem,
                BattleOp.ForceWin,
                BattleOp.ForceLose,
                -> Unit
            }
        }
        if (invalid.isNotEmpty()) {
            throw CampaignAssemblyException(
                "battle '${script.id}' pre-deployment references unit(s) not currently deployed: " +
                    invalid.joinToString("; "),
            )
        }
    }

    private fun preDeploymentRef(scriptId: String, index: Int, op: String, unit: String): String =
        "events.sScripts[$scriptId].pre[$index].$op=$unit"

    private fun validateScriptMapBounds(script: SScript, mapDef: MapDef) {
        val outOfBounds = scriptPositions(script).filterNot { mapDef.contains(it.pos) }
        if (outOfBounds.isNotEmpty()) {
            throw CampaignAssemblyException(
                "battle '${script.id}' references out-of-bounds tile(s) on map '${mapDef.id}': " +
                    outOfBounds.joinToString("; ") { "${it.path}=(${it.pos.x}, ${it.pos.y})" },
            )
        }
    }

    private fun scriptPositions(script: SScript): List<ScriptPosRef> =
        winLosePositions(script.id, "win", script.win) +
            winLosePositions(script.id, "lose", script.lose) +
            battleOpPositions("events.sScripts[${script.id}].pre", script.pre) +
            script.mid.flatMapIndexed { index, trigger -> triggerPositions(script.id, index, trigger) } +
            battleOpPositions("events.sScripts[${script.id}].post", script.post)

    private fun triggerPositions(scriptId: String, index: Int, trigger: BattleTrigger): List<ScriptPosRef> =
        triggerConditionPositions(scriptId, index, trigger.whenCondition) +
            battleOpPositions("events.sScripts[$scriptId].mid[$index].actions", trigger.actions)

    private fun triggerConditionPositions(
        scriptId: String,
        index: Int,
        condition: TriggerCondition,
    ): List<ScriptPosRef> =
        when (condition) {
            is TriggerCondition.UnitReach -> listOf(
                ScriptPosRef("events.sScripts[$scriptId].mid[$index].when.pos", condition.pos),
            )
            is TriggerCondition.TurnStart,
            is TriggerCondition.UnitDead,
            is TriggerCondition.HpBelow,
            is TriggerCondition.EnemyCountBelow,
            is TriggerCondition.VarEquals,
            -> emptyList()
        }

    private fun winLosePositions(scriptId: String, side: String, conditions: List<WinLoseCondition>): List<ScriptPosRef> =
        conditions.mapIndexedNotNull { index, condition ->
            when (condition) {
                is WinLoseCondition.ReachTile -> ScriptPosRef("events.sScripts[$scriptId].$side[$index].pos", condition.pos)
                is WinLoseCondition.AnnihilateEnemies,
                is WinLoseCondition.UnitDead,
                is WinLoseCondition.SurviveTurns,
                is WinLoseCondition.ProtectAlive,
                is WinLoseCondition.DefeatUnit,
                -> null
            }
        }

    private fun battleOpPositions(path: String, ops: List<BattleOp>): List<ScriptPosRef> =
        ops.mapIndexedNotNull { index, op ->
            when (op) {
                is BattleOp.SpawnUnit -> ScriptPosRef("$path[$index].at", op.at)
                is BattleOp.MoveUnit -> ScriptPosRef("$path[$index].to", op.to)
                is BattleOp.Script,
                is BattleOp.RemoveUnit,
                is BattleOp.SetHp,
                is BattleOp.SetStatus,
                is BattleOp.GiveItem,
                BattleOp.ForceWin,
                BattleOp.ForceLose,
                -> null
            }
        }

    private fun MapDef.contains(pos: Pos): Boolean =
        pos.x in 0 until size.width && pos.y in 0 until size.height

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
        defs.associate {
            it.id to UnitClass(it.id, it.name, it.movement.moveType, it.movement.move, counters(it), it.movement.terrainCost)
        }

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

    private fun deploy(script: SScript, ctx: ScriptContext, seed: Long): BattleState {
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

    private data class ScriptPosRef(val path: String, val pos: Pos)
}
