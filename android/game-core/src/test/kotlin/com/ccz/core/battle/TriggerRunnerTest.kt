package com.ccz.core.battle

import com.ccz.core.event.BattleOp
import com.ccz.core.event.BattleTrigger
import com.ccz.core.event.SScript
import com.ccz.core.event.ScenarioOp
import com.ccz.core.event.TriggerCondition
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TriggerRunnerTest {
    private val ctx = ScriptContext()

    private fun sscript(
        pre: List<BattleOp> = emptyList(),
        mid: List<BattleTrigger> = emptyList(),
        win: List<WinLoseCondition> = emptyList(),
    ): SScript = SScript("s", win = win, lose = emptyList(), pre = pre, mid = mid, post = emptyList())

    @Test
    fun onceTriggerFiresOnlyOnce() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)), turn = 1)
        val script = sscript(
            mid = listOf(BattleTrigger("t", TriggerCondition.TurnStart(1), once = true, actions = listOf(BattleOp.GiveItem("h", "gift")))),
        )
        val first = TriggerRunner.tick(state, script, ctx)
        val second = TriggerRunner.tick(first.state, script, ctx)

        assertEquals(listOf(Event.ItemGranted("h", "gift")), first.events)
        assertTrue(first.state.hasFired("t"))
        assertTrue(second.events.isEmpty())
    }

    @Test
    fun nonOnceTriggerFiresEveryTick() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)), turn = 1)
        val script = sscript(
            mid = listOf(BattleTrigger("t", TriggerCondition.TurnStart(1), once = false, actions = listOf(BattleOp.GiveItem("h", "gift")))),
        )
        val first = TriggerRunner.tick(state, script, ctx)
        val second = TriggerRunner.tick(first.state, script, ctx)

        assertEquals(listOf(Event.ItemGranted("h", "gift")), first.events)
        assertEquals(listOf(Event.ItemGranted("h", "gift")), second.events)
        assertTrue(!first.state.hasFired("t")) // non-once triggers are not tracked
    }

    @Test
    fun tickFiresTriggerThenSettlesWinLose() {
        val state = stateOf(
            combatant("h", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
            turn = 1,
        )
        val script = sscript(
            mid = listOf(BattleTrigger("kill", TriggerCondition.TurnStart(1), once = true, actions = listOf(BattleOp.RemoveUnit("e")))),
            win = listOf(WinLoseCondition.AnnihilateEnemies),
        )
        val result = TriggerRunner.tick(state, script, ctx)

        assertEquals(BattleOutcome.VICTORY, result.state.outcome)
        assertEquals(listOf(Event.UnitRemoved("e"), Event.BattleEnded(BattleOutcome.VICTORY)), result.events)
    }

    @Test
    fun triggerWithUnmetConditionDoesNothing() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)), turn = 1)
        val script = sscript(
            mid = listOf(BattleTrigger("late", TriggerCondition.TurnStart(5), once = true, actions = listOf(BattleOp.GiveItem("h", "gift")))),
        )
        val result = TriggerRunner.tick(state, script, ctx)

        assertTrue(result.events.isEmpty())
        assertEquals(BattleOutcome.ONGOING, result.state.outcome)
        assertTrue(!result.state.hasFired("late"))
    }

    @Test
    fun applyPreRunsPreOps() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val script = sscript(pre = listOf(BattleOp.Script(ScenarioOp.SetVar("intro", 1))))
        val result = TriggerRunner.applyPre(state, script, ctx)

        assertEquals(1, result.state.varValue("intro"))
        assertEquals(listOf(Event.VarSet("intro", 1)), result.events)
    }
}
