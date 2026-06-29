package com.ccz.app.campaign

import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.ScriptContext
import com.ccz.core.event.RScript
import com.ccz.core.event.SScript

interface CampaignStageRuntime {
    val stageId: String
    val battleScriptId: String
    val mapId: String
    val introScriptId: String?

    fun contentVersion(): String

    fun terrainNames(): Map<String, String>

    fun context(): BattleContext

    fun initialState(): BattleState

    fun script(): SScript

    fun scriptContext(): ScriptContext

    fun introScriptOrNull(): RScript?
}
