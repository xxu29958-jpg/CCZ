package com.ccz.core.battle

import com.ccz.core.event.TriggerCondition
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerConditionTest {
    @Test
    fun turnStartMatchesTurnAndOptionalFaction() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)), active = Faction.PLAYER, turn = 3)
        assertTrue(TriggerConditions.met(state, TriggerCondition.TurnStart(3)))
        assertTrue(TriggerConditions.met(state, TriggerCondition.TurnStart(3, Faction.PLAYER)))
        assertFalse(TriggerConditions.met(state, TriggerCondition.TurnStart(2)))
        assertFalse(TriggerConditions.met(state, TriggerCondition.TurnStart(3, Faction.ENEMY)))
    }

    @Test
    fun unitDeadWhenAbsentOrZeroHp() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0), hp = 0))
        assertTrue(TriggerConditions.met(state, TriggerCondition.UnitDead("h")))
        assertTrue(TriggerConditions.met(state, TriggerCondition.UnitDead("ghost")))
    }

    @Test
    fun unitReachOnlyForLivingUnitAtPos() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(2, 5)))
        val dead = stateOf(combatant("h", Faction.PLAYER, Pos(2, 5), hp = 0))
        assertTrue(TriggerConditions.met(state, TriggerCondition.UnitReach("h", Pos(2, 5))))
        assertFalse(TriggerConditions.met(state, TriggerCondition.UnitReach("h", Pos(2, 6))))
        assertFalse(TriggerConditions.met(dead, TriggerCondition.UnitReach("h", Pos(2, 5))))
        assertFalse(TriggerConditions.met(state, TriggerCondition.UnitReach("ghost", Pos(2, 5))))
    }

    @Test
    fun hpBelowUsesIntegerThreshold() {
        val below = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0), hp = 29)) // 29% < 30%
        val onThreshold = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0), hp = 30)) // 30% not < 30%
        val dead = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0), hp = 0))
        assertTrue(TriggerConditions.met(below, TriggerCondition.HpBelow("h", 30)))
        assertFalse(TriggerConditions.met(onThreshold, TriggerCondition.HpBelow("h", 30)))
        assertFalse(TriggerConditions.met(dead, TriggerCondition.HpBelow("h", 30)))
    }

    @Test
    fun enemyCountBelowCountsLivingEnemies() {
        val state = stateOf(
            combatant("h", Faction.PLAYER, Pos(0, 0)),
            combatant("e1", Faction.ENEMY, Pos(1, 0)),
            combatant("e2", Faction.ENEMY, Pos(2, 0), hp = 0), // dead, not counted
        )
        assertTrue(TriggerConditions.met(state, TriggerCondition.EnemyCountBelow(2)))
        assertFalse(TriggerConditions.met(state, TriggerCondition.EnemyCountBelow(1)))
    }

    @Test
    fun varEqualsTreatsMissingAsZero() {
        val base = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        assertTrue(TriggerConditions.met(base, TriggerCondition.VarEquals("flag", 0)))
        assertFalse(TriggerConditions.met(base, TriggerCondition.VarEquals("flag", 1)))
        assertTrue(TriggerConditions.met(base.withVar("flag", 1), TriggerCondition.VarEquals("flag", 1)))
    }
}
