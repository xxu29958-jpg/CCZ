package com.ccz.app.battle

import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleMap
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.MapTile
import com.ccz.core.battle.ScriptContext
import com.ccz.core.event.SScript
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.CombatIdentity
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.CombatVitals
import com.ccz.core.model.Combatant
import com.ccz.core.model.DamageKind
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
import com.ccz.core.model.SkillEffect
import com.ccz.core.model.UnitClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the reducer's CAST routing with a small authored test battle. The route is independent from any
 * promoted legacy stage: selecting an effect skill flips taps to Command.Cast targets, while pure-damage
 * skills remain attacks.
 */
class CastRoutingTest {
    private val map = BattleMap.uniform(3, 2, MapTile("plain", 1))
    private val soldier = UnitClass("soldier", "Soldier", "foot", move = 3)
    private val strike = Skill("strike", "Strike", DamageKind.PHYSICAL, 100)
    private val heal = Skill(
        id = "heal",
        name = "Heal",
        kind = DamageKind.PHYSICAL,
        powerCoeff = 0,
        range = RangeSpec(0, 1),
        effects = listOf(SkillEffect.Heal(EffectTarget.ALLY, 30)),
    )
    private val context = BattleContext(
        map = map,
        classes = mapOf(soldier.id to soldier),
        skills = mapOf(strike.id to strike, heal.id to heal),
        loadouts = mapOf(
            "medic" to listOf(strike.id, heal.id),
            "ally" to listOf(strike.id),
            "foe" to listOf(strike.id),
        ),
    )
    private val script = SScript(
        id = "cast_routing",
        win = listOf(WinLoseCondition.AnnihilateEnemies),
        lose = listOf(WinLoseCondition.ProtectAlive("medic")),
        pre = emptyList(),
        mid = emptyList(),
        post = emptyList(),
    )
    private val reducer = BattleReducer(context, script, ScriptContext(map = map))

    private fun start(): BattleUiState = reducer.initial(
        BattleState(
            units = listOf(
                unit("medic", Faction.PLAYER, Pos(0, 0)),
                unit("ally", Faction.PLAYER, Pos(1, 0)),
                unit("foe", Faction.ENEMY, Pos(0, 1)),
            ).associateBy { it.id },
            turn = 1,
            active = Faction.PLAYER,
            rngState = 1L,
        ),
    )

    @Test
    fun selectingTheHealSkillRoutesToSameSideCastTargets() {
        val start = start()
        val selected = reducer.tapTile(start, start.state.units.getValue("medic").pos)
        assertEquals("medic", selected.selection?.unit)
        assertTrue("the test caster carries the heal skill", heal.id in (selected.selection?.skills ?: emptyList()))

        val healing = reducer.selectSkill(selected, heal.id)
        assertTrue("the heal is a cast skill", healing.selection?.castSkill == true)
        val targets = healing.selection?.targets ?: emptySet()
        assertTrue("an ALLY-band heal can target the caster itself", "medic" in targets)
        assertTrue("an ALLY-band heal can target adjacent same-side units", "ally" in targets)
        assertTrue("an ALLY-band heal never targets enemies", "foe" !in targets)
    }

    @Test
    fun tappingACastTargetCastsThroughTheAuthority() {
        val start = start()
        val healing = reducer.selectSkill(reducer.tapTile(start, start.state.units.getValue("medic").pos), heal.id)
        assertTrue("precondition: caster is a self-cast target", "medic" in (healing.selection?.targets ?: emptySet()))

        val cast = reducer.tapTile(healing, start.state.units.getValue("medic").pos)
        assertTrue("the authority spent the caster's action", cast.state.hasActed("medic"))
        assertNull("selection clears after a cast", cast.selection)
        assertTrue("a no-op cast still logs a non-blank line", cast.log.last().isNotBlank())
    }

    private fun unit(id: String, faction: Faction, pos: Pos): Combatant =
        Combatant(
            identity = CombatIdentity(id = id, name = id, classId = soldier.id, faction = faction),
            pos = pos,
            vitals = CombatVitals(hp = 100, hpMax = 100),
            stats = CombatStats(atk = 20, def = 10, mat = 10, res = 10),
            rates = CombatRates(),
        )
}
