package com.ccz.app.battle

import com.ccz.core.battle.Event

/**
 * A transient, presentation-only readout of something the authority already decided this
 * command — a floating "−40" / "Miss" / "KO" the board shows over the affected unit. It is a
 * pure translation of a game-core [Event] into something to draw: it carries NO combat
 * authority, computes nothing (every number, e.g. [Damaged.amount], is copied straight off the
 * authority's event), and changing or dropping an effect can never alter the battle. [unit] is
 * the id of the unit the badge is anchored to (so the board can place it on that unit's tile,
 * alive or just-defeated).
 */
sealed interface BattleEffect {
    val unit: String

    data class Damaged(override val unit: String, val amount: Int, val crit: Boolean, val combo: Boolean) : BattleEffect
    data class Missed(override val unit: String) : BattleEffect
    data class Defeated(override val unit: String) : BattleEffect
    data class Healed(override val unit: String, val amount: Int) : BattleEffect
    data class Buffed(override val unit: String, val stat: String, val amount: Int) : BattleEffect

    /** An ailment landed on [unit] (ADR 0008); [status] is the authority's raw status id, labelled at render. */
    data class Afflicted(override val unit: String, val status: String) : BattleEffect
}

/**
 * Maps the authority's [Event] stream into the presentation effects worth surfacing, in order.
 * Only the combat outcomes a player needs to see are translated (a hit's damage, a miss, a
 * death, a heal/stat change, an ailment); movement and turn bookkeeping carry no badge. This is a
 * total, side-effect-free projection — it reads event fields and never recomputes them.
 */
internal fun effectsOf(events: List<Event>): List<BattleEffect> = events.mapNotNull { event ->
    when (event) {
        is Event.Damaged -> BattleEffect.Damaged(event.target, event.amount, event.crit, event.combo)
        is Event.Missed -> BattleEffect.Missed(event.target)
        is Event.Died -> BattleEffect.Defeated(event.unit)
        is Event.Healed -> BattleEffect.Healed(event.unit, event.amount)
        is Event.StatChanged -> BattleEffect.Buffed(event.unit, event.stat.name, event.amount)
        is Event.StatusApplied -> BattleEffect.Afflicted(event.unit, event.status)
        else -> null
    }
}
