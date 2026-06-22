package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Gameplay.legalDestinations] is the read-only move-highlight query the presentation
 * layer renders. These tests pin it to the same authority [CommandValidator] enforces:
 * a tile is reported reachable iff a [Command.Move] there would be accepted.
 */
class GameplayQueryTest {
    @Test
    fun reachableStopsMatchOnFlatMap() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        val destinations = Gameplay.legalDestinations(state, "m", contextOf(flat(3, 1), classes = classesOf(move = 1)))
        // budget 1 on a flat row: own tile (free) plus the single adjacent step.
        assertEquals(setOf(Pos(0, 0), Pos(1, 0)), destinations)
    }

    @Test
    fun ownTileIsAlwaysReachableForActiveUnit() {
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(2, 2)))
        // Move-to-self is an accepted wait-in-place, so a selectable unit never gets an empty set.
        assertTrue(Pos(2, 2) in Gameplay.legalDestinations(state, "m", contextOf(flat(5, 5))))
    }

    @Test
    fun occupiedTilesAreExcludedButPassThroughIsReachable() {
        val state = stateOf(
            combatant("m", Faction.PLAYER, Pos(0, 0)),
            combatant("ally", Faction.PLAYER, Pos(1, 0)),
        )
        val destinations = Gameplay.legalDestinations(state, "m", contextOf(flat(3, 1)))
        assertFalse(Pos(1, 0) in destinations, "a tile held by another unit is not a legal stop")
        assertTrue(Pos(2, 0) in destinations, "a friendly unit may be passed through to an open tile")
    }

    @Test
    fun nonSelectableUnitsGetEmptySet() {
        val context = contextOf(flat(3, 3))
        val live = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        assertEquals(emptySet(), Gameplay.legalDestinations(live, "ghost", context))

        val dead = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0), hp = 0))
        assertEquals(emptySet(), Gameplay.legalDestinations(dead, "m", context))

        val enemyTurn = stateOf(combatant("e", Faction.ENEMY, Pos(0, 0)), active = Faction.PLAYER)
        assertEquals(emptySet(), Gameplay.legalDestinations(enemyTurn, "e", context))

        val noClass = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0), classId = "phantom"))
        assertEquals(emptySet(), Gameplay.legalDestinations(noClass, "m", context))
    }

    @Test
    fun everyReportedDestinationIsAcceptedBySubmitAndNoOtherTileIs() {
        val context = contextOf(flat(3, 1), classes = classesOf(move = 1))
        val state = stateOf(combatant("m", Faction.PLAYER, Pos(0, 0)))
        val destinations = Gameplay.legalDestinations(state, "m", context)
        for (x in 0 until 3) {
            val tile = Pos(x, 0)
            val accepted = Gameplay.submit(state, Command.Move("m", tile), context) is Gameplay.Outcome.Accepted
            assertEquals(tile in destinations, accepted, "query and submit must agree on $tile")
        }
    }

    @Test
    fun onlyEnemiesWithinSkillRangeAreReportedTargets() {
        val context = contextOf(flat(6, 1), skills = skillsOf("bow", RangeSpec(1, 2)))
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("near", Faction.ENEMY, Pos(2, 0)), // distance 2: inside the band
            combatant("far", Faction.ENEMY, Pos(5, 0)), // distance 5: outside it
        )
        assertEquals(setOf("near"), Gameplay.legalTargets(state, "a", "bow", context))
    }

    @Test
    fun sameSideUnitsAndSelfAreNeverTargetsEvenWithinRange() {
        // A range band that covers distance 0 would admit the attacker itself on distance alone;
        // self and same-side units must still be excluded by the targeting rule.
        val context = contextOf(flat(4, 1), skills = skillsOf("bow", RangeSpec(0, 3)))
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("ally", Faction.ALLY, Pos(1, 0)),
            combatant("mate", Faction.PLAYER, Pos(2, 0)),
            combatant("foe", Faction.ENEMY, Pos(3, 0)),
        )
        assertEquals(setOf("foe"), Gameplay.legalTargets(state, "a", "bow", context))
    }

    @Test
    fun deadEnemiesAreNotTargets() {
        val context = contextOf(flat(3, 1), skills = skillsOf("bow", RangeSpec(1, 2)))
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("downed", Faction.ENEMY, Pos(1, 0), hp = 0),
        )
        assertTrue(Gameplay.legalTargets(state, "a", "bow", context).isEmpty())
    }

    @Test
    fun unattackableQueriesGetEmptySet() {
        val context = contextOf(flat(3, 1)) // default skill "atk", melee
        val live = stateOf(combatant("a", Faction.PLAYER, Pos(0, 0)), combatant("e", Faction.ENEMY, Pos(1, 0)))
        assertEquals(emptySet(), Gameplay.legalTargets(live, "ghost", "atk", context), "unknown attacker")
        assertEquals(emptySet(), Gameplay.legalTargets(live, "a", "missing", context), "unknown skill")

        val dead = stateOf(combatant("a", Faction.PLAYER, Pos(0, 0), hp = 0), combatant("e", Faction.ENEMY, Pos(1, 0)))
        assertEquals(emptySet(), Gameplay.legalTargets(dead, "a", "atk", context), "dead attacker")

        val enemyTurn = stateOf(
            combatant("e", Faction.ENEMY, Pos(0, 0)),
            combatant("p", Faction.PLAYER, Pos(1, 0)),
            active = Faction.PLAYER,
        )
        assertEquals(emptySet(), Gameplay.legalTargets(enemyTurn, "e", "atk", context), "attacker not on the active side")
    }

    @Test
    fun everyReportedTargetIsAcceptedBySubmitAndNoOtherUnitIs() {
        val context = contextOf(flat(6, 1), skills = skillsOf("bow", RangeSpec(1, 2)))
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("ally", Faction.PLAYER, Pos(1, 0)),
            combatant("near", Faction.ENEMY, Pos(2, 0)),
            combatant("far", Faction.ENEMY, Pos(5, 0)),
        )
        val targets = Gameplay.legalTargets(state, "a", "bow", context)
        for (id in listOf("a", "ally", "near", "far")) {
            val accepted = Gameplay.submit(state, Command.Attack("a", id, "bow"), context) is Gameplay.Outcome.Accepted
            assertEquals(id in targets, accepted, "query and submit must agree on $id")
        }
    }
}
