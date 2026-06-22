package com.ccz.app.battle

import com.ccz.core.battle.BattleState
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the pure presentation reducer over the demo seed on the JVM (no device). Proves
 * the app's tap loop renders and forwards through game-core without owning combat truth:
 * selection follows what game-core reports legal, and every state change is the
 * authority's, never the reducer's own arithmetic.
 */
class BattleReducerTest {
    private val context = DemoBattle.context()
    private val reducer = BattleReducer(context)

    private fun start(): BattleUiState = reducer.initial(DemoBattle.initialState())
    private fun playerUnit(state: BattleState): Combatant = state.units.values.first { it.faction == Faction.PLAYER }
    private fun enemyUnit(state: BattleState): Combatant = state.units.values.first { it.faction == Faction.ENEMY }

    @Test
    fun tappingFriendlyUnitSelectsItAndExposesDestinations() {
        val ui = start()
        val player = playerUnit(ui.state)
        val after = reducer.tapTile(ui, player.pos)
        assertEquals(player.id, after.selected)
        assertTrue("own tile is a wait-in-place stop", player.pos in after.destinations)
        assertTrue("a selected unit has reachable tiles beyond its own", after.destinations.size > 1)
    }

    @Test
    fun tappingLegalDestinationMovesUnitAndClearsSelection() {
        val ui = start()
        val player = playerUnit(ui.state)
        val selected = reducer.tapTile(ui, player.pos)
        val destination = selected.destinations.first { it != player.pos }
        val moved = reducer.tapTile(selected, destination)
        assertEquals("authority placed the unit on the tapped tile", destination, moved.state.units.getValue(player.id).pos)
        assertNull("selection clears after a move", moved.selected)
        assertTrue("the move is logged", moved.log.size > ui.log.size)
    }

    @Test
    fun tappingEnemyUnitDoesNotSelectIt() {
        val ui = start()
        val after = reducer.tapTile(ui, enemyUnit(ui.state).pos)
        assertNull("the enemy is not the player's to command this turn", after.selected)
    }

    @Test
    fun endTurnFlipsActiveSideAndAdvancesTurn() {
        val ui = start()
        val after = reducer.endTurn(ui)
        assertEquals(Faction.ENEMY, after.state.active)
        assertEquals(ui.state.turn + 1, after.state.turn)
    }

    @Test
    fun tappingUnreachableTileWhileSelectedLeavesStateUnchanged() {
        val ui = start()
        val player = playerUnit(ui.state)
        val selected = reducer.tapTile(ui, player.pos)
        val faraway = Pos(DemoBattle.WIDTH - 1, DemoBattle.HEIGHT - 1)
        assertTrue("precondition: tile is out of move range", faraway !in selected.destinations)
        val after = reducer.tapTile(selected, faraway)
        assertEquals("unmoved units stay put", ui.state.units, after.state.units)
        assertEquals("a non-destination tap appends nothing to the log", ui.log, after.log)
        assertNull("selection clears on an empty non-destination tap", after.selected)
    }

    @Test
    fun staleDestinationTapFailsClosedWithoutMutatingState() {
        val base = start()
        val player = playerUnit(base.state)
        // An empty, out-of-range tile: a destination set that no longer matches what the
        // authority accepts. tapTile routes it to submitMove, which must defer to the
        // rejection rather than fabricate a move.
        val illegal = Pos(DemoBattle.WIDTH - 1, DemoBattle.HEIGHT - 1)
        val stale = base.copy(selected = player.id, destinations = setOf(illegal))
        val after = reducer.tapTile(stale, illegal)
        assertEquals("the reducer never moves a unit the authority rejected", base.state.units, after.state.units)
        assertNull("selection clears after a rejected submit", after.selected)
        assertTrue("the rejection is surfaced in the log", after.log.any { it.contains("rejected") })
    }

    @Test
    fun eventLogTruncatesToMaxLines() {
        var ui = start() // one opening line
        repeat(MAX_LOG_LINES + 1) { ui = reducer.endTurn(ui) } // overflow the cap
        assertEquals("log is capped at MAX_LOG_LINES", MAX_LOG_LINES, ui.log.size)
        assertFalse("the oldest line is dropped past the cap", ui.log.any { it.startsWith("Battle start") })
    }
}
