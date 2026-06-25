package com.ccz.app.battle

import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Event

internal const val MAX_LOG_LINES = 6

/** Human-readable name for a unit id, falling back to the id when it is unknown to state. */
internal fun unitName(state: BattleState, id: String): String = state.units[id]?.name ?: id

/** One log line describing the move events of an accepted command, against the resulting state. */
internal fun describeMoves(events: List<Event>, state: BattleState): String =
    events.joinToString(separator = "; ") { event ->
        when (event) {
            is Event.Moved -> "${unitName(state, event.unit)} → (${event.to.x}, ${event.to.y})"
            else -> event::class.simpleName ?: "event"
        }
    }

/** One log line describing the combat events of an accepted attack, against the resulting state. */
internal fun describeAttack(events: List<Event>, state: BattleState): String =
    events.joinToString(separator = "; ") { event ->
        when (event) {
            is Event.Missed -> "${unitName(state, event.target)} evaded"
            is Event.Damaged -> "${unitName(state, event.target)} −${event.amount}" +
                (if (event.crit) " crit" else "") + (if (event.combo) " combo" else "")
            is Event.Died -> "${unitName(state, event.unit)} defeated"
            else -> event::class.simpleName ?: "event"
        }
    }

/** One log line describing the effect events of an accepted cast (ADR 0008), against the resulting state. */
internal fun describeCast(events: List<Event>, state: BattleState): String =
    events.joinToString(separator = "; ") { event ->
        when (event) {
            is Event.Healed -> "${unitName(state, event.unit)} +${event.amount}"
            else -> event::class.simpleName ?: "event"
        }
    }

internal fun appendLog(log: List<String>, line: String): List<String> = (log + line).takeLast(MAX_LOG_LINES)
