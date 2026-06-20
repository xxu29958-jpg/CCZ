package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Turn ownership is by SIDE: PLAYER and ALLY act together (see [sameSide]), so an
 * ALLY unit may act while active == PLAYER. A zero-distance move is an accepted
 * wait-in-place no-op. These pin the contract clarified after adversarial review.
 */
class TurnOwnershipTest {
    @Test
    fun allyUnitMovesOnPlayerSideTurn() {
        val state = stateOf(combatant("ally", Faction.ALLY, Pos(0, 0)), active = Faction.PLAYER)
        assertNull(CommandValidator.check(state, Command.Move("ally", Pos(2, 0)), contextOf(flat(5, 1))))
    }

    @Test
    fun allyUnitAttacksEnemyOnPlayerSideTurn() {
        val state = stateOf(
            combatant("ally", Faction.ALLY, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
            active = Faction.PLAYER,
        )
        assertNull(CommandValidator.check(state, Command.Attack("ally", "e", "atk"), contextOf(flat(5, 1))))
    }

    @Test
    fun moveToOwnTileIsAcceptedAsWait() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(2, 2)))
        assertNull(CommandValidator.check(state, Command.Move("m", Pos(2, 2)), contextOf(flat(5, 5))))
    }
}
