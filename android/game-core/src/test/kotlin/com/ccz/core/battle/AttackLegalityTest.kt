package com.ccz.core.battle

import com.ccz.core.model.DamageKind
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
import com.ccz.core.model.SkillEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttackLegalityTest {
    private val map = flat(5, 5)

    @Test
    fun unknownAttackerIsRejected() {
        val state = stateOf(combatant("a", Faction.PLAYER, Pos(0, 0)))
        val reason = CommandValidator.check(state, Command.Attack("ghost", "a", "atk"), contextOf(map))
        assertEquals(RejectReason.UNIT_NOT_FOUND, reason)
    }

    @Test
    fun deadAttackerIsRejected() {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0), hp = 0),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        assertEquals(RejectReason.UNIT_DEAD, CommandValidator.check(state, Command.Attack("a", "e", "atk"), contextOf(map)))
    }

    @Test
    fun attackByInactiveFactionIsRejected() {
        val state = stateOf(
            combatant("a", Faction.ENEMY, Pos(0, 0)),
            combatant("p", Faction.PLAYER, Pos(1, 0)),
            active = Faction.PLAYER,
        )
        assertEquals(RejectReason.NOT_ACTIVE_FACTION, CommandValidator.check(state, Command.Attack("a", "p", "atk"), contextOf(map)))
    }

    @Test
    fun unknownSkillIsRejected() {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        assertEquals(RejectReason.UNKNOWN_SKILL, CommandValidator.check(state, Command.Attack("a", "e", "fireball"), contextOf(map)))
    }

    @Test
    fun attackingSelfIsRejected() {
        val state = stateOf(combatant("a", Faction.PLAYER, Pos(0, 0)))
        assertEquals(RejectReason.SELF_TARGET, CommandValidator.check(state, Command.Attack("a", "a", "atk"), contextOf(map)))
    }

    @Test
    fun missingTargetIsRejected() {
        val state = stateOf(combatant("a", Faction.PLAYER, Pos(0, 0)))
        assertEquals(RejectReason.TARGET_NOT_FOUND, CommandValidator.check(state, Command.Attack("a", "ghost", "atk"), contextOf(map)))
    }

    @Test
    fun deadTargetIsRejected() {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0), hp = 0),
        )
        assertEquals(RejectReason.TARGET_DEAD, CommandValidator.check(state, Command.Attack("a", "e", "atk"), contextOf(map)))
    }

    @Test
    fun friendlyTargetIsRejected() {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("ally", Faction.ALLY, Pos(1, 0)),
        )
        assertEquals(RejectReason.TARGET_FRIENDLY, CommandValidator.check(state, Command.Attack("a", "ally", "atk"), contextOf(map)))
    }

    @Test
    fun outOfRangeAttackIsRejected() {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(2, 0)),
        )
        assertEquals(RejectReason.OUT_OF_ATTACK_RANGE, CommandValidator.check(state, Command.Attack("a", "e", "atk"), contextOf(map)))
    }

    @Test
    fun attackingWithACastOnlyEffectSkillIsRejected() {
        // An effect skill (non-empty effects) is cast-only — using it as Attack must be rejected BEFORE it
        // reaches the RNG-consuming damage pipeline (the inverse of checkCast's SKILL_HAS_NO_EFFECT). The
        // guard fires on the skill regardless of target validity (here an in-range enemy).
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        val ctx = contextOf(
            map,
            skills = mapOf(
                "atk" to Skill("atk", "Attack", DamageKind.PHYSICAL, 100),
                "heal" to Skill("heal", "Heal", DamageKind.PHYSICAL, 0, RangeSpec(0, 1), listOf(SkillEffect.Heal(EffectTarget.ALLY, 30))),
            ),
        )
        assertEquals(RejectReason.SKILL_IS_CAST_ONLY, CommandValidator.check(state, Command.Attack("a", "e", "heal"), ctx))
    }

    @Test
    fun inRangeAttackIsAccepted() {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(1, 1)),
            combatant("e", Faction.ENEMY, Pos(3, 1)),
        )
        val context = contextOf(map, skills = skillsOf(range = RangeSpec(2, 2)))
        assertNull(CommandValidator.check(state, Command.Attack("a", "e", "atk"), context))
    }
}
