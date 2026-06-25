package com.ccz.core.battle

import com.ccz.core.model.ActiveAilment
import com.ccz.core.model.Ailment
import com.ccz.core.model.Combatant
import com.ccz.core.model.DamageKind
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
import com.ccz.core.model.SkillEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [Ailment.STUN] (ADR 0008): a command-legality ailment that forbids Move/Attack/Cast — only Wait stays legal,
 * so a stunned unit can be passed and the turn advances. Reuses the silence machinery (persistence, refresh,
 * tick-drop, decode whitelist) with no save bump. These tests prove the gate fires on EVERY acting path AND
 * every read-only preview (so the enemy AI degrades a stunned actor to Wait without deadlocking), that the cast
 * draws no RNG, and that the ailment lifts on schedule.
 */
class StunTest {
    private val atkSkill = Skill("atk", "Attack", DamageKind.PHYSICAL, 100)
    private val stunSkill =
        Skill("stun", "Stun", DamageKind.PHYSICAL, 0, RangeSpec(1, 2), listOf(SkillEffect.ApplyAilment(EffectTarget.ENEMY, Ailment.STUN, 1)))
    private val ctx = contextOf(flat(6, 1), classes = classesOf(move = 2), skills = mapOf("atk" to atkSkill, "stun" to stunSkill))

    private fun stunned(c: Combatant): Combatant = c.copy(ailments = listOf(ActiveAilment(Ailment.STUN, 2)))

    /** A stunned player unit with an adjacent enemy — so a Move/Attack/Cast would otherwise have a legal target. */
    private fun field(): BattleState = stateOf(
        stunned(combatant("hero", Faction.PLAYER, Pos(1, 0))),
        combatant("foe", Faction.ENEMY, Pos(2, 0)),
        active = Faction.PLAYER,
    )

    private fun reject(state: BattleState, command: Command): RejectReason =
        assertIs<Gameplay.Outcome.Rejected>(Gameplay.submit(state, command, ctx)).reason

    private fun accept(state: BattleState, command: Command): Resolution =
        assertIs<Gameplay.Outcome.Accepted>(Gameplay.submit(state, command, ctx)).resolution

    private fun casterField(): BattleState = stateOf(
        combatant("caster", Faction.PLAYER, Pos(0, 0)),
        combatant("foe", Faction.ENEMY, Pos(1, 0)),
        active = Faction.PLAYER,
    )

    @Test
    fun aStunnedUnitCannotMove() {
        assertEquals(RejectReason.ACTOR_STUNNED, reject(field(), Command.Move("hero", Pos(0, 0))))
    }

    @Test
    fun aStunnedUnitCannotAttack() {
        assertEquals(RejectReason.ACTOR_STUNNED, reject(field(), Command.Attack("hero", "foe", "atk")))
    }

    @Test
    fun aStunnedUnitCannotCast() {
        // Stun dominates silence: a stunned (even non-silenced) caster reports the more fundamental reason.
        assertEquals(RejectReason.ACTOR_STUNNED, reject(field(), Command.Cast("hero", "foe", "stun")))
    }

    @Test
    fun aStunnedUnitCanStillWait() {
        // Wait stays legal so the turn can advance past the unit (and the AI can consume it) — the only action
        // a stunned unit may take. checkWait does NOT gate stun (it lives on the acting paths, not actorEligibility).
        assertIs<Gameplay.Outcome.Accepted>(Gameplay.submit(field(), Command.Wait("hero"), ctx))
    }

    @Test
    fun aStunnedUnitsActionPreviewsAreAllEmpty() {
        // Every read-only preview must agree with the validator (else the AI/UI would propose a rejected action).
        val state = field()
        assertTrue(Gameplay.legalDestinations(state, "hero", ctx).isEmpty(), "no move destinations")
        assertTrue(Gameplay.legalSkills(state, "hero", ctx).isEmpty(), "no usable skills")
        assertTrue(Gameplay.legalTargets(state, "hero", "atk", ctx).isEmpty(), "no attack targets")
        assertTrue(Gameplay.legalCastTargets(state, "hero", "stun", ctx).isEmpty(), "no cast targets")
    }

    @Test
    fun castingStunAfflictsAnEnemyWithoutDrawingRng() {
        val state = casterField()
        val result = accept(state, Command.Cast("caster", "foe", "stun"))
        assertTrue(result.state.unit("foe").stunned, "the enemy is stunned")
        assertEquals(listOf(ActiveAilment(Ailment.STUN, 1)), result.state.unit("foe").ailments)
        assertEquals(state.rngState, result.state.rngState, "a stun cast draws no RNG — the damage golden is untouched")
        assertEquals("STUN", result.events.filterIsInstance<Event.StatusApplied>().single().status)
    }

    @Test
    fun stunExpiresAfterItsDuration() {
        // duration 1 → lifts after one EndTurn turn-boundary (id-sorted, RNG-free tick).
        val cast = accept(casterField(), Command.Cast("caster", "foe", "stun"))
        assertTrue(cast.state.unit("foe").stunned)
        val ticked = accept(cast.state, Command.EndTurn(Faction.PLAYER)).state
        assertFalse(ticked.unit("foe").stunned, "stun lifts after its duration elapses")
        assertTrue(ticked.unit("foe").ailments.isEmpty(), "the expired ailment is dropped")
    }

    @Test
    fun theEnemyAiWaitsAStunnedActorWithoutDeadlocking() {
        // A stunned enemy is the only un-acted active-side unit: every preview is empty, so the AI degrades to
        // Wait (legal) rather than proposing a Move/Attack the validator would reject (which would abort the turn).
        val state = stateOf(
            stunned(combatant("e", Faction.ENEMY, Pos(1, 0))),
            combatant("hero", Faction.PLAYER, Pos(3, 0)),
            active = Faction.ENEMY,
        )
        assertEquals(Command.Wait("e"), EnemyAi.nextCommand(state, ctx))
        // Submitting that Wait is accepted and marks it acted, so the next plan ends the turn — it terminates.
        val after = accept(state, Command.Wait("e")).state
        assertEquals(Command.EndTurn(Faction.ENEMY), EnemyAi.nextCommand(after, ctx))
    }
}
