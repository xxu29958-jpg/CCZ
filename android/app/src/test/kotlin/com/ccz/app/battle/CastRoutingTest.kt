package com.ccz.app.battle

import com.ccz.app.campaign.CampaignRuntime
import com.ccz.core.model.Faction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the reducer's CAST routing (ADR 0008 Phase 1 wiring) over the real 大兴山 campaign, where 刘备
 * (hero_1) now carries a heal skill (skill_2). Proves that picking an effect skill flips the selection to
 * SELF/ALLY cast targets (never enemies) and that tapping one submits a Command.Cast through game-core —
 * the presentation layer routes by the skill's effects but owns no combat truth.
 */
class CastRoutingTest {
    private fun reducer(): BattleReducer =
        BattleReducer(CampaignRuntime.context(), CampaignRuntime.script(), CampaignRuntime.scriptContext())

    private fun start(): BattleUiState = reducer().initial(CampaignRuntime.initialState())

    @Test
    fun selectingTheHealSkillRoutesToSameSideCastTargets() {
        val reducer = reducer()
        val start = start()
        val liubeiPos = start.state.units.getValue("hero_1").pos
        val selected = reducer.tapTile(start, liubeiPos)
        assertEquals("hero_1", selected.selection?.unit)
        assertTrue("刘备 carries the heal skill", "skill_2" in (selected.selection?.skills ?: emptyList()))

        val healing = reducer.selectSkill(selected, "skill_2")
        assertTrue("the heal is a cast skill", healing.selection?.castSkill == true)
        val targets = healing.selection?.targets ?: emptySet()
        assertTrue("an ALLY-band heal can target the caster itself", "hero_1" in targets)
        targets.forEach { id ->
            assertEquals("every cast target is same-side — never an enemy", Faction.PLAYER, healing.state.units.getValue(id).faction)
        }
    }

    @Test
    fun tappingACastTargetCastsThroughTheAuthority() {
        val reducer = reducer()
        val start = start()
        val liubeiPos = start.state.units.getValue("hero_1").pos
        val healing = reducer.selectSkill(reducer.tapTile(start, liubeiPos), "skill_2")
        assertTrue("precondition: 刘备 is a self-cast target", "hero_1" in (healing.selection?.targets ?: emptySet()))

        val cast = reducer.tapTile(healing, liubeiPos)
        assertTrue("the cast resolved through the authority — the caster spent its action", cast.state.hasActed("hero_1"))
        assertNull("selection clears after a cast", cast.selection)
        // 刘备 deploys at full HP, so the self-heal is a no-op (zero events) — but it still spent the action,
        // so the log must carry a non-blank line rather than silently swallowing the turn.
        assertTrue("a no-op cast still logs a non-blank line", cast.log.last().isNotBlank())
    }
}
