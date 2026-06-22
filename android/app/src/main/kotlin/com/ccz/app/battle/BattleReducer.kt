package com.ccz.app.battle

import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Command
import com.ccz.core.battle.Event
import com.ccz.core.battle.Gameplay
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

internal const val MAX_LOG_LINES = 6

/** Side label for a faction; PLAYER and ALLY share the player's turn (see sameSide in core). */
internal fun sideLabel(faction: Faction): String = when (faction) {
    Faction.PLAYER, Faction.ALLY -> "Player"
    Faction.ENEMY -> "Enemy"
}

/**
 * Immutable snapshot the battle UI renders: the authoritative [BattleState] (owned by
 * game-core and replaced wholesale on every accepted command — never edited here), plus
 * the transient selection, the move destinations and attack-target ids game-core reported
 * for it, and a short human-readable event log.
 */
data class BattleUiState(
    val state: BattleState,
    val selected: String? = null,
    val destinations: Set<Pos> = emptySet(),
    val targets: Set<String> = emptySet(),
    val log: List<String> = emptyList(),
)

/**
 * Pure presentation reducer: turns a tap or end-turn into the next [BattleUiState] by
 * asking [Gameplay] what is legal and submitting commands through it. It holds NO combat
 * authority — it never computes damage, decides who is in range, moves a unit itself, or
 * mutates [BattleState]; every state change is exactly whatever [Gameplay.submit] returns,
 * and every highlight is exactly what a [Gameplay] read-only query reported. [basicAttackSkill]
 * is the skill id a tapped attack is submitted with (a single basic attack for now; a
 * per-unit skill picker is a later slice). Kept free of Android types so it runs under the
 * plain-JVM unit-test gate.
 */
class BattleReducer(private val context: BattleContext, private val basicAttackSkill: String) {
    fun initial(state: BattleState): BattleUiState =
        BattleUiState(state = state, log = listOf("Battle start — ${sideLabel(state.active)}'s turn"))

    /**
     * Tap routing, in priority order: while a unit is selected, tapping a tile that holds one of
     * the targets game-core reported for it attacks that unit; otherwise tapping a unit game-core
     * reports legal moves for (living, active side) selects it (and exposes its move + attack
     * options); tapping a highlighted destination while a unit is selected moves it; anything
     * else clears the selection.
     */
    fun tapTile(ui: BattleUiState, pos: Pos): BattleUiState {
        val unitHere = ui.state.units.values.firstOrNull { it.alive && it.pos == pos }
        val selected = ui.selected
        if (selected != null && unitHere != null && unitHere.id in ui.targets) {
            return submitAttack(ui, selected, unitHere.id)
        }
        if (unitHere != null) {
            val destinations = Gameplay.legalDestinations(ui.state, unitHere.id, context)
            return if (destinations.isEmpty()) clearSelection(ui)
            else ui.copy(
                selected = unitHere.id,
                destinations = destinations,
                targets = Gameplay.legalTargets(ui.state, unitHere.id, basicAttackSkill, context),
            )
        }
        return if (selected != null && pos in ui.destinations) submitMove(ui, selected, pos)
        else clearSelection(ui)
    }

    fun endTurn(ui: BattleUiState): BattleUiState =
        when (val outcome = Gameplay.submit(ui.state, Command.EndTurn(ui.state.active), context)) {
            is Gameplay.Outcome.Accepted -> {
                val next = outcome.resolution.state
                clearSelection(ui).copy(
                    state = next,
                    log = appendLog(ui.log, "—— ${sideLabel(next.active)}'s turn (turn ${next.turn}) ——"),
                )
            }
            // Defensive: EndTurn(state.active) is always legal, so this branch is unreachable
            // by construction today; kept fail-safe in case the active-side seam ever changes.
            is Gameplay.Outcome.Rejected -> ui.copy(log = appendLog(ui.log, "End turn rejected: ${outcome.reason}"))
        }

    private fun submitMove(ui: BattleUiState, unitId: String, to: Pos): BattleUiState =
        when (val outcome = Gameplay.submit(ui.state, Command.Move(unitId, to), context)) {
            is Gameplay.Outcome.Accepted -> {
                val next = outcome.resolution.state
                clearSelection(ui).copy(state = next, log = appendLog(ui.log, describeMoves(outcome.resolution.events, next)))
            }
            is Gameplay.Outcome.Rejected ->
                clearSelection(ui).copy(log = appendLog(ui.log, "Move rejected: ${outcome.reason}"))
        }

    private fun submitAttack(ui: BattleUiState, attackerId: String, targetId: String): BattleUiState =
        when (val outcome = Gameplay.submit(ui.state, Command.Attack(attackerId, targetId, basicAttackSkill), context)) {
            is Gameplay.Outcome.Accepted -> {
                val next = outcome.resolution.state
                clearSelection(ui).copy(state = next, log = appendLog(ui.log, describeAttack(outcome.resolution.events, next)))
            }
            is Gameplay.Outcome.Rejected ->
                clearSelection(ui).copy(log = appendLog(ui.log, "Attack rejected: ${outcome.reason}"))
        }

    private fun clearSelection(ui: BattleUiState): BattleUiState =
        ui.copy(selected = null, destinations = emptySet(), targets = emptySet())
}

private fun unitName(state: BattleState, id: String): String = state.units[id]?.name ?: id

private fun describeMoves(events: List<Event>, state: BattleState): String =
    events.joinToString(separator = "; ") { event ->
        when (event) {
            is Event.Moved -> "${unitName(state, event.unit)} → (${event.to.x}, ${event.to.y})"
            else -> event::class.simpleName ?: "event"
        }
    }

private fun describeAttack(events: List<Event>, state: BattleState): String =
    events.joinToString(separator = "; ") { event ->
        when (event) {
            is Event.Missed -> "${unitName(state, event.target)} evaded"
            is Event.Damaged -> "${unitName(state, event.target)} −${event.amount}" +
                (if (event.crit) " crit" else "") + (if (event.combo) " combo" else "")
            is Event.Died -> "${unitName(state, event.unit)} defeated"
            else -> event::class.simpleName ?: "event"
        }
    }

private fun appendLog(log: List<String>, line: String): List<String> = (log + line).takeLast(MAX_LOG_LINES)
