package com.ccz.app.battle

import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Command
import com.ccz.core.battle.Gameplay
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

/** Side label for a faction; PLAYER and ALLY share the player's turn (see sameSide in core). */
internal fun sideLabel(faction: Faction): String = when (faction) {
    Faction.PLAYER, Faction.ALLY -> "Player"
    Faction.ENEMY -> "Enemy"
}

/**
 * The living unit occupying [pos], if any — the single presentation-side reading of board
 * occupancy (alive, same tile; at most one by core's single-occupant invariant). Shared by tap
 * routing and rendering so they never disagree about what stands on a tile. Stays in :app
 * (Android-free) reading authoritative state; legality still lives in [Gameplay].
 */
internal fun BattleState.unitAt(pos: Pos): Combatant? = units.values.firstOrNull { it.alive && it.pos == pos }

/**
 * What game-core reported about the currently selected unit: where it may move, which attack
 * skills it may use, which one is active, and the targets that skill can legally reach. Every
 * field is a read-only query result — the UI owns none of these rules. A null [BattleUiState.selection]
 * means nothing is selected.
 */
data class Selection(
    val unit: String,
    val destinations: Set<Pos> = emptySet(),
    val skills: List<String> = emptyList(),
    val selectedSkill: String? = null,
    val targets: Set<String> = emptySet(),
)

/**
 * Immutable snapshot the battle UI renders: the authoritative [BattleState] (owned by game-core
 * and replaced wholesale on every accepted command — never edited here), the current [Selection]
 * (the move/skill/target options game-core reported for the chosen unit, or null when none is
 * selected), the presentation effects from the last accepted command (floating damage/miss/KO
 * badges, a pure translation of the authority's events), and a short human-readable event log.
 */
data class BattleUiState(
    val state: BattleState,
    val selection: Selection? = null,
    val effects: List<BattleEffect> = emptyList(),
    val log: List<String> = emptyList(),
)

/**
 * Pure presentation reducer: turns a tap, skill pick, or end-turn into the next [BattleUiState] by
 * asking [Gameplay] what is legal and submitting commands through it. It holds NO combat authority —
 * it never computes damage, decides range, picks a unit's usable skills, moves a unit itself, or
 * mutates [BattleState]; every state change is exactly what [Gameplay.submit] returns and every
 * option in a [Selection] is exactly what a [Gameplay] read-only query reported. Kept free of
 * Android types so it runs under the plain-JVM unit-test gate.
 */
class BattleReducer(private val context: BattleContext) {
    fun initial(state: BattleState): BattleUiState =
        BattleUiState(state = state, log = listOf("Battle start — ${sideLabel(state.active)}'s turn"))

    /**
     * Tap routing, in priority order: while a unit is selected, tapping a tile that holds one of
     * the active skill's targets attacks it; otherwise tapping a unit game-core reports legal moves
     * for selects it (exposing its destinations, loadout, and first skill's targets); tapping a
     * highlighted destination while a unit is selected moves it; anything else clears the selection.
     */
    fun tapTile(ui: BattleUiState, pos: Pos): BattleUiState {
        val unitHere = ui.state.unitAt(pos)
        val selection = ui.selection
        if (selection != null && unitHere != null && unitHere.id in selection.targets) {
            return submitAttack(ui, selection, unitHere.id)
        }
        if (unitHere != null) return selectUnit(ui, unitHere.id)
        return if (selection != null && pos in selection.destinations) submitMove(ui, selection.unit, pos)
        else clearSelection(ui)
    }

    /**
     * Switches which loadout skill the selected unit will attack with, re-asking game-core for the
     * targets that skill can legally reach. A no-op when nothing is selected or [skillId] is not a
     * skill game-core listed for this unit — the loadout stays the authority on what it may use.
     */
    fun selectSkill(ui: BattleUiState, skillId: String): BattleUiState {
        val selection = ui.selection ?: return ui
        if (skillId !in selection.skills) return ui
        return ui.copy(
            selection = selection.copy(
                selectedSkill = skillId,
                targets = Gameplay.legalTargets(ui.state, selection.unit, skillId, context),
            ),
        )
    }

    private fun selectUnit(ui: BattleUiState, unitId: String): BattleUiState {
        val destinations = Gameplay.legalDestinations(ui.state, unitId, context)
        if (destinations.isEmpty()) return clearSelection(ui)
        val skills = Gameplay.legalSkills(ui.state, unitId, context)
        val skill = skills.firstOrNull()
        return ui.copy(
            selection = Selection(
                unit = unitId,
                destinations = destinations,
                skills = skills,
                selectedSkill = skill,
                targets = skill?.let { Gameplay.legalTargets(ui.state, unitId, it, context) } ?: emptySet(),
            ),
            effects = emptyList(),
        )
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
            // Defensive: EndTurn(state.active) is always legal, so this branch is unreachable by
            // construction today; kept fail-safe in case the active-side seam ever changes.
            is Gameplay.Outcome.Rejected -> ui.copy(log = appendLog(ui.log, "End turn rejected: ${phraseOf(outcome.reason)}"))
        }

    private fun submitMove(ui: BattleUiState, unitId: String, to: Pos): BattleUiState =
        when (val outcome = Gameplay.submit(ui.state, Command.Move(unitId, to), context)) {
            is Gameplay.Outcome.Accepted -> {
                val next = outcome.resolution.state
                clearSelection(ui).copy(state = next, log = appendLog(ui.log, describeMoves(outcome.resolution.events, next)))
            }
            is Gameplay.Outcome.Rejected ->
                clearSelection(ui).copy(log = appendLog(ui.log, "Move rejected: ${phraseOf(outcome.reason)}"))
        }

    private fun submitAttack(ui: BattleUiState, selection: Selection, targetId: String): BattleUiState {
        // The tapped target came from this selection's active-skill target set, so selectedSkill is
        // set; the elvis is a fail-safe that never fabricates an attack with an unchosen skill.
        val skill = selection.selectedSkill ?: return clearSelection(ui)
        return when (val outcome = Gameplay.submit(ui.state, Command.Attack(selection.unit, targetId, skill), context)) {
            is Gameplay.Outcome.Accepted -> {
                val resolution = outcome.resolution
                clearSelection(ui).copy(
                    state = resolution.state,
                    effects = effectsOf(resolution.events),
                    log = appendLog(ui.log, describeAttack(resolution.events, resolution.state)),
                )
            }
            is Gameplay.Outcome.Rejected ->
                clearSelection(ui).copy(log = appendLog(ui.log, "Attack rejected: ${phraseOf(outcome.reason)}"))
        }
    }

    private fun clearSelection(ui: BattleUiState): BattleUiState = ui.copy(selection = null, effects = emptyList())
}
