package com.ccz.core.battle

import com.ccz.core.model.AffectedStat
import com.ccz.core.model.DamageKind
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Faction
import com.ccz.core.model.HealMode
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
import com.ccz.core.model.SkillEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Command.Cast / Resolver.cast (ADR 0008 Phase 1): a deterministic, RNG-free single-target heal applied
 * to a SELF/ALLY target. Proves the cast heals by the flat amount (clamped at max HP), draws no RNG
 * (rngState preserved — the damage golden is therefore untouched), spends the caster's action, and is
 * gated by CommandValidator as the friendly-targeting inverse of an attack.
 */
class CastTest {
    private val healSkill =
        Skill("heal", "Heal", DamageKind.PHYSICAL, 0, RangeSpec(0, 1), listOf(SkillEffect.Heal(EffectTarget.ALLY, 30)))
    private val selfHealSkill =
        Skill("self_heal", "Self Heal", DamageKind.PHYSICAL, 0, RangeSpec(0, 0), listOf(SkillEffect.Heal(EffectTarget.SELF, 30)))
    private val atkSkill = Skill("atk", "Attack", DamageKind.PHYSICAL, 100)
    private val percentHealSkill =
        Skill("pheal", "% Heal", DamageKind.PHYSICAL, 0, RangeSpec(0, 1), listOf(SkillEffect.Heal(EffectTarget.ALLY, 40, HealMode.PERCENT_MAX)))
    private val buffSkill =
        Skill("buff", "Buff", DamageKind.PHYSICAL, 0, RangeSpec(0, 1), listOf(SkillEffect.StatDelta(EffectTarget.ALLY, AffectedStat.ATK, 15)))
    private val ctx = contextOf(
        flat(6, 1),
        skills = mapOf(
            "heal" to healSkill, "self_heal" to selfHealSkill, "atk" to atkSkill,
            "pheal" to percentHealSkill, "buff" to buffSkill,
        ),
    )

    private fun field(casterHp: Int = 100, allyHp: Int = 50): BattleState = stateOf(
        combatant("medic", Faction.PLAYER, Pos(0, 0), hp = casterHp),
        combatant("ally", Faction.PLAYER, Pos(1, 0), hp = allyHp),
        combatant("foe", Faction.ENEMY, Pos(3, 0)),
        active = Faction.PLAYER,
    )

    private fun accept(state: BattleState, command: Command): Resolution =
        assertIs<Gameplay.Outcome.Accepted>(Gameplay.submit(state, command, ctx)).resolution

    private fun reject(state: BattleState, command: Command): RejectReason =
        assertIs<Gameplay.Outcome.Rejected>(Gameplay.submit(state, command, ctx)).reason

    @Test
    fun castHealsAnAllyByTheFlatAmountWithoutDrawingRng() {
        val state = field(allyHp = 50)
        val result = accept(state, Command.Cast("medic", "ally", "heal"))
        assertEquals(80, result.state.unit("ally").hp, "ally healed by the flat amount")
        assertEquals(state.rngState, result.state.rngState, "a cast draws no RNG — rngState is preserved")
        assertTrue(result.state.hasActed("medic"), "the caster spent its action")
        val healed = result.events.filterIsInstance<Event.Healed>().single()
        assertEquals("ally", healed.unit)
        assertEquals(30, healed.amount)
    }

    @Test
    fun percentHealRestoresAPercentOfMaxHp() {
        // ally 50/100, heal 40% of max HP = 40 → 90 (integer-truncating hpMax * amount / 100, no float).
        val result = accept(field(allyHp = 50), Command.Cast("medic", "ally", "pheal"))
        assertEquals(90, result.state.unit("ally").hp, "percent heal restores hpMax * pct / 100")
    }

    @Test
    fun castHealClampsAtMaxHp() {
        val result = accept(field(allyHp = 90), Command.Cast("medic", "ally", "heal"))
        assertEquals(100, result.state.unit("ally").hp, "heal caps at max HP")
        assertEquals(10, result.events.filterIsInstance<Event.Healed>().single().amount, "event shows only HP actually restored")
    }

    @Test
    fun castHealOnFullHpAllyIsANoOpButStillSpendsTheAction() {
        val result = accept(field(allyHp = 100), Command.Cast("medic", "ally", "heal"))
        assertEquals(100, result.state.unit("ally").hp, "a full-HP ally is unchanged")
        assertTrue(result.events.filterIsInstance<Event.Healed>().isEmpty(), "no heal event when nothing was restored")
        assertTrue(result.state.hasActed("medic"), "the action is still spent")
    }

    @Test
    fun selfHealTargetsTheCaster() {
        val result = accept(field(casterHp = 40), Command.Cast("medic", "medic", "self_heal"))
        assertEquals(70, result.state.unit("medic").hp, "the caster healed itself")
    }

    @Test
    fun castingAnAllyHealOnAnEnemyIsRejected() {
        assertEquals(RejectReason.CAST_TARGET_INVALID, reject(field(), Command.Cast("medic", "foe", "heal")))
    }

    @Test
    fun castingADamageOnlySkillIsRejected() {
        assertEquals(RejectReason.SKILL_HAS_NO_EFFECT, reject(field(), Command.Cast("medic", "ally", "atk")))
    }

    @Test
    fun castingASelfSkillOnAnotherUnitIsRejected() {
        assertEquals(RejectReason.CAST_TARGET_INVALID, reject(field(), Command.Cast("medic", "ally", "self_heal")))
    }

    @Test
    fun castingOutOfRangeIsRejected() {
        val state = stateOf(
            combatant("medic", Faction.PLAYER, Pos(0, 0)),
            combatant("ally", Faction.PLAYER, Pos(4, 0), hp = 50), // distance 4 > heal range 1
            active = Faction.PLAYER,
        )
        assertEquals(RejectReason.OUT_OF_CAST_RANGE, reject(state, Command.Cast("medic", "ally", "heal")))
    }

    @Test
    fun statDeltaBuffsTheTargetStatAndEmitsAnEvent() {
        // ally base atk = 80 (fixture); a +15 ALLY buff → 95, with a StatChanged event the UI can surface.
        val result = accept(field(), Command.Cast("medic", "ally", "buff"))
        assertEquals(95, result.state.unit("ally").stats.atk, "ally atk buffed by the flat amount")
        val event = result.events.filterIsInstance<Event.StatChanged>().single()
        assertEquals(AffectedStat.ATK, event.stat)
        assertEquals(15, event.amount)
    }

    @Test
    fun legalCastTargetsForABuffAreSameSideInRange() {
        // ALLY band, range 0-1: medic (self) + ally; the enemy is excluded by band and range.
        assertEquals(setOf("medic", "ally"), Gameplay.legalCastTargets(field(), "medic", "buff", ctx))
    }

    @Test
    fun legalCastTargetsReportsSameSideUnitsInRangeForAnAllyHeal() {
        // heal range 0-1, ALLY band: medic (self, dist 0) + ally (dist 1); the enemy at dist 3 is excluded
        // both by band and range.
        assertEquals(setOf("medic", "ally"), Gameplay.legalCastTargets(field(), "medic", "heal", ctx))
    }

    @Test
    fun legalCastTargetsForASelfHealIsOnlyTheCaster() {
        assertEquals(setOf("medic"), Gameplay.legalCastTargets(field(), "medic", "self_heal", ctx))
    }

    @Test
    fun legalCastTargetsIsEmptyForADamageOnlySkill() {
        assertTrue(Gameplay.legalCastTargets(field(), "medic", "atk", ctx).isEmpty(), "a damage skill has no cast targets")
    }

    @Test
    fun legalCastTargetsMatchesWhatSubmitAccepts() {
        // query⟺submit parity: every reported target is a Cast submit accepts; a non-reported unit (the
        // enemy) is rejected. (The band rule is single-sourced in castTargetAllows, so they cannot diverge.)
        val state = field()
        val reported = Gameplay.legalCastTargets(state, "medic", "heal", ctx)
        reported.forEach { id ->
            assertIs<Gameplay.Outcome.Accepted>(Gameplay.submit(state, Command.Cast("medic", id, "heal"), ctx))
        }
        assertTrue("foe" !in reported, "the enemy is not a cast target")
    }
}
