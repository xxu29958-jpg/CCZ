package com.ccz.app.campaign

import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.assembly.BattleSetup
import com.ccz.contentpack.assembly.CampaignAssembler
import com.ccz.contentpack.json.ContentJsonLoader
import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.ScriptContext
import com.ccz.core.event.RScript
import com.ccz.core.event.SScript

/**
 * Single source for the app's real campaign (大兴山之战). The campaign is composed of TWO bundled content
 * packs — the GENERATED battle pack (real classes/units/terrain/map + the s-script, from
 * [com.ccz.modimport.LegacyPackGenerator]) and the AUTHORED scenario pack (the intro r-script) — assembled
 * into the inputs game-core needs. The battle host, intro host, and the replay driver all read this one
 * runtime, so the campaign's packs, ids, and version live in exactly ONE place (extracted from the prior
 * RealBattle/RealScenario accessors).
 *
 * The two packs are ONE campaign release, versioned together under a shared [contentVersion] (ADR 0007):
 * replay safety does not depend on this (battle = snapshotted initialState + command integrity; scenario =
 * r-script-id existence), so the two-pack split needs no save-schema change. game-core stays the sole
 * battle authority; this only loads + assembles content and never holds a second copy of combat truth.
 */
object CampaignRuntime {
    const val BATTLE_SCRIPT_ID = "daxingshan"
    const val MAP_ID = "daxingshan_map"
    const val INTRO_SCRIPT_ID = "daxingshan_intro"

    private const val BATTLE_PACK = "/content/ccz_daxingshan/campaign.json"
    private const val SCENARIO_PACK = "/content/ccz_daxingshan/intro.json"

    private val setup: BattleSetup by lazy { CampaignAssembler.assemble(battlePack(), BATTLE_SCRIPT_ID, MAP_ID) }

    fun battlePack(): NativeContent = load(BATTLE_PACK)

    fun scenarioPack(): NativeContent = load(SCENARIO_PACK)

    /** Both packs ship as one campaign release; their `content_version` is shared (ADR 0007). */
    fun contentVersion(): String = battlePack().manifest.contentVersion

    fun context(): BattleContext = setup.context

    fun initialState(): BattleState = setup.initialState

    fun script(): SScript = setup.script

    fun scriptContext(): ScriptContext = setup.scriptContext

    fun introScript(): RScript =
        rScripts()[INTRO_SCRIPT_ID] ?: error("bundled 大兴山 intro script missing: $INTRO_SCRIPT_ID")

    /** Every replayable R-script of the campaign (from the scenario pack), keyed by id. */
    fun rScripts(): Map<String, RScript> = scenarioPack().events.rScripts.associateBy { it.id }

    private fun load(resource: String): NativeContent =
        ContentJsonLoader.load(
            (javaClass.getResourceAsStream(resource) ?: error("bundled campaign content missing: $resource"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
        )
}
