package com.ccz.core.battle

import com.ccz.core.event.BattleOp
import com.ccz.core.event.ScenarioOp
import com.ccz.core.model.Combatant

/**
 * Static inputs the op interpreter needs beyond [BattleState]. [reserves] holds
 * off-map unit templates a SpawnUnit op can place, keyed by the unit id the op
 * references ([BattleOp.SpawnUnit.unit]); built from validated content at a higher
 * layer (ContentEventValidator already checks spawn references), so a missing
 * template is rejected here rather than crashing. [map] is the battle's static
 * spatial model used to fail-closed a spawn onto an off-map or impassable tile; it
 * is optional because occupancy (the dynamic check) is always derivable from state,
 * so a null map only relaxes the bounds/passability checks, never the occupancy one.
 */
data class ScriptContext(
    val reserves: Map<String, Combatant> = emptyMap(),
    val map: BattleMap? = null,
)

/**
 * Interprets [BattleOp]s into state transitions + events. Pure and deterministic
 * (no RNG); ops apply in list order. Outcome ops respect the sticky rule. Ops the
 * core model cannot represent yet (GiveItem has no inventory; non-SetVar scenario
 * ops are presentation) are surfaced as events without faking state.
 */
internal object BattleOps {
    fun applyOps(state: BattleState, ops: List<BattleOp>, ctx: ScriptContext): Resolution {
        var current = state
        val events = mutableListOf<Event>()
        for (op in ops) {
            val resolution = applyOp(current, op, ctx)
            current = resolution.state
            events += resolution.events
        }
        return Resolution(current, events)
    }

    fun applyOp(state: BattleState, op: BattleOp, ctx: ScriptContext): Resolution = when (op) {
        is BattleOp.SpawnUnit -> spawn(state, op, ctx)
        is BattleOp.RemoveUnit -> remove(state, op.unit)
        is BattleOp.MoveUnit -> move(state, op)
        is BattleOp.SetHp -> setHp(state, op)
        is BattleOp.SetStatus -> setStatus(state, op)
        is BattleOp.GiveItem -> Resolution(state, listOf(Event.ItemGranted(op.to, op.item)))
        BattleOp.ForceWin -> endBattle(state, BattleOutcome.VICTORY)
        BattleOp.ForceLose -> endBattle(state, BattleOutcome.DEFEAT)
        is BattleOp.Script -> script(state, op.op)
    }

    private fun spawn(state: BattleState, op: BattleOp.SpawnUnit, ctx: ScriptContext): Resolution {
        val template = ctx.reserves[op.unit] ?: return rejected(state, op.unit, SpawnReject.NO_TEMPLATE)
        val obstruction = obstruction(state, op, ctx.map)
        if (obstruction != null) return rejected(state, op.unit, obstruction)
        val faction = op.faction ?: template.identity.faction
        val placed = template.copy(pos = op.at, identity = template.identity.copy(faction = faction))
        return Resolution(state.withUnit(placed), listOf(Event.UnitSpawned(placed.id)))
    }

    /**
     * First reason [op] cannot place onto its target tile, or null when clear.
     * Bounds/passability need the [map] (skipped when it is null); occupancy is
     * derived from living units other than the spawned id (a same-id respawn may
     * land on its own current tile, mirroring [CommandValidator]'s move check).
     */
    private fun obstruction(state: BattleState, op: BattleOp.SpawnUnit, map: BattleMap?): SpawnReject? {
        if (map != null) {
            if (!map.inBounds(op.at)) return SpawnReject.OUT_OF_BOUNDS
            if (!map.tileAt(op.at).passable) return SpawnReject.IMPASSABLE
        }
        if (op.at in occupancyOf(state, exclude = op.unit)) return SpawnReject.OCCUPIED
        return null
    }

    private fun rejected(state: BattleState, unit: String, reason: SpawnReject): Resolution =
        Resolution(state, listOf(Event.SpawnRejected(unit, reason)))

    private fun remove(state: BattleState, unit: String): Resolution =
        if (unit in state.units) {
            Resolution(state.copy(units = state.units - unit), listOf(Event.UnitRemoved(unit)))
        } else {
            Resolution(state, emptyList())
        }

    private fun move(state: BattleState, op: BattleOp.MoveUnit): Resolution {
        val unit = state.units[op.unit] ?: return Resolution(state, emptyList())
        return Resolution(state.withUnit(unit.copy(pos = op.to)), listOf(Event.Moved(unit.id, unit.pos, op.to)))
    }

    private fun setHp(state: BattleState, op: BattleOp.SetHp): Resolution {
        val unit = state.units[op.unit] ?: return Resolution(state, emptyList())
        val hp = op.hp.coerceIn(0, unit.hpMax)
        val events = mutableListOf<Event>(Event.HpSet(unit.id, hp))
        if (hp <= 0) events += Event.Died(unit.id)
        return Resolution(state.withUnit(unit.withHp(hp)), events)
    }

    private fun setStatus(state: BattleState, op: BattleOp.SetStatus): Resolution {
        val unit = state.units[op.unit] ?: return Resolution(state, emptyList())
        return Resolution(
            state.withUnit(unit.copy(statuses = unit.statuses + op.status)),
            listOf(Event.StatusApplied(unit.id, op.status)),
        )
    }

    private fun endBattle(state: BattleState, outcome: BattleOutcome): Resolution =
        if (state.outcome == BattleOutcome.ONGOING) {
            Resolution(state.withOutcome(outcome), listOf(Event.BattleEnded(outcome)))
        } else {
            Resolution(state, emptyList())
        }

    private fun script(state: BattleState, op: ScenarioOp): Resolution = when (op) {
        is ScenarioOp.SetVar ->
            Resolution(state.withVar(op.name, op.value), listOf(Event.VarSet(op.name, op.value)))
        else -> Resolution(state, listOf(Event.Scenario(op)))
    }
}
