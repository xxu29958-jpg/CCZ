package com.ccz.app.battle

import com.ccz.contentpack.assembly.BattleSetup
import com.ccz.contentpack.assembly.CampaignAssembler
import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleState
import com.ccz.core.event.SScript

/**
 * The app's demo battle, **assembled from the [CampaignContent] native-content pack** by
 * [CampaignAssembler] — the content-driven path that replaced the old hand-built core-type seed. The
 * presentation layer still holds zero combat truth: this only turns validated content into the engine
 * inputs ([BattleContext] + a deployed opening [BattleState] + the win/lose [SScript]) and hands them to
 * [com.ccz.core.battle.Gameplay], which remains the sole authority. The map's bounds/passability are
 * enforced during deployment because the assembler threads the [BattleMap] through the spawn path.
 *
 * The assembly is cached: the pack is fixed, so the [BattleSetup] is computed once and every accessor
 * returns the same immutable value. The intro cutscene ([com.ccz.app.scenario.DemoScenario]) is still a
 * hand-built R-script pending a content path for non-combatant speakers (see KNOWN_ISSUES).
 */
object DemoBattle {
    const val WIDTH = CampaignContent.WIDTH
    const val HEIGHT = CampaignContent.HEIGHT

    private val setup: BattleSetup by lazy {
        CampaignAssembler.assemble(CampaignContent.pack(), CampaignContent.BATTLE_SCRIPT_ID, CampaignContent.MAP_ID)
    }

    fun context(): BattleContext = setup.context

    fun initialState(): BattleState = setup.initialState

    fun script(): SScript = setup.script
}
