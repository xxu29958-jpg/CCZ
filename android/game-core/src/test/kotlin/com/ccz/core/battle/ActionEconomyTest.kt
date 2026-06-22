package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Action economy (Fire-Emblem style): each unit may Move once then take one action — Attack or Wait —
 * per turn; it may not move twice nor act twice, and EndTurn clears the economy so the next side starts
 * fresh. The gate is [Gameplay.submit] / [CommandValidator]; resolution is unaffected (replay re-applies
 * already-accepted commands through the Resolver directly, never re-validating). Query parity (the
 * read-only [Gameplay] previews) is pinned alongside so the UI's highlights match what submit accepts.
 */
class ActionEconomyTest {
    private val ctx = contextOf(flat(6, 1)) // default skill "atk" (melee), class move = 5

    private fun adjacent() = stateOf(combatant("p", Faction.PLAYER, Pos(0, 0)), combatant("e", Faction.ENEMY, Pos(1, 0)))
    private fun open() = stateOf(combatant("p", Faction.PLAYER, Pos(0, 0)), combatant("e", Faction.ENEMY, Pos(5, 0)))

    private fun accept(state: BattleState, command: Command): BattleState {
        val outcome = Gameplay.submit(state, command, ctx)
        assertTrue(outcome is Gameplay.Outcome.Accepted, "expected accepted: $command")
        return (outcome as Gameplay.Outcome.Accepted).resolution.state
    }

    private fun reason(state: BattleState, command: Command): RejectReason? =
        (Gameplay.submit(state, command, ctx) as? Gameplay.Outcome.Rejected)?.reason

    @Test
    fun aUnitCannotMoveTwiceInATurn() {
        val moved = accept(open(), Command.Move("p", Pos(1, 0)))
        assertEquals(RejectReason.UNIT_ALREADY_MOVED, reason(moved, Command.Move("p", Pos(2, 0))))
    }

    @Test
    fun aMovedUnitMayStillAttack() {
        // Move-to-self uses the unit's move (Fire-Emblem default: move then act), and it may then attack.
        val moved = accept(adjacent(), Command.Move("p", Pos(0, 0)))
        assertTrue(moved.hasMoved("p"))
        assertNull(reason(moved, Command.Attack("p", "e", "atk")), "a moved-but-not-acted unit may attack")
    }

    @Test
    fun anActedUnitCanNeitherMoveNorAttackNorWaitAgain() {
        val acted = accept(adjacent(), Command.Attack("p", "e", "atk"))
        assertTrue(acted.hasActed("p"))
        assertEquals(RejectReason.UNIT_ALREADY_ACTED, reason(acted, Command.Move("p", Pos(0, 0))))
        assertEquals(RejectReason.UNIT_ALREADY_ACTED, reason(acted, Command.Attack("p", "e", "atk")))
        assertEquals(RejectReason.UNIT_ALREADY_ACTED, reason(acted, Command.Wait("p")))
    }

    @Test
    fun waitExhaustsAUnitForTheTurn() {
        val waited = accept(adjacent(), Command.Wait("p"))
        assertTrue(waited.hasActed("p"))
        assertEquals(RejectReason.UNIT_ALREADY_ACTED, reason(waited, Command.Attack("p", "e", "atk")))
    }

    @Test
    fun endTurnClearsTheActionEconomy() {
        val moved = accept(open(), Command.Move("p", Pos(1, 0)))
        val next = accept(moved, Command.EndTurn(Faction.PLAYER))
        assertFalse(next.hasMoved("p"), "EndTurn resets per-turn move/act tracking")
        assertFalse(next.hasActed("p"))
    }

    @Test
    fun queriesGoEmptyOnceTheUnitHasMovedOrActed() {
        val fresh = adjacent()
        assertTrue(Gameplay.legalDestinations(fresh, "p", ctx).isNotEmpty(), "fresh unit has destinations")
        assertTrue("e" in Gameplay.legalTargets(fresh, "p", "atk", ctx), "fresh unit can target the adjacent foe")

        val moved = accept(fresh, Command.Move("p", Pos(0, 0)))
        assertTrue(Gameplay.legalDestinations(moved, "p", ctx).isEmpty(), "a moved unit has no further destinations")
        assertTrue("e" in Gameplay.legalTargets(moved, "p", "atk", ctx), "but a moved unit can still target (move-then-attack)")

        val acted = accept(moved, Command.Attack("p", "e", "atk"))
        assertTrue(Gameplay.legalTargets(acted, "p", "atk", ctx).isEmpty(), "an acted unit has no targets")
        assertTrue(Gameplay.legalSkills(acted, "p", ctx).isEmpty(), "an acted unit has no skills")
    }
}
