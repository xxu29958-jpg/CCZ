package com.ccz.app.battle

import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleMap
import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.MapTile
import com.ccz.core.battle.ScriptContext
import com.ccz.core.event.BattleOp
import com.ccz.core.event.BattleTrigger
import com.ccz.core.event.SScript
import com.ccz.core.event.TriggerCondition
import com.ccz.core.model.CombatIdentity
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.CombatVitals
import com.ccz.core.model.Combatant
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
import com.ccz.core.model.UnitClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the reducer runs game-core's mid-battle triggers via TriggerRunner.tick after each accepted
 * command (the loaded SScript.mid actually fires in the running battle), while owning none of the trigger
 * logic itself. Uses self-contained fixtures (not the demo, whose mid list is empty).
 */
class MidTriggerTest {
    private val map = BattleMap.uniform(3, 3, MapTile("plain", moveCost = 1))
    private val context = BattleContext(
        map = map,
        classes = mapOf("c" to UnitClass("c", "C", "foot", move = 4)),
        skills = mapOf("s" to Skill("s", "S", DamageKind.PHYSICAL, powerCoeff = 100, range = RangeSpec(1, 1))),
    )
    private val roster = listOf(unit("p", Faction.PLAYER, Pos(0, 0)), unit("e", Faction.ENEMY, Pos(2, 2)))
    private val state = BattleState(units = roster.associateBy { it.id }, turn = 1, active = Faction.PLAYER, rngState = 1L)

    private fun unit(id: String, faction: Faction, pos: Pos): Combatant = Combatant(
        identity = CombatIdentity(id, id, "c", faction),
        pos = pos,
        vitals = CombatVitals(hp = 100, hpMax = 100),
        stats = CombatStats(atk = 50, def = 30, mat = 10, res = 20),
        rates = CombatRates(),
    )

    private fun scriptWith(trigger: BattleTrigger): SScript =
        SScript(id = "t", win = emptyList(), lose = emptyList(), pre = emptyList(), mid = listOf(trigger), post = emptyList())

    @Test
    fun anAcceptedCommandFiresAMidTriggerThatSpawnsReinforcements() {
        // TurnStart(1) is met right away; the once-trigger spawns "ally" (a reserve) — a state change no
        // win/lose poll could produce, so its appearance proves tick ran the trigger.
        val script = scriptWith(
            BattleTrigger("spawn_ally", TriggerCondition.TurnStart(1), actions = listOf(BattleOp.SpawnUnit("ally", Pos(0, 1)))),
        )
        val scriptContext = ScriptContext(reserves = mapOf("ally" to unit("ally", Faction.PLAYER, Pos(-1, -1))), map = map)
        val reducer = BattleReducer(context, script, scriptContext)

        val selected = reducer.tapTile(reducer.initial(state), Pos(0, 0)) // select p
        val waited = reducer.wait(selected) // Wait → tickAfter fires the mid-trigger

        assertTrue("the mid-trigger spawned the reinforcement through tick", "ally" in waited.state.units)
        assertEquals(Pos(0, 1), waited.state.units.getValue("ally").pos)
    }

    @Test
    fun aForceWinMidTriggerSurfacesVictoryThatAConditionPollWouldMiss() {
        // win/lose are empty, so an evaluate-only poll would always read ONGOING; the verdict can only come
        // from the trigger's ForceWin settling onto state — which the reducer now reads after tick.
        val script = scriptWith(BattleTrigger("win", TriggerCondition.TurnStart(1), actions = listOf(BattleOp.ForceWin)))
        val reducer = BattleReducer(context, script, ScriptContext(map = map))

        val start = reducer.initial(state)
        assertEquals("nothing decides the opening state", BattleOutcome.ONGOING, start.outcome)
        val waited = reducer.wait(reducer.tapTile(start, Pos(0, 0)))

        assertEquals("a force_win mid-trigger surfaces VICTORY", BattleOutcome.VICTORY, waited.outcome)
    }
}
