package com.ccz.core.battle

import com.ccz.core.event.SScript
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Faction

/**
 * Evaluates an S-script's win/lose lists against the authoritative state and
 * decides the [BattleOutcome]. Contract (structural, frozen per the Evidence rule,
 * calibratable when real MOD samples land):
 *
 * - Each list is OR: any condition met decides that outcome.
 * - Lose is checked before win (defeat takes precedence on a tie).
 * - The outcome is sticky: once decided it is never re-evaluated.
 *
 * Pure and deterministic — reads state only, consumes no RNG.
 */
object WinLose {
    fun evaluate(state: BattleState, script: SScript): BattleOutcome {
        if (state.outcome != BattleOutcome.ONGOING) return state.outcome
        if (script.lose.any { met(state, it) }) return BattleOutcome.DEFEAT
        if (script.win.any { met(state, it) }) return BattleOutcome.VICTORY
        return BattleOutcome.ONGOING
    }

    /** Settles the outcome, emitting [Event.BattleEnded] only on the ONGOING -> decided edge. */
    fun settle(state: BattleState, script: SScript): Resolution {
        val outcome = evaluate(state, script)
        return if (outcome != state.outcome) {
            Resolution(state.withOutcome(outcome), listOf(Event.BattleEnded(outcome)))
        } else {
            Resolution(state, emptyList())
        }
    }

    private fun met(state: BattleState, cond: WinLoseCondition): Boolean = when (cond) {
        WinLoseCondition.AnnihilateEnemies ->
            state.units.values.none { it.alive && it.faction == Faction.ENEMY }
        is WinLoseCondition.UnitDead -> !aliveAt(state, cond.unit)
        is WinLoseCondition.DefeatUnit -> !aliveAt(state, cond.unit)
        // "Protect" objective fires (is met) when the guarded unit has fallen; place in the lose list.
        is WinLoseCondition.ProtectAlive -> !aliveAt(state, cond.unit)
        is WinLoseCondition.ReachTile ->
            state.units[cond.unit]?.let { it.alive && it.pos == cond.pos } ?: false
        is WinLoseCondition.SurviveTurns -> state.turn >= cond.turns
    }

    private fun aliveAt(state: BattleState, unit: String): Boolean = state.units[unit]?.alive ?: false
}
