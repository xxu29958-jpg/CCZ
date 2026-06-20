package com.ccz.core.battle

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
}

data class BattleState(
    val units: Map<String, Combatant>,
    val turn: Int,
    val active: Faction,
    val rngState: Long,
) {
    fun unit(id: String): Combatant = units.getValue(id)
    fun withUnit(unit: Combatant): BattleState = copy(units = units + (unit.id to unit))
}

data class Resolution(val state: BattleState, val events: List<Event>)

