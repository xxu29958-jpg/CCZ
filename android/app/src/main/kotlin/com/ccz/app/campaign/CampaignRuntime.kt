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
object CampaignRuntime : CampaignStageRuntime {
    const val STAGE_ID = "legacy_stage_1"
    const val BATTLE_SCRIPT_ID = "daxingshan_full"
    const val MAP_ID = "daxingshan_full_map"
    const val INTRO_SCRIPT_ID = "daxingshan_intro"

    override val stageId: String get() = STAGE_ID
    override val battleScriptId: String get() = BATTLE_SCRIPT_ID
    override val mapId: String get() = MAP_ID
    override val introScriptId: String get() = INTRO_SCRIPT_ID

    // The faithful FULL-STAGE 大兴山 battle (real dispatch roster on the complete 23×16 map, #138). The battle
    // pack is a HAND-MAINTAINED artifact: `LegacyPackGenerator.generateFullStage` emits the real roster/coords
    // (basic attacks, content_version 0.1.0), then the hand-authored effect-skill table (skill_2~8) + the player
    // trio's effect loadouts (刘备 heal/cleanse, 关羽 debuff/silence, 张飞 buff/stun) + 程远志's enemy 破甲 are
    // spliced in and the version bumped to 0.7.0 — DO NOT blind-regenerate over it (the CampaignRuntimeTest +
    // CastRoutingTest guards loudly fail if the splice is lost). The authored intro r-script ships in
    // ccz_daxingshan/intro.json (portrait-based, so unit-id-independent); both packs share one content_version
    // (ADR 0007).
    private const val BATTLE_PACK = "/content/ccz_daxingshan_full/campaign.json"
    private const val SCENARIO_PACK = "/content/ccz_daxingshan/intro.json"

    private val setup: BattleSetup by lazy { CampaignAssembler.assemble(battlePack(), BATTLE_SCRIPT_ID, MAP_ID) }

    fun battlePack(): NativeContent = load(BATTLE_PACK)

    fun scenarioPack(): NativeContent = load(SCENARIO_PACK)

    /** Both packs ship as one campaign release; their `content_version` is shared (ADR 0007). */
    override fun contentVersion(): String = battlePack().manifest.contentVersion

    /** Display names for the campaign's terrain (terrainId → name), for the battle's tile inspector. */
    override fun terrainNames(): Map<String, String> = battlePack().tables.terrain.associate { it.id to it.name }

    override fun context(): BattleContext = setup.context

    override fun initialState(): BattleState = setup.initialState

    override fun script(): SScript = setup.script

    override fun scriptContext(): ScriptContext = setup.scriptContext

    fun introScript(): RScript =
        rScripts()[INTRO_SCRIPT_ID] ?: error("bundled 大兴山 intro script missing: $INTRO_SCRIPT_ID")

    override fun introScriptOrNull(): RScript = introScript()

    /** Every replayable R-script of the campaign (from the scenario pack), keyed by id. */
    fun rScripts(): Map<String, RScript> = scenarioPack().events.rScripts.associateBy { it.id }

    private fun load(resource: String): NativeContent =
        ContentJsonLoader.load(
            (javaClass.getResourceAsStream(resource) ?: error("bundled campaign content missing: $resource"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
        )
}
