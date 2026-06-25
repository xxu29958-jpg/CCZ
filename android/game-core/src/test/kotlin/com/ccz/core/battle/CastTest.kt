package com.ccz.core.battle

import com.ccz.core.model.ActiveAilment
import com.ccz.core.model.AffectedStat
import com.ccz.core.model.Ailment
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
import kotlin.test.assertFalse
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
    private val debuffSkill =
        Skill("debuff", "Debuff", DamageKind.PHYSICAL, 0, RangeSpec(1, 3), listOf(SkillEffect.StatDelta(EffectTarget.ENEMY, AffectedStat.ATK, -15)))
    private val timedBuffSkill =
        Skill("tbuff", "Timed Buff", DamageKind.PHYSICAL, 0, RangeSpec(0, 1), listOf(SkillEffect.StatDelta(EffectTarget.ALLY, AffectedStat.ATK, 15, duration = 2)))
    // A timed debuff whose magnitude EXCEEDS the target's stat, to exercise the floor-at-0 reversal symmetry.
    private val bigDebuffSkill =
        Skill("crush", "Crush", DamageKind.PHYSICAL, 0, RangeSpec(1, 3), listOf(SkillEffect.StatDelta(EffectTarget.ENEMY, AffectedStat.ATK, -100, duration = 1)))
    private val silenceSkill =
        Skill("silence", "Silence", DamageKind.PHYSICAL, 0, RangeSpec(1, 3), listOf(SkillEffect.ApplyAilment(EffectTarget.ENEMY, Ailment.SILENCE, 2)))
    private val cleanseSkill =
        Skill("cleanse", "Cleanse", DamageKind.PHYSICAL, 0, RangeSpec(0, 1), listOf(SkillEffect.Cleanse(EffectTarget.ALLY)))
    private val ctx = contextOf(
        flat(6, 1),
        skills = mapOf(
            "heal" to healSkill, "self_heal" to selfHealSkill, "atk" to atkSkill, "pheal" to percentHealSkill,
            "buff" to buffSkill, "debuff" to debuffSkill, "tbuff" to timedBuffSkill, "crush" to bigDebuffSkill,
            "silence" to silenceSkill, "cleanse" to cleanseSkill,
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
    fun statDeltaDebuffsAnEnemyStatAndEmitsASignedEvent() {
        // ENEMY-band negative StatDelta: foe base atk 80 → -15 = 65; the event carries the signed amount.
        val result = accept(field(), Command.Cast("medic", "foe", "debuff"))
        assertEquals(65, result.state.unit("foe").stats.atk, "enemy atk reduced by the debuff")
        assertEquals(-15, result.events.filterIsInstance<Event.StatChanged>().single().amount)
    }

    @Test
    fun legalCastTargetsForADebuffAreEnemiesInRange() {
        // ENEMY band, range 1-3: only the foe (dist 3); same-side units excluded by band.
        assertEquals(setOf("foe"), Gameplay.legalCastTargets(field(), "medic", "debuff", ctx))
    }

    @Test
    fun aTimedBuffIsRecordedAndRevertsAfterItsDuration() {
        // duration 2: applied now (atk 80→95) + recorded as an ActiveEffect; reverts after 2 EndTurn ticks.
        val cast = accept(field(), Command.Cast("medic", "medic", "tbuff"))
        assertEquals(95, cast.state.unit("medic").stats.atk, "buff applied immediately")
        assertEquals(1, cast.state.unit("medic").effects.size, "the timed effect is recorded for reversal")
        // tick 1 (player EndTurn): remaining 2→1, still buffed
        val t1 = accept(cast.state, Command.EndTurn(Faction.PLAYER)).state
        assertEquals(95, t1.unit("medic").stats.atk, "still buffed after one turn-boundary")
        // tick 2 (enemy EndTurn, active flipped): remaining 1→0, reverts and drops off
        val t2 = accept(t1, Command.EndTurn(t1.active)).state
        assertEquals(80, t2.unit("medic").stats.atk, "reverts after the duration elapses")
        assertTrue(t2.unit("medic").effects.isEmpty(), "the expired effect is dropped")
    }

    @Test
    fun aTimedDebuffThatFloorsAStatRevertsToTheOriginalNotInflated() {
        // foe atk 80; a -100 debuff floors it to 0 (realized delta -80, not the requested -100). On expiry it
        // must restore the ORIGINAL 80 — never 0 + 100 — i.e. apply/expiry compose to identity through the floor.
        val cast = accept(field(), Command.Cast("medic", "foe", "crush"))
        assertEquals(0, cast.state.unit("foe").stats.atk, "the debuff floors atk at 0")
        val t1 = accept(cast.state, Command.EndTurn(Faction.PLAYER)).state // duration 1 → expires this tick
        assertEquals(80, t1.unit("foe").stats.atk, "reverts to the original stat, not the floor + magnitude")
        assertTrue(t1.unit("foe").effects.isEmpty(), "the expired effect is dropped")
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

    @Test
    fun castingSilenceAfflictsAnEnemyWithoutDrawingRng() {
        val state = field()
        val result = accept(state, Command.Cast("medic", "foe", "silence"))
        val foe = result.state.unit("foe")
        assertTrue(foe.silenced, "the enemy is silenced")
        assertEquals(listOf(ActiveAilment(Ailment.SILENCE, 2)), foe.ailments, "the timed ailment is recorded")
        assertEquals(state.rngState, result.state.rngState, "an ailment cast draws no RNG — the damage golden is untouched")
        val applied = result.events.filterIsInstance<Event.StatusApplied>().single()
        assertEquals("foe", applied.unit)
        assertEquals("SILENCE", applied.status)
    }

    @Test
    fun aSilencedCasterCannotCastAndHasNoCastTargets() {
        // query⟺submit parity for the legality-gate ailment: the gate (CASTER_SILENCED) and the preview
        // (empty targets) are single-sourced through Combatant.silenced, so they cannot disagree.
        val state = field().let { it.withUnit(it.unit("medic").copy(ailments = listOf(ActiveAilment(Ailment.SILENCE, 2)))) }
        assertEquals(RejectReason.CASTER_SILENCED, reject(state, Command.Cast("medic", "ally", "heal")))
        assertTrue(Gameplay.legalCastTargets(state, "medic", "heal", ctx).isEmpty(), "a silenced caster has no cast targets")
    }

    @Test
    fun aSilencedUnitCanStillAttack() {
        // Silence forbids casting, NOT attacking — it is checked only on the Cast path, not actorEligibility.
        val state = stateOf(
            combatant("medic", Faction.PLAYER, Pos(0, 0)).copy(ailments = listOf(ActiveAilment(Ailment.SILENCE, 2))),
            combatant("foe", Faction.ENEMY, Pos(1, 0)),
            active = Faction.PLAYER,
        )
        assertIs<Gameplay.Outcome.Accepted>(Gameplay.submit(state, Command.Attack("medic", "foe", "atk"), ctx))
    }

    @Test
    fun silenceExpiresAfterItsDuration() {
        // duration 2: applied now + recorded; lifts after 2 EndTurn turn-boundaries (id-sorted, RNG-free tick).
        val cast = accept(field(), Command.Cast("medic", "foe", "silence"))
        assertTrue(cast.state.unit("foe").silenced, "silenced immediately")
        val t1 = accept(cast.state, Command.EndTurn(Faction.PLAYER)).state // remaining 2→1
        assertTrue(t1.unit("foe").silenced, "still silenced after one turn-boundary")
        val t2 = accept(t1, Command.EndTurn(t1.active)).state // remaining 1→0, drops
        assertFalse(t2.unit("foe").silenced, "silence lifts after the duration elapses")
        assertTrue(t2.unit("foe").ailments.isEmpty(), "the expired ailment is dropped")
    }

    @Test
    fun recastingSilenceRefreshesRatherThanStacks() {
        // A foe already silenced (remaining 1) re-silenced → a single instance refreshed to the new duration.
        val state = field().let { it.withUnit(it.unit("foe").copy(ailments = listOf(ActiveAilment(Ailment.SILENCE, 1)))) }
        val result = accept(state, Command.Cast("medic", "foe", "silence"))
        assertEquals(listOf(ActiveAilment(Ailment.SILENCE, 2)), result.state.unit("foe").ailments, "refreshed, not stacked")
    }

    @Test
    fun cleanseLiftsAllAilmentsFromAnAllyWithoutDrawingRng() {
        // Cleanse (ADR 0008) — the counterplay to ailments: an ally carrying SILENCE + STUN is fully cleared,
        // emitting Event.StatusCleared, with no RNG drawn. (Timed stat-debuffs are untouched — they self-expire.)
        val state = field().let {
            it.withUnit(it.unit("ally").copy(ailments = listOf(ActiveAilment(Ailment.SILENCE, 2), ActiveAilment(Ailment.STUN, 1))))
        }
        val result = accept(state, Command.Cast("medic", "ally", "cleanse"))
        assertTrue(result.state.unit("ally").ailments.isEmpty(), "every ailment is lifted")
        assertEquals(state.rngState, result.state.rngState, "a cleanse draws no RNG")
        assertEquals("ally", result.events.filterIsInstance<Event.StatusCleared>().single().unit)
        assertTrue(result.state.hasActed("medic"), "the caster spent its action")
    }

    @Test
    fun cleanseOnAnUnafflictedAllyIsANoOpButStillSpendsTheAction() {
        val result = accept(field(), Command.Cast("medic", "ally", "cleanse"))
        assertTrue(result.events.filterIsInstance<Event.StatusCleared>().isEmpty(), "no event when there is nothing to lift")
        assertTrue(result.state.hasActed("medic"), "the action is still spent")
    }

    @Test
    fun cleansingAnEnemyIsRejected() {
        // Cleanse is a friendly effect (ALLY band) — targeting an enemy is rejected, like a heal.
        assertEquals(RejectReason.CAST_TARGET_INVALID, reject(field(), Command.Cast("medic", "foe", "cleanse")))
    }

    @Test
    fun legalTargetsIsEmptyForACastOnlyEffectSkill() {
        // query⟺submit parity: an effect skill is cast-only, so legalTargets (the ATTACK preview) reports no
        // targets for it — mirroring checkAttack's SKILL_IS_CAST_ONLY gate. The debuff would otherwise reach
        // the in-range foe (range 1-3, dist 3) as an attack target.
        assertTrue(Gameplay.legalTargets(field(), "medic", "debuff", ctx).isEmpty(), "an effect skill has no attack targets")
        assertEquals(RejectReason.SKILL_IS_CAST_ONLY, reject(field(), Command.Attack("medic", "foe", "debuff")))
    }
}
