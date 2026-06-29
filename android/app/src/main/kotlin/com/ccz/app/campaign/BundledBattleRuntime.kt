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
 * Runtime wrapper for a promoted legacy battle pack that has no authored intro yet. The pack is already native
 * content; this class only loads the bundled JSON resource and assembles it into game-core inputs.
 */
class BundledBattleRuntime(
    override val stageId: String,
    override val battleScriptId: String,
    override val mapId: String,
    private val battlePackResource: String,
) : CampaignStageRuntime {
    override val introScriptId: String? = null

    private val pack: NativeContent by lazy { load(battlePackResource) }
    private val setup: BattleSetup by lazy { CampaignAssembler.assemble(pack, battleScriptId, mapId) }

    fun battlePack(): NativeContent = pack

    override fun contentVersion(): String = pack.manifest.contentVersion

    override fun terrainNames(): Map<String, String> = pack.tables.terrain.associate { it.id to it.name }

    override fun context(): BattleContext = setup.context

    override fun initialState(): BattleState = setup.initialState

    override fun script(): SScript = setup.script

    override fun scriptContext(): ScriptContext = setup.scriptContext

    override fun introScriptOrNull(): RScript? = null

    private fun load(resource: String): NativeContent =
        ContentJsonLoader.load(
            (javaClass.getResourceAsStream(resource) ?: error("bundled battle content missing: $resource"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
        )
}
