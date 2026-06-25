package com.ccz.app.battle

import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Command
import com.ccz.core.battle.EnemyAi
import com.ccz.core.battle.Event
import com.ccz.core.battle.Gameplay
import com.ccz.core.battle.Resolution
import com.ccz.core.battle.ScriptContext
import com.ccz.core.battle.TriggerRunner
import com.ccz.core.event.SScript
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

/** Fail-safe bound on enemy-turn commands; the action economy makes a real turn far shorter. */
private const val ENEMY_TURN_STEP_CAP = 256

/** Side label for a faction; PLAYER and ALLY share the player's turn (see sameSide in core). */
internal fun sideLabel(faction: Faction): String = when (faction) {
    Faction.PLAYER, Faction.ALLY -> "Player"
    Faction.ENEMY -> "Enemy"
}

/** Log banner for a decided battle; empty while ONGOING (never appended — see logVerdict). */
internal fun verdictBanner(outcome: BattleOutcome): String = when (outcome) {
    BattleOutcome.VICTORY -> "★ 胜利！"
    BattleOutcome.DEFEAT -> "✖ 战败"
    BattleOutcome.ONGOING -> ""
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
 * badges, a pure translation of the authority's events), a short human-readable event log, and
 * the tile the player last tapped ([inspected], null until the first tap) so the UI can show that
 * terrain's cover — a read-only readout of the map, never a command.
 */
data class BattleUiState(
    val state: BattleState,
    val selection: Selection? = null,
    val effects: List<BattleEffect> = emptyList(),
    val log: List<String> = emptyList(),
    val outcome: BattleOutcome = BattleOutcome.ONGOING,
    val inspected: Pos? = null,
)

/**
 * Pure presentation reducer: turns a tap, skill pick, or end-turn into the next [BattleUiState] by
 * asking [Gameplay] what is legal and submitting commands through it. It holds NO combat authority —
 * it never computes damage, decides range, picks a unit's usable skills, moves a unit itself, or
 * mutates [BattleState]; every state change is exactly what [Gameplay.submit] returns and every
 * option in a [Selection] is exactly what a [Gameplay] read-only query reported. Kept free of
 * Android types so it runs under the plain-JVM unit-test gate.
 *
 * [script] is the S-script whose win/lose lists decide the battle outcome and whose `mid` triggers fire
 * mid-battle. After every accepted command the reducer runs the authority's [TriggerRunner.tick]
 * ([tickAfter]) — firing eligible mid-triggers and settling win/lose in game-core — then reads the verdict
 * from the settled [BattleState.outcome]. The reducer owns none of this: triggers and settlement live in
 * game-core; it only forwards state through `tick` and renders the result. Once decided, input is ignored
 * — a finished battle is terminal. [scriptContext] (reserves + map, from the assembler) is what `tick`
 * draws mid spawns from and checks placement against — the same context the opening deployment used.
 */
class BattleReducer(
    private val context: BattleContext,
    private val script: SScript,
    private val scriptContext: ScriptContext,
) {
    fun initial(state: BattleState): BattleUiState =
        BattleUiState(
            state = state,
            log = listOf("Battle start — ${sideLabel(state.active)}'s turn"),
            // The opening state has not had a command, so evaluate (don't settle) its verdict: a battle that
            // is somehow already decided at deployment shows decided; mid-triggers settle from tickAfter on.
            outcome = Gameplay.outcome(state, script),
        )

    /**
     * Runs game-core's mid-battle triggers + win/lose settlement after a command resolves: the loaded
     * S-script's `mid` triggers fire (conditions met → [com.ccz.core.battle.BattleOps]) and the outcome
     * settles, all in the authority. Returns the ticked state plus the command's events followed by the
     * tick's, so a trigger-driven spawn/damage/outcome is reflected in both state and the rendered events.
     *
     * Presentation caveat (the demo's `mid` is empty, so this is dormant today): a trigger's *combat*
     * events (Damaged/Missed/Died) badge and log like a command's, but its *structural* events
     * (UnitSpawned/UnitRemoved and the fail-closed SpawnRejected/MoveRejected/HpSetRejected) are applied to
     * state yet not yet surfaced as a badge or a dedicated log line — so a rejected mid-spawn is currently
     * silent in the UI. Surface these when in-battle trigger content lands, alongside the per-event timed
     * presentation refinement (see KNOWN_ISSUES / HANDOFF 内聚触发器 ⑤).
     */
    private fun tickAfter(resolution: Resolution): Resolution {
        val ticked = TriggerRunner.tick(resolution.state, script, scriptContext)
        return Resolution(ticked.state, resolution.events + ticked.events)
    }

    /**
     * Routes a tap to its command (see [routeTap]) and records the tapped tile in [BattleUiState.inspected]
     * so the UI can show that terrain's cover. Inspection is a pure read-only readout of the map — it
     * issues no command and is recorded on every tap regardless of what the tap did; a decided battle
     * is terminal and ignores the tap entirely (no inspection either).
     */
    fun tapTile(ui: BattleUiState, pos: Pos): BattleUiState {
        if (ui.outcome != BattleOutcome.ONGOING) return ui
        return routeTap(ui, pos).copy(inspected = pos)
    }

    /**
     * Tap routing, in priority order: while a unit is selected, tapping a tile that holds one of
     * the active skill's targets attacks it; otherwise tapping a unit game-core reports legal moves
     * for selects it (exposing its destinations, loadout, and first skill's targets); tapping a
     * highlighted destination while a unit is selected moves it; anything else clears the selection.
     */
    private fun routeTap(ui: BattleUiState, pos: Pos): BattleUiState {
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
        if (ui.outcome != BattleOutcome.ONGOING) return ui
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
        val skills = Gameplay.legalSkills(ui.state, unitId, context)
        // Selectable while the unit can still do something this turn: a fresh unit has destinations and
        // skills; a unit that has only MOVED has no destinations but keeps its skills (move-then-attack)
        // and can Wait; a fully-acted (or off-side / dead) unit has neither, so it cannot be selected.
        if (destinations.isEmpty() && skills.isEmpty()) return clearSelection(ui)
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

    fun endTurn(ui: BattleUiState): BattleUiState {
        if (ui.outcome != BattleOutcome.ONGOING) return ui
        val afterPlayer = when (val result = Gameplay.submit(ui.state, Command.EndTurn(ui.state.active), context)) {
            // tick after the turn flip fires the new turn's start-triggers and settles win/lose (advancing
            // the turn can satisfy a SurviveTurns condition; the verdict comes from the settled state).
            is Gameplay.Outcome.Accepted -> {
                val resolution = tickAfter(result.resolution)
                clearSelection(ui).withVerdict(resolution.state, appendLog(ui.log, turnBanner(resolution.state)))
            }
            // Defensive: EndTurn(state.active) is always legal, so this branch is unreachable by
            // construction today; kept fail-safe in case the active-side seam ever changes.
            is Gameplay.Outcome.Rejected -> return ui.copy(log = appendLog(ui.log, "End turn rejected: ${phraseOf(result.reason)}"))
        }
        return runEnemyTurn(afterPlayer)
    }

    /**
     * Drives the enemy side automatically once the player's turn ends: each [EnemyAi] command is
     * submitted through the same [Gameplay] gate a player uses (the reducer owns no AI authority — the
     * plan is game-core's), until the turn flips back to the player or the battle is decided. The action
     * economy bounds the loop (each unit acts at most once); [ENEMY_TURN_STEP_CAP] is a fail-safe against
     * an unexpected non-terminating plan. Intermediate damage badges are kept (an attack's effects carry
     * forward) so the player sees the last blow; per-blow animation is a later (event-timed) slice.
     */
    private fun runEnemyTurn(start: BattleUiState): BattleUiState {
        var ui = start
        var steps = 0
        while (ui.outcome == BattleOutcome.ONGOING && ui.state.active == Faction.ENEMY && steps < ENEMY_TURN_STEP_CAP) {
            steps++
            val command = EnemyAi.nextCommand(ui.state, context)
            ui = when (val result = Gameplay.submit(ui.state, command, context)) {
                is Gameplay.Outcome.Accepted -> applyEnemyCommand(ui, command, tickAfter(result.resolution))
                // The AI only issues commands its own legality queries reported, so a rejection is a
                // should-never bug; hand control back to the player rather than spin or strand them.
                is Gameplay.Outcome.Rejected -> return handBackToPlayer(ui, "Enemy command rejected: ${phraseOf(result.reason)}")
            }
        }
        // Fail-safe: if the loop bailed on the step cap with the enemy still active, return control to
        // the player rather than leave them able to command enemy units (the cap can't trip on a real
        // roster — the action economy bounds the turn — so this is defense-in-depth).
        return if (ui.outcome == BattleOutcome.ONGOING && ui.state.active == Faction.ENEMY) {
            handBackToPlayer(ui, "Enemy turn exceeded its step budget")
        } else {
            ui
        }
    }

    /** Applies one accepted enemy command: carry the last blow's badges forward, log it, refresh verdict. */
    private fun applyEnemyCommand(ui: BattleUiState, command: Command, resolution: Resolution): BattleUiState {
        val effects = effectsOf(resolution.events).ifEmpty { ui.effects }
        return ui.copy(selection = null, effects = effects)
            .withVerdict(resolution.state, appendLog(ui.log, enemyLogLine(command, resolution.events, resolution.state)))
    }

    /** Fail-safe used only on an unexpected enemy-turn bail: force the active side's EndTurn so control
     *  returns to the player instead of stranding them able to command enemy units. */
    private fun handBackToPlayer(ui: BattleUiState, note: String): BattleUiState =
        when (val result = Gameplay.submit(ui.state, Command.EndTurn(ui.state.active), context)) {
            is Gameplay.Outcome.Accepted -> {
                val resolution = tickAfter(result.resolution)
                ui.copy(selection = null).withVerdict(resolution.state, appendLog(appendLog(ui.log, note), turnBanner(resolution.state)))
            }
            is Gameplay.Outcome.Rejected -> ui.copy(log = appendLog(ui.log, note))
        }

    private fun turnBanner(state: BattleState): String = "—— ${sideLabel(state.active)}'s turn (turn ${state.turn}) ——"

    private fun enemyLogLine(command: Command, events: List<Event>, state: BattleState): String = when (command) {
        is Command.Move -> describeMoves(events, state)
        is Command.Attack -> describeAttack(events, state)
        // The enemy AI does not cast in Phase 1 (ADR 0008), so this arm is unreached today; kept for
        // exhaustiveness so a future casting AI compiles and logs sensibly rather than silently.
        is Command.Cast -> "${unitName(state, command.caster)} casts ${command.skill}"
        is Command.Wait -> "${unitName(state, command.unit)} waits"
        is Command.EndTurn -> turnBanner(state)
    }

    private fun submitMove(ui: BattleUiState, unitId: String, to: Pos): BattleUiState =
        when (val result = Gameplay.submit(ui.state, Command.Move(unitId, to), context)) {
            is Gameplay.Outcome.Accepted -> {
                val resolution = tickAfter(result.resolution)
                val next = resolution.state
                val moved = clearSelection(ui).withVerdict(next, appendLog(ui.log, describeMoves(resolution.events, next)))
                // Move-then-act: keep the unit selected so it can still attack or Wait this turn. selectUnit
                // now reports empty destinations (it has moved) but its skills/targets, so the attack UX is
                // reachable; if the battle just ended, leave the selection cleared.
                if (moved.outcome == BattleOutcome.ONGOING) selectUnit(moved, unitId) else moved
            }
            is Gameplay.Outcome.Rejected ->
                clearSelection(ui).copy(log = appendLog(ui.log, "Move rejected: ${phraseOf(result.reason)}"))
        }

    /**
     * Stands the selected unit down for the turn (Fire-Emblem "wait"), exhausting it without attacking —
     * the move-then-no-attack path and the way a unit with nothing to hit finishes. No-op when nothing is
     * selected or the battle is decided; a rejection (e.g. the unit already acted) only logs.
     */
    fun wait(ui: BattleUiState): BattleUiState {
        if (ui.outcome != BattleOutcome.ONGOING) return ui
        val selection = ui.selection ?: return ui
        return when (val result = Gameplay.submit(ui.state, Command.Wait(selection.unit), context)) {
            is Gameplay.Outcome.Accepted ->
                clearSelection(ui).withVerdict(tickAfter(result.resolution).state, appendLog(ui.log, "${unitName(ui.state, selection.unit)} waits"))
            is Gameplay.Outcome.Rejected ->
                clearSelection(ui).copy(log = appendLog(ui.log, "Wait rejected: ${phraseOf(result.reason)}"))
        }
    }

    private fun submitAttack(ui: BattleUiState, selection: Selection, targetId: String): BattleUiState {
        // The tapped target came from this selection's active-skill target set, so selectedSkill is
        // set; the elvis is a fail-safe that never fabricates an attack with an unchosen skill.
        val skill = selection.selectedSkill ?: return clearSelection(ui)
        return when (val result = Gameplay.submit(ui.state, Command.Attack(selection.unit, targetId, skill), context)) {
            is Gameplay.Outcome.Accepted -> {
                val resolution = tickAfter(result.resolution)
                clearSelection(ui)
                    .copy(effects = effectsOf(resolution.events))
                    .withVerdict(resolution.state, appendLog(ui.log, describeAttack(resolution.events, resolution.state)))
            }
            is Gameplay.Outcome.Rejected ->
                clearSelection(ui).copy(log = appendLog(ui.log, "Attack rejected: ${phraseOf(result.reason)}"))
        }
    }

    /**
     * Applies a post-[tickAfter] command's new [state] to this UI snapshot, reading the verdict from the
     * authority-settled [BattleState.outcome] (game-core's `tick` already settled it — including any
     * trigger-driven force_win/force_lose, which a condition-only poll would miss) and appending a
     * victory/defeat banner to [log] only on the ONGOING -> decided edge. The receiver carries any
     * already-set effects forward.
     */
    private fun BattleUiState.withVerdict(state: BattleState, log: List<String>): BattleUiState {
        val verdict = state.outcome
        val banner = if (verdict != BattleOutcome.ONGOING && outcome == BattleOutcome.ONGOING) appendLog(log, verdictBanner(verdict)) else log
        return copy(state = state, outcome = verdict, log = banner)
    }

    private fun clearSelection(ui: BattleUiState): BattleUiState = ui.copy(selection = null, effects = emptyList())
}
