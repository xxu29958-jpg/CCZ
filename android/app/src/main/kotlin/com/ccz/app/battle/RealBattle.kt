package com.ccz.app.battle

import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.assembly.BattleSetup
import com.ccz.contentpack.assembly.CampaignAssembler
import com.ccz.contentpack.json.ContentJsonLoader
import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.ScriptContext
import com.ccz.core.event.SScript

/**
 * The real-data battle the app ships: 大兴山之战 (桃园三兄弟 vs 黄巾), assembled from a native content pack
 * GENERATED from the user's decrypted legacy tables by [com.ccz.modimport.LegacyPackGenerator] — real
 * classes (群雄/裨将/重骑兵/黄巾军) with real `dic_job` growth, real `dic_hero` stats, and a grade forged
 * from each hero's combat strength. It is fought on an 8×7 crop of the REAL `terrainMap_1` (大兴山) terrain
 * (荒地/山地/树林) with real `dic_jobWalk` per-class move costs and `dic_jobTerrain` combat affinity wired in.
 * Deploy levels are battle design; real heroes carry no skill in the ore, so the generator grants a basic
 * attack. Same content → [CampaignAssembler] → [com.ccz.core.battle.Gameplay] path as [DemoBattle]; the
 * presentation layer holds zero combat truth. The level × growth × grade levers make the roster's panels
 * visibly differ — an elite (关羽, grade 2 level 8) outscales a rookie (邓茂, grade 0).
 */
object RealBattle {
    const val BATTLE_SCRIPT_ID = "daxingshan"
    const val MAP_ID = "daxingshan_map"

    private const val RESOURCE = "/content/ccz_daxingshan/campaign.json"

    fun pack(): NativeContent =
        ContentJsonLoader.load(
            (javaClass.getResourceAsStream(RESOURCE) ?: error("bundled battle content missing: $RESOURCE"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
        )

    private val setup: BattleSetup by lazy { CampaignAssembler.assemble(pack(), BATTLE_SCRIPT_ID, MAP_ID) }

    fun context(): BattleContext = setup.context

    fun initialState(): BattleState = setup.initialState

    fun script(): SScript = setup.script

    fun scriptContext(): ScriptContext = setup.scriptContext
}
