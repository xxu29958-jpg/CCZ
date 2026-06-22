package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The deterministic aggressive enemy planner: attack the nearest in-range foe, else step toward the
 * nearest foe, else Wait; end the turn when every active-side unit is exhausted. Plans are pure (no RNG)
 * and every planned command is one [Gameplay.submit] accepts.
 */
class EnemyAiTest {
    private val ctx = contextOf(flat(6, 1)) // default skill "atk" (melee), class move = 5

    @Test
    fun attacksAnAdjacentFoe() {
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(0, 0)),
            combatant("p", Faction.PLAYER, Pos(1, 0)),
            active = Faction.ENEMY,
        )
        assertEquals(Command.Attack("e", "p", "atk"), EnemyAi.nextCommand(state, ctx))
    }

    @Test
    fun stepsTowardADistantFoe() {
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(0, 0)),
            combatant("p", Faction.PLAYER, Pos(5, 0)),
            active = Faction.ENEMY,
        )
        val command = assertIs<Command.Move>(EnemyAi.nextCommand(state, ctx), "with no foe in range the enemy advances")
        assertTrue(manhattan(command.to, Pos(5, 0)) < manhattan(Pos(0, 0), Pos(5, 0)), "the move closes distance on the foe")
    }

    @Test
    fun waitsWhenItCannotCloseOnAFoe() {
        val pinned = contextOf(flat(6, 1), classes = classesOf(move = 0))
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(0, 0)),
            combatant("p", Faction.PLAYER, Pos(5, 0)),
            active = Faction.ENEMY,
        )
        assertEquals(Command.Wait("e"), EnemyAi.nextCommand(state, pinned))
    }

    @Test
    fun endsTheTurnWhenEveryUnitIsExhausted() {
        val state = stateOf(combatant("e", Faction.ENEMY, Pos(0, 0)), active = Faction.ENEMY).markActed("e")
        assertEquals(Command.EndTurn(Faction.ENEMY), EnemyAi.nextCommand(state, ctx))
    }

    @Test
    fun attacksTheNearestFoeTieBrokenById() {
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(1, 0)),
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("z", Faction.PLAYER, Pos(2, 0)),
            active = Faction.ENEMY,
        )
        // Both foes are distance 1; the id tie-break picks "a".
        assertEquals(Command.Attack("e", "a", "atk"), EnemyAi.nextCommand(state, ctx))
    }

    @Test
    fun planIsPureAndAlwaysAcceptedBySubmit() {
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(0, 0)),
            combatant("p", Faction.PLAYER, Pos(5, 0)),
            active = Faction.ENEMY,
        )
        val first = EnemyAi.nextCommand(state, ctx)
        assertEquals(first, EnemyAi.nextCommand(state, ctx), "the plan is a pure function of state")
        assertTrue(Gameplay.submit(state, first, ctx) is Gameplay.Outcome.Accepted, "the planned command is legal")
    }
}
