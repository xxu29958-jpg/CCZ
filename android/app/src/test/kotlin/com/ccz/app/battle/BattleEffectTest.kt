package com.ccz.app.battle

import com.ccz.core.battle.Event
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [effectsOf] as a pure projection of the authority's event stream: only damage, miss, and
 * death become badges (movement and turn bookkeeping carry none), order is preserved, and every
 * field is copied straight off the event — the presentation layer never recomputes a number.
 */
class BattleEffectTest {
    @Test
    fun translatesDamageMissAndDeathInOrderIgnoringBookkeeping() {
        val events = listOf(
            Event.Moved("u", Pos(0, 0), Pos(1, 0)),
            Event.Damaged("foe", amount = 40, crit = false, combo = false, broke = true),
            Event.Missed("attacker", "foe"),
            Event.Died("foe"),
            Event.TurnEnded(Faction.PLAYER),
        )
        assertEquals(
            listOf(
                BattleEffect.Damaged("foe", 40, crit = false, combo = false),
                BattleEffect.Missed("foe"),
                BattleEffect.Defeated("foe"),
            ),
            effectsOf(events),
        )
    }

    @Test
    fun damagedEffectCopiesAuthorityFieldsVerbatim() {
        val effects = effectsOf(listOf(Event.Damaged("x", amount = 137, crit = true, combo = true, broke = false)))
        assertEquals(listOf(BattleEffect.Damaged("x", 137, crit = true, combo = true)), effects)
    }
}
