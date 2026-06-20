package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveLegalityTest {
    private fun check(state: BattleState, to: Pos, context: BattleContext, unit: String = "m"): RejectReason? =
        CommandValidator.check(state, Command.Move(unit, to), context)

    @Test
    fun unknownUnitIsRejected() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        assertEquals(RejectReason.UNIT_NOT_FOUND, check(state, Pos(1, 0), contextOf(flat(3, 3)), unit = "ghost"))
    }

    @Test
    fun deadUnitCannotMove() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0), hp = 0))
        assertEquals(RejectReason.UNIT_DEAD, check(state, Pos(1, 0), contextOf(flat(3, 3))))
    }

    @Test
    fun moveByInactiveFactionIsRejected() {
        val state = stateOf(combatant("m", Faction.ENEMY, Pos(0, 0)), active = Faction.PLAYER)
        assertEquals(RejectReason.NOT_ACTIVE_FACTION, check(state, Pos(1, 0), contextOf(flat(3, 3))))
    }

    @Test
    fun moveByUnitWithUnknownClassIsRejected() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0), classId = "phantom"))
        // live + active, but classId is absent from classesOf() -> must reach the class lookup branch.
        assertEquals(RejectReason.UNKNOWN_CLASS, check(state, Pos(1, 0), contextOf(flat(3, 3))))
    }

    @Test
    fun moveOntoOutOfBoundsTileIsRejected() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        assertEquals(RejectReason.DESTINATION_OUT_OF_BOUNDS, check(state, Pos(9, 9), contextOf(flat(3, 3))))
    }

    @Test
    fun moveOntoImpassableTileIsRejected() {
        val map = BattleMap(2, 1, listOf(listOf(MapTile("plain", 1), MapTile("wall", 1, passable = false))))
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        assertEquals(RejectReason.DESTINATION_IMPASSABLE, check(state, Pos(1, 0), contextOf(map)))
    }

    @Test
    fun moveOntoOccupiedTileIsRejected() {
        val state = stateOf(
            combatant("m", Faction.PLAYER, Pos(0, 0)),
            combatant("blocker", Faction.PLAYER, Pos(1, 0)),
        )
        assertEquals(RejectReason.DESTINATION_OCCUPIED, check(state, Pos(1, 0), contextOf(flat(3, 3))))
    }

    @Test
    fun moveBeyondMoveBudgetIsRejected() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        assertEquals(
            RejectReason.OUT_OF_MOVE_RANGE,
            check(state, Pos(6, 0), contextOf(flat(8, 1), classes = classesOf(move = 5))),
        )
    }

    @Test
    fun moveWithinBudgetOverCostlyTerrainIsAccepted() {
        val map = BattleMap(
            3, 1,
            listOf(listOf(MapTile("plain", 1), MapTile("marsh", 3), MapTile("plain", 1))),
        )
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        assertNull(check(state, Pos(2, 0), contextOf(map, classes = classesOf(move = 5))))
    }

    @Test
    fun enemyBlocksPathWhileAllyIsPassThrough() {
        val mover = combatant("m", Faction.PLAYER, Pos(0, 0))
        val context = contextOf(flat(3, 1), classes = classesOf(move = 2))

        val enemyBlocked = stateOf(mover, combatant("e", Faction.ENEMY, Pos(1, 0)))
        assertEquals(RejectReason.OUT_OF_MOVE_RANGE, check(enemyBlocked, Pos(2, 0), context))

        val allyOpen = stateOf(mover, combatant("a", Faction.PLAYER, Pos(1, 0)))
        assertNull(check(allyOpen, Pos(2, 0), context))
    }
}
