package com.ccz.app.battle

import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Event
import com.ccz.core.model.Ailment

internal const val MAX_LOG_LINES = 6

/** Human-readable name for a unit id, falling back to the id when it is unknown to state. */
internal fun unitName(state: BattleState, id: String): String = state.units[id]?.name ?: id

/** Chinese display label for an ailment kind (ADR 0008); exhaustive `when` so a new kind must add its label. */
internal fun ailmentLabel(kind: Ailment): String = when (kind) {
    Ailment.SILENCE -> "沉默"
    Ailment.STUN -> "麻痹"
}

/**
 * Display label for an [Event.StatusApplied] status id: an ailment id resolves to its [ailmentLabel], any
 * other (scenario-applied) status falls back to its raw id. Keeps the ailment labels single-sourced.
 */
internal fun statusLabel(status: String): String =
    Ailment.entries.firstOrNull { it.name == status }?.let { ailmentLabel(it) } ?: status

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
            is Event.StatChanged ->
                "${unitName(state, event.unit)} ${event.stat.name.lowercase()} ${if (event.amount >= 0) "+${event.amount}" else "${event.amount}"}"
            is Event.StatusApplied -> "${unitName(state, event.unit)} ${statusLabel(event.status)}"
            else -> event::class.simpleName ?: "event"
        }
    }

internal fun appendLog(log: List<String>, line: String): List<String> = (log + line).takeLast(MAX_LOG_LINES)
