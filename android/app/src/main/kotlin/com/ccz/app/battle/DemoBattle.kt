package com.ccz.app.battle

import com.ccz.contentpack.assembly.BattleSetup
import com.ccz.contentpack.assembly.CampaignAssembler
import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.ScriptContext
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
    private val setup: BattleSetup by lazy {
        CampaignAssembler.assemble(CampaignContent.pack(), CampaignContent.BATTLE_SCRIPT_ID, CampaignContent.MAP_ID)
    }

    // Derived from the assembled map so the JSON pack stays the single source of the board's size.
    val WIDTH: Int get() = setup.context.map.width
    val HEIGHT: Int get() = setup.context.map.height

    fun context(): BattleContext = setup.context

    fun initialState(): BattleState = setup.initialState

    fun script(): SScript = setup.script

    fun scriptContext(): ScriptContext = setup.scriptContext
}
