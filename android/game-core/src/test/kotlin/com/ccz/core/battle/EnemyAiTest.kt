package com.ccz.core.battle

import com.ccz.core.model.ClassTerrain
import com.ccz.core.model.DamageKind
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
import com.ccz.core.model.SkillEffect
import com.ccz.core.model.UnitClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The deterministic aggressive enemy planner: focus-fire the most wounded in-range foe (tie-broken
 * nearest then id), else step toward the nearest foe, else Wait; end the turn when every active-side unit
 * is exhausted. Plans are pure (no RNG) and every planned command is one [Gameplay.submit] accepts.
 */
class EnemyAiTest {
    private val ctx = contextOf(flat(6, 1)) // default skill "atk" (melee), class move = 5

    // A support context (ADR 0008): an attack skill + a heal (cast/effect) skill, for the heal-AI tests.
    private val atk = Skill("atk", "Attack", DamageKind.PHYSICAL, 100)
    private val healSkill =
        Skill("heal", "Heal", DamageKind.PHYSICAL, 0, RangeSpec(0, 1), listOf(SkillEffect.Heal(EffectTarget.ALLY, 50)))
    private fun medicCtx(skills: List<String>) =
        contextOf(flat(6, 1), skills = mapOf("atk" to atk, "heal" to healSkill), loadouts = mapOf("medic" to skills))

    @Test
    fun healsTheMostWoundedAllyWhenItCan() {
        // "medic" (full) is the lowest-id actor; ally "wounded" (30/100, below half) is adjacent; foe far.
        // Support-first: heal the wounded ally. (Ids chosen so the healer, not the wounded unit, is the actor.)
        val state = stateOf(
            combatant("medic", Faction.ENEMY, Pos(0, 0)),
            combatant("wounded", Faction.ENEMY, Pos(1, 0), hp = 30),
            combatant("foe", Faction.PLAYER, Pos(5, 0)),
            active = Faction.ENEMY,
        )
        assertEquals(Command.Cast("medic", "wounded", "heal"), EnemyAi.nextCommand(state, medicCtx(listOf("atk", "heal"))))
    }

    @Test
    fun doesNotHealAHealthyAllyAndAttacksInstead() {
        // ally "zally" is full HP (not meaningfully wounded) and a foe is adjacent to the actor → attack with
        // the DAMAGE skill, not heal. ("medic" < "zally" so the healer is the actor.)
        val state = stateOf(
            combatant("medic", Faction.ENEMY, Pos(0, 0)),
            combatant("zally", Faction.ENEMY, Pos(0, 1)),
            combatant("foe", Faction.PLAYER, Pos(1, 0)),
            active = Faction.ENEMY,
        )
        assertEquals(Command.Attack("medic", "foe", "atk"), EnemyAi.nextCommand(state, medicCtx(listOf("atk", "heal"))))
    }

    @Test
    fun neverAttacksWithAHealSkill() {
        // A pure healer (only a heal skill) adjacent to a foe, with no wounded ally, must NOT attack with the
        // heal and must NOT cast — a cast/effect skill is never a chip attack; it waits/repositions instead.
        val state = stateOf(
            combatant("medic", Faction.ENEMY, Pos(0, 0)),
            combatant("p", Faction.PLAYER, Pos(1, 0)),
            active = Faction.ENEMY,
        )
        val command = EnemyAi.nextCommand(state, medicCtx(listOf("heal")))
        assertTrue(command !is Command.Attack, "a heal skill is never used as an attack")
        assertTrue(command !is Command.Cast, "no wounded ally → no heal cast")
    }

    @Test
    fun repositionsOntoFavorableCombatTerrainAmongFiringTiles() {
        // 'inf' favors hill (+20%); a 3x3 with a hill firing tile at (1,0) vs a plain firing tile at (0,1),
        // both adjacent to the foe at (1,1) and equidistant from the actor. Without terrain the x/y
        // tie-break picks plain (0,1); the terrain-aware planner takes the hill.
        val classes = mapOf(
            "inf" to UnitClass("inf", "Infantry", "foot", 5, terrain = ClassTerrain(affinity = mapOf("hill" to 120))),
        )
        val map = BattleMap(
            3, 3,
            listOf(
                listOf(MapTile("plain", 1), MapTile("hill", 1), MapTile("plain", 1)),
                listOf(MapTile("plain", 1), MapTile("plain", 1), MapTile("plain", 1)),
                listOf(MapTile("plain", 1), MapTile("plain", 1), MapTile("plain", 1)),
            ),
        )
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(0, 0)),
            combatant("p", Faction.PLAYER, Pos(1, 1)),
            active = Faction.ENEMY,
        )
        assertEquals(Command.Move("e", Pos(1, 0)), EnemyAi.nextCommand(state, contextOf(map, classes = classes)))
    }

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
    fun focusFiresTheMostWoundedFoeOverNearerAndIdTieBreaks() {
        // A spear (range 1-2) enemy with two foes in range: a healthy one adjacent (dist 1, and the id that
        // wins the tie-break) and a wounded one at dist 2. The aggressive planner secures the kill — it
        // strikes the low-HP "zwounded" foe, proving HP priority overrides both nearest and id tie-breaks.
        val spearCtx = contextOf(flat(6, 1), skills = skillsOf("spear", RangeSpec(1, 2)), loadouts = mapOf("e" to listOf("spear")))
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(0, 0)),
            combatant("ahealthy", Faction.PLAYER, Pos(1, 0), hp = 90),
            combatant("zwounded", Faction.PLAYER, Pos(2, 0), hp = 10),
            active = Faction.ENEMY,
        )
        assertEquals(Command.Attack("e", "zwounded", "spear"), EnemyAi.nextCommand(state, spearCtx))
    }

    @Test
    fun amongEquallyWoundedFoesItStrikesTheNearest() {
        // Two foes at the SAME HP but different distances, both in a spear's 1-2 range: the nearest wins the
        // second tie-break key (Manhattan), even though the far one's id ("a") would win the id tie-break.
        val spearCtx = contextOf(flat(6, 1), skills = skillsOf("spear", RangeSpec(1, 2)), loadouts = mapOf("e" to listOf("spear")))
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(0, 0)),
            combatant("a", Faction.PLAYER, Pos(2, 0), hp = 40), // farther, but id wins a tie
            combatant("near", Faction.PLAYER, Pos(1, 0), hp = 40), // equally wounded but adjacent → struck
            active = Faction.ENEMY,
        )
        assertEquals(Command.Attack("e", "near", "spear"), EnemyAi.nextCommand(state, spearCtx))
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
    fun aRangedUnitRepositionsToFireRatherThanIdling() {
        // A bow (range 2-3) enemy adjacent to a foe (distance 1, inside its min range) cannot fire from
        // where it stands; it should step to a tile within its range band, then attack — not Wait.
        val bowCtx = contextOf(flat(6, 1), skills = skillsOf("bow", RangeSpec(2, 3)), loadouts = mapOf("e" to listOf("bow")))
        val state = stateOf(
            combatant("e", Faction.ENEMY, Pos(1, 0)),
            combatant("p", Faction.PLAYER, Pos(0, 0)),
            active = Faction.ENEMY,
        )
        val move = assertIs<Command.Move>(EnemyAi.nextCommand(state, bowCtx), "the archer repositions instead of idling")
        val moved = assertIs<Gameplay.Outcome.Accepted>(Gameplay.submit(state, move, bowCtx)).resolution.state
        assertTrue("p" in Gameplay.legalTargets(moved, "e", "bow", bowCtx), "after repositioning it can fire on the foe")
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
