package com.ccz.core.battle

import com.ccz.core.event.TriggerCondition
import com.ccz.core.model.Faction

/**
 * Pure predicates for the first batch of mid-battle trigger conditions. Reads
 * authoritative state only — no RNG, no mutation. HP comparison stays integer
 * (`hp/hpMax < pct/100` cross-multiplied) to preserve replay determinism.
 */
internal object TriggerConditions {
    fun met(state: BattleState, cond: TriggerCondition): Boolean = when (cond) {
        is TriggerCondition.TurnStart ->
            state.turn == cond.turn && (cond.faction == null || state.active == cond.faction)
        is TriggerCondition.UnitDead -> !alive(state, cond.unit)
        is TriggerCondition.UnitReach ->
            state.units[cond.unit]?.let { it.alive && it.pos == cond.pos } ?: false
        is TriggerCondition.HpBelow -> hpBelow(state, cond.unit, cond.pct)
        is TriggerCondition.EnemyCountBelow -> aliveEnemyCount(state) < cond.count
        is TriggerCondition.VarEquals -> state.varValue(cond.name) == cond.value
    }

    private fun alive(state: BattleState, unit: String): Boolean = state.units[unit]?.alive ?: false

    private fun hpBelow(state: BattleState, unit: String, pct: Int): Boolean {
        val combatant = state.units[unit] ?: return false
        return combatant.alive && combatant.hp * 100 < combatant.hpMax * pct
    }

    private fun aliveEnemyCount(state: BattleState): Int =
        state.units.values.count { it.alive && it.faction == Faction.ENEMY }
}
