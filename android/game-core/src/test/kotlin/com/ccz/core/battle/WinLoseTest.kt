package com.ccz.core.battle

import com.ccz.core.event.SScript
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinLoseTest {
    private fun sscript(
        win: List<WinLoseCondition> = emptyList(),
        lose: List<WinLoseCondition> = emptyList(),
    ): SScript = SScript(id = "s", win = win, lose = lose, pre = emptyList(), mid = emptyList(), post = emptyList())

    @Test
    fun annihilateEnemiesWinsWhenNoEnemyAlive() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val script = sscript(win = listOf(WinLoseCondition.AnnihilateEnemies))
        assertEquals(BattleOutcome.VICTORY, WinLose.evaluate(state, script))
    }

    @Test
    fun ongoingWhileAnEnemyRemains() {
        val state = stateOf(
            combatant("h", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        val script = sscript(win = listOf(WinLoseCondition.AnnihilateEnemies))
        assertEquals(BattleOutcome.ONGOING, WinLose.evaluate(state, script))
    }

    @Test
    fun unitDeadInLoseListLoses() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0), hp = 0))
        val script = sscript(lose = listOf(WinLoseCondition.UnitDead("h")))
        assertEquals(BattleOutcome.DEFEAT, WinLose.evaluate(state, script))
    }

    @Test
    fun reachTileWinsOnlyForLivingUnitAtTile() {
        val script = sscript(win = listOf(WinLoseCondition.ReachTile("h", Pos(3, 4))))
        val arrived = stateOf(combatant("h", Faction.PLAYER, Pos(3, 4)))
        val deadAtTile = stateOf(combatant("h", Faction.PLAYER, Pos(3, 4), hp = 0))
        assertEquals(BattleOutcome.VICTORY, WinLose.evaluate(arrived, script))
        assertEquals(BattleOutcome.ONGOING, WinLose.evaluate(deadAtTile, script))
    }

    @Test
    fun defeatUnitWinsWhenTargetIsDown() {
        val state = stateOf(
            combatant("h", Faction.PLAYER, Pos(0, 0)),
            combatant("boss", Faction.ENEMY, Pos(1, 0), hp = 0),
        )
        val script = sscript(win = listOf(WinLoseCondition.DefeatUnit("boss")))
        assertEquals(BattleOutcome.VICTORY, WinLose.evaluate(state, script))
    }

    @Test
    fun surviveTurnsWinsOnlyAtOrAfterTargetTurn() {
        val script = sscript(win = listOf(WinLoseCondition.SurviveTurns(5)))
        val early = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)), turn = 4)
        val reached = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)), turn = 5)
        assertEquals(BattleOutcome.ONGOING, WinLose.evaluate(early, script))
        assertEquals(BattleOutcome.VICTORY, WinLose.evaluate(reached, script))
    }

    @Test
    fun protectAliveFiresWhenGuardedUnitDies() {
        val script = sscript(lose = listOf(WinLoseCondition.ProtectAlive("vip")))
        val safe = stateOf(combatant("vip", Faction.PLAYER, Pos(0, 0)))
        val fallen = stateOf(combatant("vip", Faction.PLAYER, Pos(0, 0), hp = 0))
        assertEquals(BattleOutcome.ONGOING, WinLose.evaluate(safe, script))
        assertEquals(BattleOutcome.DEFEAT, WinLose.evaluate(fallen, script))
    }

    @Test
    fun loseTakesPrecedenceOverWin() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0), hp = 0)) // no enemy alive AND hero dead
        val script = sscript(
            win = listOf(WinLoseCondition.AnnihilateEnemies),
            lose = listOf(WinLoseCondition.UnitDead("h")),
        )
        assertEquals(BattleOutcome.DEFEAT, WinLose.evaluate(state, script))
    }

    @Test
    fun settleEmitsBattleEndedOnceThenStaysSticky() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val script = sscript(win = listOf(WinLoseCondition.AnnihilateEnemies))

        val first = WinLose.settle(state, script)
        assertEquals(BattleOutcome.VICTORY, first.state.outcome)
        assertEquals(listOf(Event.BattleEnded(BattleOutcome.VICTORY)), first.events)

        val again = WinLose.settle(first.state, script)
        assertEquals(BattleOutcome.VICTORY, again.state.outcome)
        assertTrue(again.events.isEmpty())
    }

    @Test
    fun settleEmitsNothingWhileOngoing() {
        val state = stateOf(
            combatant("h", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        val resolution = WinLose.settle(state, sscript(win = listOf(WinLoseCondition.AnnihilateEnemies)))
        assertEquals(BattleOutcome.ONGOING, resolution.state.outcome)
        assertTrue(resolution.events.isEmpty())
    }
}
