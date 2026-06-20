package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameplayOutcomeTest {
    private val context = contextOf(flat(5, 5))

    @Test
    fun endTurnByActiveFactionIsAccepted() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)), active = Faction.PLAYER)
        val outcome = Gameplay.submit(state, Command.EndTurn(Faction.PLAYER), context)

        val accepted = assertIs<Gameplay.Outcome.Accepted>(outcome)
        assertEquals(listOf(Event.TurnEnded(Faction.PLAYER)), accepted.resolution.events)
        assertEquals(Faction.ENEMY, accepted.resolution.state.active)
    }

    @Test
    fun endTurnByWrongFactionIsRejected() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)), active = Faction.PLAYER)
        val outcome = Gameplay.submit(state, Command.EndTurn(Faction.ENEMY), context)

        assertEquals(RejectReason.WRONG_END_TURN_FACTION, assertIs<Gameplay.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun acceptedMoveUpdatesPosition() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        val outcome = Gameplay.submit(state, Command.Move("m", Pos(2, 1)), context)

        val accepted = assertIs<Gameplay.Outcome.Accepted>(outcome)
        assertEquals(Pos(2, 1), accepted.resolution.state.unit("m").pos)
    }

    @Test
    fun acceptedAttackProducesDamageAndDoesNotMutateInputState() {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        val outcome = Gameplay.submit(state, Command.Attack("a", "e", "atk"), context)

        val accepted = assertIs<Gameplay.Outcome.Accepted>(outcome)
        val damaged = accepted.resolution.events.filterIsInstance<Event.Damaged>().single()
        assertEquals("e", damaged.target)
        assertTrue(damaged.amount > 0)
        assertEquals(100, state.unit("e").hp) // input state untouched
        assertTrue(accepted.resolution.state.unit("e").hp < 100)
    }

    @Test
    fun rejectedCommandIsSideEffectFreeAndDeterministic() {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(4, 4)),
        )
        val legal = Command.Move("a", Pos(2, 0))
        val illegal = Command.Attack("a", "e", "atk") // out of melee range

        val before = Gameplay.submit(state, legal, context)
        assertIs<Gameplay.Outcome.Rejected>(Gameplay.submit(state, illegal, context))
        val after = Gameplay.submit(state, legal, context)

        assertEquals(before, after) // the intervening rejection perturbed nothing
    }
}
