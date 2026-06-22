package com.ccz.core.battle

import com.ccz.core.event.ScenarioOp
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.Combatant

sealed interface Command {
    data class Move(val unit: String, val to: Pos) : Command
    data class Attack(val attacker: String, val target: String, val skill: String) : Command
    data class EndTurn(val faction: Faction) : Command
}

sealed interface Event {
    data class Moved(val unit: String, val from: Pos, val to: Pos) : Event
    data class Missed(val attacker: String, val target: String) : Event
    data class Damaged(
        val target: String,
        val amount: Int,
        val crit: Boolean,
        val combo: Boolean,
        val broke: Boolean,
    ) : Event

    data class Died(val unit: String) : Event
    data class TurnEnded(val faction: Faction) : Event
    data class BattleEnded(val outcome: BattleOutcome) : Event
    data class UnitSpawned(val unit: String) : Event
    data class SpawnRejected(val unit: String, val reason: PlacementReject) : Event
    data class MoveRejected(val unit: String, val reason: PlacementReject) : Event
    data class UnitRemoved(val unit: String) : Event
    data class HpSet(val unit: String, val hp: Int) : Event
    data class HpSetRejected(val unit: String, val reason: PlacementReject) : Event
    data class StatusApplied(val unit: String, val status: String) : Event
    data class ItemGranted(val unit: String, val item: String) : Event
    data class VarSet(val name: String, val value: Int) : Event
    data class Scenario(val op: ScenarioOp) : Event
}

/** Battle result from the player side's perspective. Sticky once decided. */
enum class BattleOutcome { ONGOING, VICTORY, DEFEAT }

/**
 * Why an op-driven placement could not put its unit onto a target tile; the op is a
 * fail-closed no-op (state untouched) that surfaces [Event.SpawnRejected] /
 * [Event.MoveRejected] / [Event.HpSetRejected] so the presentation layer can see why
 * the unit did not move instead of a unit silently stacking on an occupied tile or
 * sliding off the board. A SetHp that revives a dead unit (raising it above 0 onto the
 * tile it retained while dead) is a placement too, so it honors the same invariant.
 * [OCCUPIED] is always enforced (occupancy derives from state); [OUT_OF_BOUNDS] and
 * [IMPASSABLE] fire only when a [ScriptContext.map] is supplied, which the
 * content-assembly path does not yet thread in (it arrives with the battle driver
 * layer), so those two are dormant in production today. [NO_TEMPLATE] is spawn-only
 * (MoveUnit / SetHp act on a unit already on the board), so only a SpawnRejected carries it.
 */
enum class PlacementReject { NO_TEMPLATE, OUT_OF_BOUNDS, IMPASSABLE, OCCUPIED }

/**
 * Script/event-driven progression carried alongside the tactical state: the
 * decided outcome, scenario variables, and the ids of triggers that have already
 * fired. Bundled so [BattleState] stays within its constructor-parameter budget
 * and so all event-runner state evolves through one immutable value.
 */
data class BattleProgress(
    val outcome: BattleOutcome = BattleOutcome.ONGOING,
    val vars: Map<String, Int> = emptyMap(),
    val firedTriggers: Set<String> = emptySet(),
)

data class BattleState(
    val units: Map<String, Combatant>,
    val turn: Int,
    val active: Faction,
    val rngState: Long,
    val progress: BattleProgress = BattleProgress(),
) {
    val outcome: BattleOutcome get() = progress.outcome

    fun unit(id: String): Combatant = units.getValue(id)
    fun withUnit(unit: Combatant): BattleState = copy(units = units + (unit.id to unit))
    fun varValue(name: String): Int = progress.vars[name] ?: 0
    fun withVar(name: String, value: Int): BattleState = copy(progress = progress.copy(vars = progress.vars + (name to value)))
    fun withOutcome(outcome: BattleOutcome): BattleState = copy(progress = progress.copy(outcome = outcome))
    fun hasFired(id: String): Boolean = id in progress.firedTriggers
    fun markFired(id: String): BattleState = copy(progress = progress.copy(firedTriggers = progress.firedTriggers + id))
}

data class Resolution(val state: BattleState, val events: List<Event>)

