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
 * Single source for the app's stage-1 campaign runtime. The battle pack is generated from the decrypted
 * legacy package by `LegacyStagePackGenerator`; the intro remains the authored r-script pack.
 *
 * The two packs ship as one campaign release under a shared [contentVersion] (ADR 0007). This composition
 * layer only loads and assembles content; game-core remains the sole battle authority.
 */
object CampaignRuntime : CampaignStageRuntime {
    const val STAGE_ID = "legacy_stage_1"
    const val BATTLE_SCRIPT_ID = STAGE_ID
    const val MAP_ID = "legacy_stage_1_map"
    const val INTRO_SCRIPT_ID = "daxingshan_intro"

    override val stageId: String get() = STAGE_ID
    override val battleScriptId: String get() = BATTLE_SCRIPT_ID
    override val mapId: String get() = MAP_ID
    override val introScriptId: String get() = INTRO_SCRIPT_ID

    private const val BATTLE_PACK = "/content/legacy_stage_1/campaign.json"
    private const val SCENARIO_PACK = "/content/ccz_daxingshan/intro.json"

    private val setup: BattleSetup by lazy { CampaignAssembler.assemble(battlePack(), BATTLE_SCRIPT_ID, MAP_ID) }

    fun battlePack(): NativeContent = load(BATTLE_PACK)

    fun scenarioPack(): NativeContent = load(SCENARIO_PACK)

    /** Both packs ship as one campaign release; their `content_version` is shared (ADR 0007). */
    override fun contentVersion(): String = battlePack().manifest.contentVersion

    /** Display names for the campaign's terrain, for the battle's tile inspector. */
    override fun terrainNames(): Map<String, String> = battlePack().tables.terrain.associate { it.id to it.name }

    override fun context(): BattleContext = setup.context

    override fun initialState(): BattleState = setup.initialState

    override fun script(): SScript = setup.script

    override fun scriptContext(): ScriptContext = setup.scriptContext

    fun introScript(): RScript =
        rScripts()[INTRO_SCRIPT_ID] ?: error("bundled Daxingshan intro script missing: $INTRO_SCRIPT_ID")

    override fun introScriptOrNull(): RScript = introScript()

    /** Every replayable R-script of the campaign (from the scenario pack), keyed by id. */
    fun rScripts(): Map<String, RScript> = scenarioPack().events.rScripts.associateBy { it.id }

    private fun load(resource: String): NativeContent =
        ContentJsonLoader.load(
            (javaClass.getResourceAsStream(resource) ?: error("bundled campaign content missing: $resource"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
        )
}
