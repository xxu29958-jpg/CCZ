package com.ccz.core.battle

import com.ccz.core.event.SScript
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
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

    private val twoSkills = mapOf(
        "melee" to Skill("melee", "Melee", DamageKind.PHYSICAL, 100, RangeSpec.MELEE),
        "bow" to Skill("bow", "Bow", DamageKind.PHYSICAL, 80, RangeSpec(2, 3)),
    )

    @Test
    fun legalSkillsReturnsTheConfiguredLoadoutFilteredToKnownSkillsInOrder() {
        val context = contextOf(flat(3, 1), skills = twoSkills, loadouts = mapOf("a" to listOf("bow", "melee", "ghost")))
        val state = stateOf(combatant("a", Faction.PLAYER, Pos(0, 0)))
        // Loadout order is preserved; "ghost" (not in the skill table) is dropped fail-closed.
        assertEquals(listOf("bow", "melee"), Gameplay.legalSkills(state, "a", context))
    }

    @Test
    fun legalSkillsFallsBackToTheFullTableWithoutALoadoutAndIsEmptyWhenUnattackable() {
        val context = contextOf(flat(3, 3), skills = twoSkills) // no loadouts → unconstrained
        val live = stateOf(combatant("a", Faction.PLAYER, Pos(0, 0)))
        assertEquals(setOf("melee", "bow"), Gameplay.legalSkills(live, "a", context).toSet())
        assertTrue(Gameplay.legalSkills(live, "ghost", context).isEmpty(), "an unknown unit can use no skill")

        val dead = stateOf(combatant("a", Faction.PLAYER, Pos(0, 0), hp = 0))
        assertTrue(Gameplay.legalSkills(dead, "a", context).isEmpty(), "a dead unit can use no skill")

        val enemyTurn = stateOf(combatant("e", Faction.ENEMY, Pos(0, 0)), active = Faction.PLAYER)
        assertTrue(Gameplay.legalSkills(enemyTurn, "e", context).isEmpty(), "an off-side unit can use no skill")
    }

    @Test
    fun aSkillOutsideTheLoadoutIsNeitherTargetedNorAcceptedWhileAllowedOnesStillRangeCheck() {
        // "a" may use only melee; bow is in the table but not in a's loadout.
        val context = contextOf(flat(3, 1), skills = twoSkills, loadouts = mapOf("a" to listOf("melee")))
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("foe", Faction.ENEMY, Pos(2, 0)), // distance 2: in bow range, out of melee
        )
        // Bow would reach foe by range, but it is not in the loadout → no targets, and submit rejects it.
        assertTrue(Gameplay.legalTargets(state, "a", "bow", context).isEmpty(), "a skill outside the loadout has no targets")
        val outcome = Gameplay.submit(state, Command.Attack("a", "foe", "bow"), context)
        assertTrue(outcome is Gameplay.Outcome.Rejected, "submit rejects a loadout-excluded skill")
        assertEquals(RejectReason.SKILL_NOT_IN_LOADOUT, (outcome as Gameplay.Outcome.Rejected).reason)
        // Melee is allowed, but foe is out of melee range → still empty, this time on range not loadout.
        assertTrue(Gameplay.legalTargets(state, "a", "melee", context).isEmpty(), "an allowed skill still range-checks")
    }

    // --- outcome query: the read-only win/lose verdict the presentation layer polls. ---

    private fun sScript(
        win: List<WinLoseCondition> = emptyList(),
        lose: List<WinLoseCondition> = emptyList(),
    ): SScript = SScript(id = "s", win = win, lose = lose, pre = emptyList(), mid = emptyList(), post = emptyList())

    @Test
    fun outcomeIsOngoingWhileAnEnemyLivesAndNoLoseConditionIsMet() {
        val state = stateOf(combatant("p", Faction.PLAYER, Pos(0, 0)), combatant("e", Faction.ENEMY, Pos(1, 0)))
        val script = sScript(win = listOf(WinLoseCondition.AnnihilateEnemies), lose = listOf(WinLoseCondition.ProtectAlive("p")))
        assertEquals(BattleOutcome.ONGOING, Gameplay.outcome(state, script))
    }

    @Test
    fun outcomeIsVictoryWhenEnemiesAreAnnihilated() {
        val state = stateOf(combatant("p", Faction.PLAYER, Pos(0, 0)), combatant("e", Faction.ENEMY, Pos(1, 0), hp = 0))
        assertEquals(BattleOutcome.VICTORY, Gameplay.outcome(state, sScript(win = listOf(WinLoseCondition.AnnihilateEnemies))))
    }

    @Test
    fun outcomeIsDefeatWhenAProtectedUnitHasFallen() {
        val state = stateOf(combatant("p", Faction.PLAYER, Pos(0, 0), hp = 0), combatant("e", Faction.ENEMY, Pos(1, 0)))
        assertEquals(BattleOutcome.DEFEAT, Gameplay.outcome(state, sScript(lose = listOf(WinLoseCondition.ProtectAlive("p")))))
    }

    @Test
    fun outcomeLetsLoseWinTiesMirroringWinLose() {
        // Protagonist down AND enemies annihilated on the same state → defeat takes precedence.
        val state = stateOf(combatant("p", Faction.PLAYER, Pos(0, 0), hp = 0), combatant("e", Faction.ENEMY, Pos(1, 0), hp = 0))
        val script = sScript(win = listOf(WinLoseCondition.AnnihilateEnemies), lose = listOf(WinLoseCondition.ProtectAlive("p")))
        assertEquals(BattleOutcome.DEFEAT, Gameplay.outcome(state, script))
        assertEquals(WinLose.evaluate(state, script), Gameplay.outcome(state, script), "outcome is exactly WinLose.evaluate")
    }

    @Test
    fun outcomeIsOngoingWithEmptyWinLoseLists() {
        val state = stateOf(combatant("p", Faction.PLAYER, Pos(0, 0)), combatant("e", Faction.ENEMY, Pos(1, 0), hp = 0))
        assertEquals(BattleOutcome.ONGOING, Gameplay.outcome(state, sScript()), "no conditions configured → never decided")
    }
}
